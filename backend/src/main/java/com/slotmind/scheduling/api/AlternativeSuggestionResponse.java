package com.slotmind.scheduling.api;

/**
 * The outcome of "that one doesn't suit — what else?".
 *
 * <p>Two fields rather than one because the rejection and the replacement can genuinely diverge:
 * the rejection always succeeds, but there may be nothing left to offer. Collapsing that into a
 * single error would wrongly imply the rejection failed and leave the coordinator unsure whether
 * they still owe the patient a call.
 */
public record AlternativeSuggestionResponse(
    SuggestionDecisionResponse rejected, SuggestionResponse alternative) {

  public boolean hasAlternative() {
    return alternative != null;
  }
}
