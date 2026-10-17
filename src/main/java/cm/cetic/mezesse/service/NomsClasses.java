package cm.cetic.mezesse.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Nom des classes (RG-29) : « 1re année Mécanique A ». La lettre n'apparaît que lorsque le niveau et
 * la spécialité comptent plusieurs classes dans l'année ; elle suit l'ordre de création (numero).
 */
public final class NomsClasses {

  private NomsClasses() {}

  public record Nom(String nom, String nomEn) {}

  public record Renommage(String avant, String apres) {}

  public static String ordinal(int niveau) {
    return niveau == 1 ? "1re" : niveau + "e";
  }

  public static String lettre(int rang) {
    return rang < 26 ? String.valueOf((char) ('A' + rang)) : String.valueOf(rang + 1);
  }

  /**
   * @param specialiteNom nom de la spécialité, ou null pour une classe sans spécialité
   * @param specialiteNomEn nom anglais (le nom français sert à défaut)
   */
  public static List<Nom> noms(int niveau, String specialiteNom, String specialiteNomEn, int nombre) {
    List<Nom> noms = new ArrayList<>();
    for (int i = 0; i < nombre; i++) {
      String suffixe = nombre > 1 ? " " + lettre(i) : "";
      String fr = ordinal(niveau) + " année" + (specialiteNom != null ? " " + specialiteNom : "") + suffixe;
      String en =
          "Year " + niveau + (specialiteNom != null ? " " + (specialiteNomEn != null ? specialiteNomEn : specialiteNom) : "") + suffixe;
      noms.add(new Nom(fr, en));
    }
    return noms;
  }

  public static String decrire(List<Renommage> renommages) {
    if (renommages.isEmpty()) return "";
    return " ; " + String.join(", ", renommages.stream().map(r -> r.avant() + " devient " + r.apres()).toList());
  }
}
