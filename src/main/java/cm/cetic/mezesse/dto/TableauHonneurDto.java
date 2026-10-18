package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import java.util.List;

/** Tableau d'honneur : élèves dont la moyenne atteint le seuil fixé par la direction. */
public final class TableauHonneurDto {

  private TableauHonneurDto() {}

  public record Eleve(String inscriptionId, String eleveId, String matricule, String nom, String prenoms, Sexe sexe, double moyenne, Rang rang) {}

  /** effectif : élèves classés de la classe (ayant une moyenne). */
  public record Classe(String id, String nom, int effectif, List<Eleve> eleves) {}

  public record Tableau(double seuil, Periode periode, String annee, int total, List<Classe> classes) {}
}
