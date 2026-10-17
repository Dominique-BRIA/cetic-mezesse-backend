package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.util.Textes;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Classes et spécialités. */
public final class ClasseDto {

  private ClasseDto() {}

  public record Personne(String id, String nom, String prenom) {}

  public record SpecialiteRef(String id, String nom, String nomEn) {}

  public record Ref(String id, String nom) {}

  public record Comptes(long matieres, long inscriptions) {}

  public record Vue(
      String id,
      String anneeId,
      String nom,
      String nomEn,
      int niveau,
      String specialiteId,
      int numero,
      String profPrincipalId,
      Personne profPrincipal,
      SpecialiteRef specialite,
      List<Ref> groupes,
      @JsonProperty("_count") Comptes nombres) {}

  /** Le nom n'est pas saisi : il découle du niveau, de la spécialité et de la lettre (RG-29). */
  public record Requete(
      @NotNull(message = "obligatoire") @Min(value = 1, message = "entre 1 et 7") @Max(value = 7, message = "entre 1 et 7") Integer niveau,
      String specialiteId,
      String profPrincipalId) {

    public Requete {
      specialiteId = Textes.optionnel(specialiteId);
      profPrincipalId = Textes.optionnel(profPrincipalId);
    }
  }

  public record SpecialiteNombres(long classes) {}

  public record SpecialiteVue(String id, String nom, String nomEn, Instant creeLe, @JsonProperty("_count") SpecialiteNombres nombres) {}

  public record SpecialiteRequete(
      @NotBlank(message = "obligatoire") @Size(max = 60, message = "60 caractères au plus") String nom,
      @Size(max = 60, message = "60 caractères au plus") String nomEn) {

    public SpecialiteRequete {
      nom = Textes.nettoyer(nom);
      nomEn = Textes.optionnel(nomEn);
    }
  }
}
