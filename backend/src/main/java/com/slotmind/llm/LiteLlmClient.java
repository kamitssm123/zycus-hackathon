package com.slotmind.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.config.LlmProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI-compatible call to the Zycus LiteLLM proxy.
 *
 * <p>Uses Spring's RestClient, which ships inside spring-web — no new dependency (CLAUDE.md rule
 * 1). {@code response_format: json_object} makes the gateway return parseable JSON rather than
 * prose, which removes an entire class of parsing failure; the markdown-fence strip downstream
 * stays as a belt-and-braces measure because gateways wrap JSON more often than they should.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.enabled", havingValue = "true", matchIfMissing = true)
public class LiteLlmClient implements LlmClient {

  private final RestClient restClient;
  private final LlmProperties properties;
  private final ObjectMapper objectMapper;

  public LiteLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(
                ClientHttpRequestFactories.get(
                    ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .withReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))))
            .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
            .defaultHeader("product", properties.getProductHeader())
            .build();
    log.info(
        "LLM gateway enabled: {} model={} timeout={}ms",
        properties.getBaseUrl(),
        properties.getModel(),
        properties.getTimeoutMs());
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    Map<String, Object> payload =
        Map.of(
            "model", properties.getModel(),
            "temperature", properties.getTemperature(),
            "response_format", Map.of("type", "json_object"),
            "messages",
                List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)));

    try {
      String raw =
          restClient
              .post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(String.class);

      JsonNode root = objectMapper.readTree(raw);
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      if (content.isMissingNode() || content.asText().isBlank()) {
        throw new LlmUnavailableException("LLM returned no message content");
      }
      return content.asText();
    } catch (RestClientException e) {
      throw new LlmUnavailableException("LLM gateway call failed: " + e.getMessage(), e);
    } catch (LlmUnavailableException e) {
      throw e;
    } catch (Exception e) {
      throw new LlmUnavailableException("LLM response could not be read: " + e.getMessage(), e);
    }
  }
}
