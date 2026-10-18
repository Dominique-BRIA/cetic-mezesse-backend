package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import java.time.LocalDate;
import java.util.List;

/** Tableau d'honneur : élèves dont la moyenne atteint le seuil fixé par la direction. */
public final class TableauHonneurDto {

  private TableauHonneurDto() {}

  public record Eleve(
      String inscriptionId,
      String eleveId,
      String matricule,
      String nom,
      String prenoms,
      Sexe sexe,
      LocalDate dateNaissance,
      String lieuNaissance,
      double moyenne,
      Rang rang) {}

  /**
   * @param effectif élèves classés de la classe (ayant une moyenne)
   * @param moyenne moyenne générale de la classe
   * @param profPrincipal « Pauline MANGA », ou null
   */
  public record Classe(String id, String nom, int effectif, Double moyenne, String profPrincipal, List<Eleve> eleves) {}

  public record Tableau(double seuil, Periode periode, String annee, int total, List<Classe> classes) {}
}
