package com.slotmind.llm;

/**
 * The test seam.
 *
 * <p>Returns the raw assistant message rather than a parsed object on purpose: parsing and
 * hallucination-handling — the parts that actually break — then live in AiSchedulingStrategy and
 * are unit-testable against canned strings with no mocking framework at all. A single-method
 * interface also means the fake is a lambda.
 */
public interface LlmClient {

  String complete(String systemPrompt, String userPrompt);
}
