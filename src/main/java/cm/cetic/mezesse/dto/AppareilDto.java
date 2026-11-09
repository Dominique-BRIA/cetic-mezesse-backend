package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.util.Textes;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ce que l'application mobile envoie sur elle-même, et la politique de mise à jour qu'elle lit. */
public final class AppareilDto {

  private AppareilDto() {}

  /** Signal envoyé à l'ouverture, par un compte connecté. */
  public record Signal(
      @NotBlank(message = "obligatoire") @Size(max = 64, message = "64 caractères au plus") String installationId,
      @Min(value = 0, message = "invalide") int numeroVersion,
      @NotBlank(message = "obligatoire") @Size(max = 32, message = "32 caractères au plus") String nomVersion,
      Integer correctif,
      @Size(max = 80, message = "80 caractères au plus") String modele,
      @Size(max = 16, message = "16 caractères au plus") String versionAndroid) {}

  /** Plantage : route publique, l'application peut se fermer avant toute connexion. Les textes trop longs sont coupés. */
  public record Rapport(
      @NotBlank(message = "obligatoire") @Size(max = 64, message = "64 caractères au plus") String installationId,
      @NotBlank(message = "obligatoire") @Size(max = 16, message = "16 caractères au plus") String type,
      @NotBlank(message = "obligatoire") String message,
      String pile,
      @Min(value = 0, message = "invalide") int numeroVersion,
      @NotBlank(message = "obligatoire") @Size(max = 32, message = "32 caractères au plus") String nomVersion,
      String modele,
      String versionAndroid) {
    public Rapport {
      message = Textes.couper(message, 2_000);
      pile = Textes.couper(pile, 16_000);
      modele = Textes.couper(modele, 80);
      versionAndroid = Textes.couper(versionAndroid, 16);
    }
  }

  public record Politique(boolean suspendue, List<Integer> versionsBloquees, int minimum, String message) {}
}
