package cm.cetic.mezesse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/** Fusion de deux fiches en double (enseignants ou élèves) : aperçu puis exécution. */
public final class FusionDto {

  private FusionDto() {}

  /** Une des deux fiches, telle que la direction la reconnaît. */
  public record Fiche(String id, String libelle, String detail) {}

  /** Information qui diffère d'une fiche à l'autre : le choix par défaut est « garde », sauf si elle est vide. */
  public record Champ(String cle, String libelle, String garde, String autre, String parDefaut) {}

  public record Deplacement(String libelle, long nombre) {}

  public record ApercuEnseignants(Fiche garde, Fiche autre, List<Champ> differences, List<Deplacement> deplacements, List<String> avertissements) {}

  /** Une année où l'une des fiches est inscrite : l'inscription est déplacée, réunie, ou la fusion est impossible. */
  public record Annee(String annee, String classeGarde, String classeAutre, long notesAutre, long conflits, String sort) {}

  public record ApercuEleves(
      Fiche garde, Fiche autre, List<Champ> differences, List<Annee> annees, long conflits, List<String> avertissements, boolean possible) {}

  /**
   * @param choix pour chaque information qui diffère : « garde » ou « autre » ; absente, le choix par défaut
   */
  public record DemandeEnseignants(@NotBlank(message = "obligatoire") String garde, @NotBlank(message = "obligatoire") String autre, Map<String, String> choix) {}

  /**
   * @param notes en cas de deux notes pour la même matière et la même séquence : celle de « garde » ou de « autre »
   */
  public record DemandeEleves(
      @NotBlank(message = "obligatoire") String garde,
      @NotBlank(message = "obligatoire") String autre,
      Map<String, String> choix,
      @Pattern(regexp = "garde|autre", message = "garde ou autre") String notes) {}

  public record Resultat(String message, String gardeId) {}
}
