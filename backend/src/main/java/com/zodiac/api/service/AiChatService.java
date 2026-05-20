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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private WebClient getWebClient() {
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

    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("AI_API_KEY 未配置，无法生成报告。");
        }

        try {
            String response = getWebClient().post()
                    .uri(apiUrl)
                    .headers(headers -> applyAuthHeaders(headers))
                    .bodyValue(buildRequestBody(systemPrompt, userPrompt))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .onErrorResume(e -> {
                        log.error("AI API 调用失败: {}", e.getMessage());
                        return Mono.error(new RuntimeException("AI 服务暂时不可用，请稍后再试。"));
                    })
                    .block();

            return extractContent(response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成报告失败", e);
            throw new RuntimeException("生成失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt) {
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

    private void applyAuthHeaders(HttpHeaders headers) {
        headers.setBearerAuth(apiKey);
    }

    private String extractContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }
        throw new RuntimeException("AI API 返回格式异常");
    }
}
