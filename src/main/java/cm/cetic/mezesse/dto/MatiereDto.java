package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.util.Textes;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Groupes de matières et matières. */
public final class MatiereDto {

  private MatiereDto() {}

  public record Ref(String id, String nom) {}

  public record GroupeNombres(long matieres) {}

  public record GroupeVue(
      String id, String anneeId, String nom, String nomEn, int ordre, List<Ref> classes, @JsonProperty("_count") GroupeNombres nombres) {}

  public record GroupeRequete(
      @NotBlank(message = "obligatoire") @Size(max = 80, message = "80 caractères au plus") String nom,
      @Size(max = 80, message = "80 caractères au plus") String nomEn,
      @NotNull(message = "obligatoire") @Min(value = 1, message = "entre 1 et 20") @Max(value = 20, message = "entre 1 et 20") Integer ordre,
      @NotEmpty(message = "choisissez au moins une classe") List<String> classeIds) {

    public GroupeRequete {
      nom = Textes.nettoyer(nom);
      nomEn = Textes.optionnel(nomEn);
    }
  }

  public record ClasseRef(String id, String nom, int niveau) {}

  public record GroupeRef(String id, String nom, int ordre) {}

  public record Personne(String id, String nom, String prenom) {}

  public record Nombres(long notes) {}

  public record Vue(
      String id,
      String classeId,
      String groupeId,
      String nom,
      String nomEn,
      int coefficient,
      int ordre,
      String professeurId,
      ClasseRef classe,
      GroupeRef groupe,
      Personne professeur,
      @JsonProperty("_count") Nombres nombres) {}

  public record Requete(
      @NotBlank(message = "obligatoire") String classeId,
      @NotBlank(message = "obligatoire") String groupeId,
      @NotBlank(message = "obligatoire") @Size(max = 80, message = "80 caractères au plus") String nom,
      @Size(max = 80, message = "80 caractères au plus") String nomEn,
      @NotNull(message = "obligatoire") @Min(value = 1, message = "entre 1 et 20") @Max(value = 20, message = "entre 1 et 20") Integer coefficient,
      @NotNull(message = "obligatoire") @Min(value = 1, message = "entre 1 et 50") @Max(value = 50, message = "entre 1 et 50") Integer ordre,
      String professeurId) {

    public Requete {
      nom = Textes.nettoyer(nom);
      nomEn = Textes.optionnel(nomEn);
      professeurId = Textes.optionnel(professeurId);
    }
  }
}
