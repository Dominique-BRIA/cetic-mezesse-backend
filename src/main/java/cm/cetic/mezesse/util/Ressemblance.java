package cm.cetic.mezesse.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ressemblance entre deux noms saisis à la main : « Ebo'o Nkou » et « EBOO NKOU », « ZANGA Therence »
 * et « Therence Zidane ZANGA », « Steve » et « Steeve ». Accents, majuscules, apostrophes, espaces et
 * ordre des mots sont ignorés ; une lettre d'écart (deux pour un nom long) reste « proche ».
 */
public final class Ressemblance {

  private Ressemblance() {}

  public enum Force {
    /** Mêmes lettres, seule la présentation diffère. */
    IDENTIQUE,
    /** Une faute de frappe, ou un nom complet qui en contient un autre. */
    PROCHE
  }

  public static String normaliser(String s) {
    if (s == null) return "";
    String sansAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return sansAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").strip();
  }

  static List<String> mots(String s) {
    String n = normaliser(s);
    return n.isEmpty() ? List.of() : Arrays.stream(n.split(" ")).sorted().toList();
  }

  /** @return null si les deux noms ne se ressemblent pas */
  public static Force comparer(String a, String b) {
    List<String> ma = mots(a);
    List<String> mb = mots(b);
    if (ma.isEmpty() || mb.isEmpty()) return null;
    String ja = String.join(" ", ma);
    String jb = String.join(" ", mb);
    if (ja.equals(jb) || normaliser(a).replace(" ", "").equals(normaliser(b).replace(" ", ""))) return Force.IDENTIQUE;
    int seuil = Math.min(ja.length(), jb.length()) >= 12 ? 2 : 1;
    if (Math.abs(ja.length() - jb.length()) <= seuil && distance(ja, jb) <= seuil) return Force.PROCHE;
    Set<String> sa = new HashSet<>(ma);
    Set<String> sb = new HashSet<>(mb);
    if (Math.min(sa.size(), sb.size()) >= 2 && (sa.containsAll(sb) || sb.containsAll(sa))) return Force.PROCHE;
    return null;
  }

  /** Distance d'édition (insertion, suppression, remplacement d'une lettre). */
  static int distance(String a, String b) {
    int[] precedente = new int[b.length() + 1];
    int[] courante = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) precedente[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      courante[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        courante[j] = Math.min(Math.min(courante[j - 1] + 1, precedente[j] + 1), precedente[j - 1] + cout);
      }
      int[] t = precedente;
      precedente = courante;
      courante = t;
    }
    return precedente[b.length()];
  }
}
