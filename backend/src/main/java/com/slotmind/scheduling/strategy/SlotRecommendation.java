package com.slotmind.scheduling.strategy;

/**
 * One ordered recommendation. {@code reasoning} is patient-friendly prose a receptionist can read
 * aloud — never internal jargon, never a system status.
 */
public record SlotRecommendation(
    String slotId, String alternateProviderId, double confidence, String reasoning) {}
