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

  /**
   * Résultat d'un élève sur la période.
   *
   * @param classe faux si l'élève n'est pas classé (trop peu de matières composées) : pas de moyenne ni de rang
   * @param minutesNonJustifiees absences non justifiées de la période (départage de la première place, tableau d'honneur)
   * @param nonClasse la période a des notes dans la classe mais l'élève n'est pas classé (même sans aucune note) : « NC »
   * @param designe dans chaque groupe d'ex æquo (et pour un rang seul), celui que le départage désigne : il porte son
   *     rang sans « ex » sur son bulletin et passe devant les autres au tableau d'honneur ; les rangs gardent leurs ex æquo
   */
  public record ResultatEleve(
      String inscriptionId,
      List<LigneMatiere> matieres,
      List<LigneGroupe> groupes,
      int totalCoef,
      double totalPoints,
      Double moyenne,
      Rang rang,
      String cote,
      boolean classe,
      int minutesNonJustifiees,
      boolean nonClasse,
      boolean designe) {

    /** Le rang à imprimer sur le bulletin de l'élève : sans « ex » pour l'élève désigné de son groupe d'ex æquo. */
    public Rang rangBulletin() {
      return designe && rang != null ? new Rang(rang.rang(), false) : rang;
    }
  }

  /** Critère de départage de la première place : moyenne d'un groupe de matières (par son nom), discipline, ordre alphabétique. */
  public record Critere(String type, String groupe) {
    public static final String GROUPE = "GROUPE";
    public static final String DISCIPLINE = "DISCIPLINE";
    public static final String ALPHABETIQUE = "ALPHABETIQUE";
  }

  /**
   * Règles réglées dans les paramètres.
   *
   * @param seuilNonClasse pourcentage de matières composées en dessous duquel l'élève n'est pas classé (33,33)
   * @param seuilIgnorees pourcentage à partir duquel les matières non composées sont ignorées ; entre les deux elles valent 0 (66,67)
   * @param criteres ordre de départage de la première place (vide : matières professionnelles, discipline, ordre alphabétique)
   * @param moyenneDeliberation moyenne annuelle qui permet le passage après délibération (null : pas de délibération)
   */
  public record Regles(double seuilNonClasse, double seuilIgnorees, List<Critere> criteres, Double moyenneDeliberation) {
    public static final Regles DEFAUT = new Regles(33.33, 66.67, List.of(), null);
  }

  /** Décision de fin d'année, d'après la moyenne annuelle. */
  public enum Decision {
    ADMIS("Admis(e) en classe supérieure", "Promoted"),
    ADMIS_DELIBERATION("Admis(e) après délibération", "Promoted (deliberation)"),
    REDOUBLE("Redouble", "Repeats the year");

    public final String fr;
    public final String en;

    Decision(String fr, String en) {
      this.fr = fr;
      this.en = en;
    }
  }

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
