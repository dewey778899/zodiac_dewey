package com.zodiac.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiChatService {

    private WebClient webClient;
    private WebClient claudeWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- DeepSeek 配置 ----
    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${ai.api.model:deepseek-chat}")
    private String model;

    @Value("${ai.api.max-tokens:8000}")
    private Integer maxTokens;

    @Value("${ai.api.timeout-seconds:180}")
    private Integer timeoutSeconds;

    // ---- Claude 配置 ----
    @Value("${ai.api.claude.key:}")
    private String claudeApiKey;

    @Value("${ai.api.claude.url:https://api.anthropic.com/v1/messages}")
    private String claudeApiUrl;

    @Value("${ai.api.claude.model:claude-sonnet-4-20250514}")
    private String claudeModel;

    @Value("${ai.api.claude.max-tokens:8000}")
    private Integer claudeMaxTokens;

    @Value("${ai.api.claude.timeout-seconds:180}")
    private Integer claudeTimeoutSeconds;

    /**
     * 根据模型类型生成 AI 内容。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param modelChoice  模型选择: "claude" 使用 Claude, 其他值使用 DeepSeek
     * @return AI 生成的文本内容
     */
    public String generate(String systemPrompt, String userPrompt, String modelChoice) {
        if ("claude".equalsIgnoreCase(modelChoice)) {
            return generateWithClaude(systemPrompt, userPrompt);
        }
        return generateWithDeepSeek(systemPrompt, userPrompt);
    }

    /**
     * 兼容旧调用: 默认使用 DeepSeek
     */
    public String generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, "deepseek");
    }

    // ==================== DeepSeek 调用 ====================

    private String generateWithDeepSeek(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("AI_API_KEY 未配置，无法生成报告。");
        }

        try {
            String response = getDeepSeekWebClient().post()
                    .uri(apiUrl)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .bodyValue(buildDeepSeekRequestBody(systemPrompt, userPrompt))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("DeepSeek API 调用失败: {}", e.getMessage());
                        return Mono.error(new RuntimeException("AI 服务暂时不可用，请稍后再试。"));
                    })
                    .block();

            return extractDeepSeekContent(response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 生成报告失败", e);
            throw new RuntimeException("生成失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildDeepSeekRequestBody(String systemPrompt, String userPrompt) {
        return Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
    }

    private String extractDeepSeekContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }
        throw new RuntimeException("AI API 返回格式异常");
    }

    private WebClient getDeepSeekWebClient() {
        if (webClient == null) {
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(timeoutSeconds));
            webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }
        return webClient;
    }

    // ==================== Claude 调用 ====================

    private String generateWithClaude(String systemPrompt, String userPrompt) {
        if (claudeApiKey == null || claudeApiKey.isBlank()) {
            throw new RuntimeException("CLAUDE_API_KEY 未配置，无法使用 Claude 模型。");
        }

        try {
            String response = getClaudeWebClient().post()
                    .uri(claudeApiUrl)
                    .header("x-api-key", claudeApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .bodyValue(buildClaudeRequestBody(systemPrompt, userPrompt))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(claudeTimeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("Claude API 调用失败: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Claude 服务暂时不可用，请稍后再试。"));
                    })
                    .block();

            return extractClaudeContent(response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude 生成报告失败", e);
            throw new RuntimeException("生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * Claude API 请求体格式:
     * - system 字段单独放在顶层(不在 messages 数组内)
     * - messages 数组只包含 role=assistant / role=user 的消息
     */
    private Map<String, Object> buildClaudeRequestBody(String systemPrompt, String userPrompt) {
        return Map.of(
                "model", claudeModel,
                "max_tokens", claudeMaxTokens,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                )
        );
    }

    /**
     * Claude API 响应格式:
     * { "content": [ { "type": "text", "text": "..." } ] }
     */
    private String extractClaudeContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            String text = content.get(0).path("text").asText();
            if (text != null && !text.isEmpty()) {
                return text;
            }
        }
        throw new RuntimeException("Claude API 返回格式异常");
    }

    private WebClient getClaudeWebClient() {
        if (claudeWebClient == null) {
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(claudeTimeoutSeconds));
            claudeWebClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }
        return claudeWebClient;
    }
}
