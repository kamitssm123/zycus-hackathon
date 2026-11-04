package com.slotmind.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in, active when {@code app.llm.enabled=false}.
 *
 * <p>This is demo insurance, not a shortcut: a 2-second dependency on conference wifi has ended
 * more demos than any bug. It picks the first candidate slot the prompt offers, so it exercises the
 * same parse-and-validate path as the real gateway.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.llm.enabled", havingValue = "false")
public class StubLlmClient implements LlmClient {

  private static final Pattern FIRST_SLOT = Pattern.compile("(SLT-\\d+)");

  public StubLlmClient() {
    log.warn("LLM DISABLED — using StubLlmClient. Set app.llm.enabled=true for the real gateway.");
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    Matcher matcher = FIRST_SLOT.matcher(userPrompt);
    if (!matcher.find()) {
      return "{\"recommendations\":[]}";
    }
    String slotId = matcher.group(1);
    return """
        {"recommendations":[{"slotId":"%s","alternateProviderId":null,"confidence":0.7,
          "reasoning":"We can move this appointment to the next available opening. \
Offline suggestion — the scheduling assistant is unavailable, so this is the earliest slot we have."}]}
        """
        .formatted(slotId);
  }
}
