package cm.cetic.mezesse.service.calcul;

import java.util.List;

/** Types du calcul des résultats d'une classe (entrées et sorties de CalculResultats). */
public final class Resultats {

  private Resultats() {}

  public record Groupe(String id, String nom, String nomEn, int ordre) {}

  public record MatiereEntree(String id, String nom, String nomEn, int coefficient, int ordre, Groupe groupe, String professeur) {}

  /** Note d'un élève dans une matière pour une séquence (numéro 1 à 6). */
  public record NoteEntree(String inscriptionId, String matiereId, int sequence, double valeur) {}

  /** Mention et sa borne basse (incluse). */
  public record Seuil(String libelle, String libelleLong, double min) {}

  public record Baremes(List<Seuil> grades, List<Seuil> cotes) {}

  public record Rang(int rang, boolean exAequo) {}

  /**
   * Note d'un élève dans une matière sur la période.
   *
   * @param note null quand la matière n'est pas évaluée sur la période
   * @param detail notes des séquences (trimestre) ou des trimestres (année)
   */
  public record LigneMatiere(String matiereId, Double note, List<Double> detail, Double total, Rang rang, String grade) {

    LigneMatiere avecRang(Rang r) {
      return new LigneMatiere(matiereId, note, detail, total, r, grade);
    }
  }

  public record StatMatiere(
      MatiereEntree matiere, boolean evaluee, Double moyenne, Double min, Double max, Double tauxReussite, Double ecartType, int effectif) {}

  public record LigneGroupe(String groupeId, int coefficient, double total, Double moyenne) {}

  public record ResultatEleve(
      String inscriptionId,
      List<LigneMatiere> matieres,
      List<LigneGroupe> groupes,
      int totalCoef,
      double totalPoints,
      Double moyenne,
      Rang rang,
      String cote) {}

  /** Profil de la classe ; nombreMoyennes = élèves à 10 ou plus. */
  public record ProfilClasse(
      int effectif, Double moyenneGenerale, Double min, Double max, int nombreMoyennes, Double tauxReussite, Double ecartType) {}

  public record ResultatsClasse(
      Periode periode, List<StatMatiere> matieres, List<Groupe> groupes, List<ResultatEleve> eleves, ProfilClasse profil) {

    public ResultatEleve eleve(String inscriptionId) {
      return eleves.stream().filter(e -> e.inscriptionId().equals(inscriptionId)).findFirst().orElse(null);
    }
  }

  public record Tranche(String tranche, int effectif) {}
}
