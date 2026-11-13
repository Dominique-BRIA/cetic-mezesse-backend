package cm.cetic.mezesse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Page « À vérifier » : ce qui mérite un coup d'œil de la direction, sans jamais rien bloquer. */
public final class VerificationDto {

  private VerificationDto() {}

  /**
   * Ce qu'on peut faire d'un point : ouvrir la page concernée, ou fusionner deux fiches.
   *
   * @param type « ouvrir », « fusionner-enseignants » ou « fusionner-eleves »
   */
  public record Action(String type, String lien, String a, String b) {}

  /**
   * @param cle identifiant stable du point, pour le marquer « c'est normal »
   * @param gravite FORTE (à traiter) ou FAIBLE (à confirmer)
   */
  public record Point(String cle, String gravite, String titre, String detail, Action action, boolean ignore) {}

  public record Categorie(String code, String titre, String explication, List<Point> points) {}

  public record Rapport(List<Categorie> categories, long total, long ignores) {}

  public record Ignorer(@NotBlank(message = "obligatoire") @Size(max = 200, message = "200 caractères au plus") String cle, @Size(max = 300, message = "300 caractères au plus") String libelle) {}
}
