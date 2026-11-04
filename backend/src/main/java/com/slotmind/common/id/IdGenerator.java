package com.slotmind.common.id;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates ids under a row lock, inside the caller's transaction. Not {@code count() + 1}: that is
 * racy, and AcceptSuggestionConcurrencyIT would catch it.
 */
@Component
@RequiredArgsConstructor
public class IdGenerator {

  public static final String APPOINTMENT = "APPOINTMENT";
  public static final String SUGGESTION = "SUGGESTION";
  public static final String SLOT = "SLOT";
  public static final String PROVIDER = "PROVIDER";

  private final IdCounterRepository repository;

  @Transactional(propagation = Propagation.MANDATORY)
  public String next(String counter) {
    IdCounter row =
        repository
            .lockByName(counter)
            .orElseGet(
                () -> repository.save(IdCounter.builder().name(counter).nextValue(1).build()));
    long value = row.getNextValue();
    row.setNextValue(value + 1);
    return "%s-%03d".formatted(prefix(counter), value);
  }

  private static String prefix(String counter) {
    return switch (counter) {
      case APPOINTMENT -> "APT";
      case SUGGESTION -> "SGT";
      case SLOT -> "SLT";
      case PROVIDER -> "PRV";
      default -> throw new IllegalArgumentException("Unknown id counter: " + counter);
    };
  }
}
