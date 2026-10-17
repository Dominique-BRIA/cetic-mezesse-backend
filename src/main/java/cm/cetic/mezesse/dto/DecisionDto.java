package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.StatutDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Journal des décisions du SG, consulté par le Proviseur (RG-30). */
public final class DecisionDto {

  private DecisionDto() {}

  public record Personne(String id, String nom, String prenom) {}

  /**
   * Décision telle que la voit le Proviseur. Les données d'annulation restent côté serveur (elles
   * peuvent contenir une empreinte de mot de passe).
   */
  public record Vue(
      String id,
      DomaineDecision domaine,
      String action,
      String libelle,
      String lien,
      String auteurId,
      Instant creeLe,
      StatutDecision statut,
      Instant desapprouveeLe,
      String desapprouveeParId,
      String motifDesapprobation,
      boolean annulee,
      Personne auteur,
      Personne desapprouveePar,
      Instant limite,
      boolean modifiable,
      String effetPrevu,
      String effet) {}

  public record Totaux(long enDelai, long desapprouvees) {}

  public record Page(List<Vue> decisions, String suivant, Totaux totaux, int delaiJours) {}

  public record Desapprobation(@NotBlank(message = "obligatoire") @Size(max = 500, message = "500 caractères au plus") String motif) {}
}
