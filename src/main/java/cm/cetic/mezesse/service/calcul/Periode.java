package cm.cetic.mezesse.service.calcul;

import cm.cetic.mezesse.exception.ApiException;
import java.util.List;

/** Période de calcul : une séquence (S1 à S6), un trimestre (T1 à T3) ou l'année (A). */
public enum Periode {
  S1, S2, S3, S4, S5, S6, T1, T2, T3, A;

  public static final List<Periode> SEQUENCES = List.of(S1, S2, S3, S4, S5, S6);
  public static final List<Periode> TRIMESTRES = List.of(T1, T2, T3);

  public static Periode lire(String code) {
    if (code != null) {
      for (Periode p : values()) if (p.name().equals(code)) return p;
    }
    throw ApiException.invalide("Période invalide (S1 à S6, T1 à T3 ou A).");
  }

  public boolean estSequence() {
    return name().charAt(0) == 'S';
  }

  public boolean estTrimestre() {
    return name().charAt(0) == 'T';
  }

  /** Numéro de la séquence ou du trimestre (0 pour l'année). */
  public int numero() {
    return this == A ? 0 : Integer.parseInt(name().substring(1));
  }

  /** Numéros des séquences couvertes par la période. */
  public List<Integer> sequences() {
    if (this == A) return List.of(1, 2, 3, 4, 5, 6);
    int n = numero();
    return estSequence() ? List.of(n) : List.of(2 * n - 1, 2 * n);
  }

  /** Sous-périodes rappelées sur un bulletin : les séquences d'un trimestre, les trimestres de l'année. */
  public List<Periode> sousPeriodes() {
    if (this == A) return TRIMESTRES;
    if (estTrimestre()) return List.of(valueOf("S" + (2 * numero() - 1)), valueOf("S" + 2 * numero()));
    return List.of();
  }

  public String libelle() {
    if (this == A) return "Année";
    return (estSequence() ? "Séquence " : "Trimestre ") + numero();
  }

  public String libelleEn() {
    if (this == A) return "Year";
    return (estSequence() ? "Sequence " : "Term ") + numero();
  }
}
