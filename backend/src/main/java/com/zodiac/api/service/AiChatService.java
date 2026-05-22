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
import reactor.core.Exceptions;
import reactor.netty.http.client.HttpClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiChatService {

    private WebClient webClient;
    private WebClient claudeWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${ai.api.model:deepseek-chat}")
    private String model;

    @Value("${ai.api.max-tokens:8000}")
    private Integer maxTokens;

    @Value("${ai.api.temperature:0.7}")
    private Double temperature;

    @Value("${ai.api.timeout-seconds:180}")
    private Integer timeoutSeconds;

    @Value("${ai.api.claude.key:}")
    private String claudeApiKey;

    @Value("${ai.api.claude.url:https://api.anthropic.com/v1/messages}")
    private String claudeApiUrl;

    @Value("${ai.api.claude.model:claude-opus-4-7}")
    private String claudeModel;

    @Value("${ai.api.claude.max-tokens:8000}")
    private Integer claudeMaxTokens;

    @Value("${ai.api.claude.temperature:0.8}")
    private Double claudeTemperature;

    @Value("${ai.api.claude.timeout-seconds:180}")
    private Integer claudeTimeoutSeconds;

    @Value("${ai.api.proxy.host:}")
    private String proxyHost;

    @Value("${ai.api.proxy.port:0}")
    private Integer proxyPort;

    @Value("${ai.api.retry-count:1}")
    private Integer retryCount;

    public String generate(String systemPrompt, String userPrompt, String modelChoice) {
        if ("claude".equalsIgnoreCase(modelChoice)) {
            return generateWithClaude(systemPrompt, userPrompt);
        }
        return generateWithDeepSeek(systemPrompt, userPrompt);
    }

    public String generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, "deepseek");
    }

    private String generateWithDeepSeek(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(
                    AiServiceException.Reason.MISCONFIGURED,
                    "AI_API_KEY 未配置，无法生成报告。"
            );
        }
        return executeWithRetry(
                "DeepSeek",
                () -> getDeepSeekWebClient().post()
                        .uri(apiUrl)
                        .headers(headers -> headers.setBearerAuth(apiKey))
                        .bodyValue(buildDeepSeekRequestBody(systemPrompt, userPrompt))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .block(),
                this::extractDeepSeekContent
        );
    }

    private Map<String, Object> buildDeepSeekRequestBody(String systemPrompt, String userPrompt) {
        return Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "temperature", temperature,
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
            String content = choices.get(0).path("message").path("content").asText();
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        throw new AiServiceException(AiServiceException.Reason.INVALID_RESPONSE, "DeepSeek 返回内容为空或格式异常。");
    }

    private WebClient getDeepSeekWebClient() {
        if (webClient == null) {
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(timeoutSeconds));
            if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
                log.info("DeepSeek WebClient uses proxy {}:{}", proxyHost, proxyPort);
                httpClient = httpClient.proxy(proxy -> proxy
                        .type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
                        .address(new InetSocketAddress(proxyHost, proxyPort)));
            }

            webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }
        return webClient;
    }

    private String generateWithClaude(String systemPrompt, String userPrompt) {
        if (claudeApiKey == null || claudeApiKey.isBlank()) {
            throw new AiServiceException(
                    AiServiceException.Reason.MISCONFIGURED,
                    "CLAUDE_API_KEY 未配置，无法使用深度解析模型。"
            );
        }
        return executeWithRetry(
                "Opus 4.7",
                () -> getClaudeWebClient().post()
                        .uri(claudeApiUrl)
                        .header("x-api-key", claudeApiKey)
                        .header("anthropic-version", "2023-06-01")
                        .bodyValue(buildClaudeRequestBody(systemPrompt, userPrompt))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(claudeTimeoutSeconds))
                        .block(),
                this::extractClaudeContent
        );
    }

    private Map<String, Object> buildClaudeRequestBody(String systemPrompt, String userPrompt) {
        return Map.of(
                "model", claudeModel,
                "max_tokens", claudeMaxTokens,
                "temperature", claudeTemperature,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                )
        );
    }

    private String extractClaudeContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            String text = content.get(0).path("text").asText();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        throw new AiServiceException(AiServiceException.Reason.INVALID_RESPONSE, "Opus 4.7 返回内容为空或格式异常。");
    }

    private WebClient getClaudeWebClient() {
        if (claudeWebClient == null) {
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(claudeTimeoutSeconds));
            if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
                log.info("Claude WebClient uses proxy {}:{}", proxyHost, proxyPort);
                httpClient = httpClient.proxy(proxy -> proxy
                        .type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
                        .address(new InetSocketAddress(proxyHost, proxyPort)));
            }

            claudeWebClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }
        return claudeWebClient;
    }

    private String executeWithRetry(String provider, ThrowingSupplier rawSupplier, ThrowingParser parser) {
        AiServiceException lastError = null;
        int attempts = Math.max(1, retryCount + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String raw = rawSupplier.get();
                return parser.parse(raw);
            } catch (AiServiceException e) {
                lastError = e;
            } catch (Exception e) {
                lastError = mapAiException(provider, e);
            }

            log.warn("{} attempt {}/{} failed: {}", provider, attempt, attempts, lastError.getMessage());
            if (attempt >= attempts || !lastError.isRetryable()) {
                throw lastError;
            }
        }

        throw lastError != null
                ? lastError
                : new AiServiceException(AiServiceException.Reason.UPSTREAM_ERROR, provider + " 服务暂时不可用，请稍后再试。");
    }

    private AiServiceException mapAiException(String provider, Exception error) {
        Throwable unwrapped = Exceptions.unwrap(error);
        if (unwrapped instanceof AiServiceException aiError) {
            return aiError;
        }

        String message = unwrapped.getMessage();
        if (unwrapped instanceof java.util.concurrent.TimeoutException
                || (message != null && message.contains("Did not observe any item or terminal signal within"))) {
            log.error("{} timeout", provider, unwrapped);
            return new AiServiceException(
                    AiServiceException.Reason.TIMEOUT,
                    provider + " 响应超时，请稍后重试。",
                    unwrapped
            );
        }

        log.error("{} call failed", provider, unwrapped);
        return new AiServiceException(
                AiServiceException.Reason.UPSTREAM_ERROR,
                provider + " 服务暂时不可用，请稍后再试。",
                unwrapped
        );
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingParser {
        String parse(String raw) throws Exception;
    }
}
