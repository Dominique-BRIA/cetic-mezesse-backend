package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.dto.AppareilDto.Politique;
import cm.cetic.mezesse.entity.JournalConsole;
import cm.cetic.mezesse.util.Textes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Échanges entre l'API et la console technique. */
public final class ConsoleDto {

  private ConsoleDto() {}

  public record Connexion(@NotBlank(message = "obligatoire") String email, @NotBlank(message = "obligatoire") String motDePasse) {
    public Connexion {
      email = Textes.email(email);
    }
  }

  public record Moi(String id, String email, String nom) {}

  public record ReponseConnexion(String jeton, Moi admin) {}

  public record VersionRepartie(int numeroVersion, String nomVersion, long appareils) {}

  public record Apercu(
      long appareils,
      long actifsSeptJours,
      long comptesActifs,
      long comptesSansAppareil,
      long plantages24h,
      long plantagesOuverts,
      List<VersionRepartie> repartition,
      Politique politique) {}

  /** Un jour de la courbe. Les jours sans activité sont présents, à zéro. */
  public record JourAnalytique(LocalDate jour, long actifs, long nouveaux, Map<Integer, Long> parVersion, Map<String, Long> plantages) {}

  public record Cumul(long actifs, long nouveaux, long plantages) {}

  public record Part(String libelle, long valeur) {}

  public record RoleParc(String role, long equipes, long sansAppareil) {}

  public record Analytique(int jours, Cumul periode, Cumul precedente, List<JourAnalytique> serie, List<Part> modeles, List<Part> versionsAndroid, List<RoleParc> roles) {}

  public record LigneAppareil(
      String installationId,
      String compte,
      String role,
      int numeroVersion,
      String nomVersion,
      Integer correctif,
      String modele,
      String versionAndroid,
      Instant premiereVue,
      Instant derniereVue) {}

  public record CompteSansAppareil(String id, String nom, String role, String telephone) {}

  public record Parc(List<LigneAppareil> appareils, List<CompteSansAppareil> comptesSansAppareil) {}

  public record GroupePlantage(
      String empreinte,
      String type,
      String message,
      long occurrences,
      long appareils,
      Instant premiereFois,
      Instant derniereFois,
      boolean resolu,
      List<String> versions) {}

  public record OccurrencePlantage(
      String installationId, String compte, String modele, String versionAndroid, String nomVersion, int occurrences, Instant derniereFois) {}

  public record DetailPlantage(GroupePlantage groupe, String pile, List<OccurrencePlantage> occurrences) {}

  public record Resolution(@NotNull(message = "obligatoire") Boolean resolu) {}

  public record ReglagePolitique(
      @NotNull(message = "obligatoire") Boolean suspendue,
      @NotNull(message = "obligatoire") List<@Min(value = 1, message = "numéro invalide") Integer> versionsBloquees,
      @NotNull(message = "obligatoire") @Min(value = 0, message = "invalide") @Max(value = 1_000_000, message = "invalide") Integer minimum,
      @Size(max = 300, message = "300 caractères au plus") String message) {
    public ReglagePolitique {
      message = Textes.optionnel(message);
    }
  }

  public record VuePolitique(Politique politique, Instant majLe, String majPar) {}

  public record LigneJournal(String id, Instant date, String adminEmail, String action, String cible, String detail, String adresse) {
    public static LigneJournal de(JournalConsole j) {
      return new LigneJournal(j.getId(), j.getDate(), j.getAdminEmail(), j.getAction(), j.getCible(), j.getDetail(), j.getAdresse());
    }
  }
}
