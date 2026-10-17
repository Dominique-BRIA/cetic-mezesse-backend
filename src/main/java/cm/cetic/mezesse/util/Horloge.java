package cm.cetic.mezesse.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Les colonnes de date ont une précision à la milliseconde (TIMESTAMP(3)). */
public final class Horloge {

  private Horloge() {}

  public static Instant maintenant() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }
}
