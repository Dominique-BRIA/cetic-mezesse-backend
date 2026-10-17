package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.ProfilClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import cm.cetic.mezesse.service.calcul.Resultats.Tranche;
import java.util.List;

/** Statistiques d'une matière, d'une classe et de l'établissement. */
public final class StatistiqueDto {

  private StatistiqueDto() {}

  public record Resume(int effectif, Double moyenne, Double min, Double max, Double tauxReussite, Double ecartType, int reussis) {}

  public record ResumeMatiere(
      int effectif, Double moyenne, Double min, Double max, Double tauxReussite, Double ecartType, int reussis, boolean evaluee) {}

  public record ParSexe(Sexe sexe, int effectif, Double moyenne, Double min, Double max, Double tauxReussite, Double ecartType, int reussis) {}

  public record Grade(String grade, int effectif) {}

  public record Ref(String id, String nom) {}

  // ---------- Matière ----------

  public record ClasseRef(String id, String nom, String anneeId) {}

  public record MatiereRef(String id, String nom, int coefficient, ClasseRef classe) {}

  public record EleveNote(
      String inscriptionId, String eleveId, String matricule, String nom, String prenoms, Sexe sexe, Double note, Rang rang, String grade) {}

  public record EvolutionMatiere(Periode periode, Double moyenne, Double tauxReussite, boolean evaluee) {}

  public record Matiere(
      MatiereRef matiere,
      Periode periode,
      ResumeMatiere resume,
      List<Tranche> repartition,
      List<Grade> grades,
      List<ParSexe> parSexe,
      List<EvolutionMatiere> evolution,
      List<EleveNote> eleves) {}

  // ---------- Classe ----------

  public record EleveMoyenne(String inscriptionId, String eleveId, String matricule, String nom, String prenoms, Sexe sexe, Double moyenne, Rang rang) {}

  public record MatiereClasse(
      String id, String nom, int coefficient, String professeur, boolean evaluee, Double moyenne, Double tauxReussite, Double min, Double max, Double ecartType) {}

  public record Evolution(Periode periode, Double moyenne, Double tauxReussite) {}

  public record Classe(
      Ref classe,
      Periode periode,
      ProfilClasse profil,
      List<Tranche> repartition,
      List<ParSexe> parSexe,
      List<MatiereClasse> matieres,
      List<Evolution> evolution,
      List<EleveMoyenne> classement) {}

  // ---------- Établissement ----------

  public record Totaux(long eleves, long renvoyes, int classes, long enseignants, long filles, long garcons) {}

  public record EleveEtablissement(
      String inscriptionId, String eleveId, String matricule, String nom, String prenoms, Sexe sexe, Ref classe, Double moyenne, Rang rangClasse, Rang rang) {}

  public record ClasseEtablissement(
      String id,
      String nom,
      int niveau,
      int effectif,
      Double moyenneGenerale,
      Double min,
      Double max,
      int nombreMoyennes,
      Double tauxReussite,
      Double ecartType,
      Rang rang) {}

  public record MatiereComparee(String nom, int classes, double moyenne, double tauxReussite) {}

  public record Etablissement(
      Periode periode,
      Totaux totaux,
      Resume resume,
      List<Tranche> repartition,
      List<ParSexe> parSexe,
      List<EleveEtablissement> premier,
      List<EleveEtablissement> dernier,
      ClasseEtablissement premiereClasse,
      ClasseEtablissement derniereClasse,
      List<ClasseEtablissement> classes,
      List<MatiereComparee> matieres,
      List<EleveEtablissement> classement) {}
}
