package cm.cetic.mezesse.util;

import java.text.Normalizer;
import java.util.Locale;

/** Nettoyage des saisies avant validation : espaces retirés, champs vides ramenés à null. */
public final class Textes {

  private Textes() {}

  public static String nettoyer(String s) {
    return s == null ? null : s.strip();
  }

  /** Champ facultatif : vide ou blanc devient null. */
  public static String optionnel(String s) {
    String t = nettoyer(s);
    return t == null || t.isEmpty() ? null : t;
  }

  /** « 699 00.00-00 » → « 699000000 » ; vide → null. */
  public static String telephone(String s) {
    String t = optionnel(s);
    return t == null ? null : t.replaceAll("[\\s.-]", "");
  }

  public static String email(String s) {
    String t = optionnel(s);
    return t == null ? null : t.toLowerCase(Locale.ROOT);
  }

  public static String majuscules(String s) {
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  /** Sans accents et en capitales : « CETIC DE MEZESSE ». */
  public static String capitales(String s) {
    return sansAccents(s).toUpperCase(Locale.ROOT);
  }

  public static String sansAccents(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }

  /** Nom de fichier sûr : « 1re année Mécanique A » → « 1re-annee-mecanique-a ». */
  public static String nomFichier(String s) {
    return sansAccents(s)
        .replaceAll("[^a-zA-Z0-9-]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "")
        .toLowerCase(Locale.ROOT);
  }

  /** Coupe un texte trop long pour sa colonne. */
  public static String couper(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }
}
