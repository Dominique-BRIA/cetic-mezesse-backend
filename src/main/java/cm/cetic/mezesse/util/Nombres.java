package cm.cetic.mezesse.util;

import java.math.BigDecimal;

/** Écriture des nombres dans les textes (journal, documents). */
public final class Nombres {

  private Nombres() {}

  /** « 16 », « 12.5 » : sans « .0 » inutile. */
  public static String simple(double v) {
    if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
    return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
  }

  /** « 12,50 » : deux décimales, virgule française. */
  public static String fr(Double v, int decimales) {
    if (v == null) return "";
    return String.format(java.util.Locale.ROOT, "%." + decimales + "f", v).replace('.', ',');
  }

  public static String fr(Double v) {
    return fr(v, 2);
  }

  /** Arrondi au centième, comme Math.round(v * 100) / 100 en JavaScript. */
  public static double arrondi(double v) {
    return Math.floor(v * 100 + 0.5) / 100;
  }
}
