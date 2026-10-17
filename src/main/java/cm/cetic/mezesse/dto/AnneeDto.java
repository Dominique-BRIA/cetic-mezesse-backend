package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.util.Textes;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/** Années scolaires et séquences d'évaluation. */
public final class AnneeDto {

  private AnneeDto() {}

  public record SequenceVue(String id, String anneeId, int numero, int trimestre, boolean saisieOuverte) {

    public static SequenceVue de(Sequence s) {
      return new SequenceVue(s.getId(), s.getAnneeId(), s.getNumero(), s.getTrimestre(), s.isSaisieOuverte());
    }
  }

  public record Vue(String id, String libelle, boolean active, Instant creeLe) {

    public static Vue de(AnneeScolaire a) {
      return new Vue(a.getId(), a.getLibelle(), a.isActive(), a.getCreeLe());
    }
  }

  public record Comptes(long classes, long inscriptions) {}

  /** Ligne de la liste des années : séquences et nombre de classes et d'inscriptions. */
  public record Ligne(
      String id,
      String libelle,
      boolean active,
      Instant creeLe,
      List<SequenceVue> sequences,
      @JsonProperty("_count") Comptes nombres) {}

  public record Creation(
      @NotNull(message = "obligatoire") @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "format attendu : 2026-2027") String libelle,
      String copierDepuisId,
      Boolean activer) {

    public Creation {
      libelle = Textes.nettoyer(libelle);
      copierDepuisId = Textes.optionnel(copierDepuisId);
      if (activer == null) activer = false;
    }
  }

  /** Ouverture ou fermeture de la saisie des notes d'une séquence. */
  public record Saisie(@NotNull(message = "obligatoire") Boolean saisieOuverte) {}
}
