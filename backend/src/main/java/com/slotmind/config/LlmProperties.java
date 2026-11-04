package com.slotmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.llm")
@Getter
@Setter
public class LlmProperties {

  /** false selects the deterministic stub — the stage-safety switch for a dead gateway. */
  private boolean enabled = true;

  private String baseUrl = "https://litellm-qc.zycus.net/v1";
  private String apiKey = "";
  private String model = "gpt-5.6-luna-azure-payg-global";

  /** Required by the Zycus LiteLLM proxy; sent as the literal header name "product". */
  private String productHeader = "PC1";

  private int timeoutMs = 8000;

  /** Low by default: a scheduling recommendation should be stable, not creative. */
  private double temperature = 0.2;
}
