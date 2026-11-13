package cm.cetic.mezesse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

/** Emploi du temps d'une classe : versions datées et créneaux de la semaine type. */
public final class EmploiDuTempsDto {

  private EmploiDuTempsDto() {}

  /** Une version : valable de « valableDu » (vide : depuis la rentrée) à « valableAu » (vide : jusqu'à nouvel ordre). */
  public record Version(String id, LocalDate valableDu, LocalDate valableAu, boolean enVigueur) {}

  public record Personne(String id, String nom, String prenom) {}

  public record MatiereRef(String id, String nom, Personne professeur) {}

  /** Un autre cours attendu en même temps : même classe, ou même enseignant dans une autre classe. */
  public record Conflit(String classe, String matiere, String debut, String fin, boolean memeClasse) {}

  /** Heures au format « 07:30 » ; durée en minutes. */
  public record Creneau(String id, int jour, String debut, String fin, int duree, MatiereRef matiere, List<Conflit> conflits) {}

  public record Vue(String classeId, List<Version> versions, Version version, List<Creneau> creneaux, int minutesParSemaine) {}

  /** Nouvelle version à partir d'une date ; « copier » reprend les créneaux de la version en vigueur à cette date. */
  public record NouvelleVersion(@NotBlank String classeId, LocalDate valableDu, boolean copier) {}

  private static final String HEURE = "^([01]\\d|2[0-3]):[0-5]\\d$";

  /** « versionId » vide : la version en vigueur aujourd'hui (créée si la classe n'en a pas encore). */
  public record CreneauRequete(
      @NotBlank String classeId,
      String versionId,
      @Min(1) @Max(6) int jour,
      @NotBlank @Pattern(regexp = HEURE, message = "heure au format 07:30") String debut,
      @NotBlank @Pattern(regexp = HEURE, message = "heure au format 07:30") String fin,
      @NotBlank String matiereId) {}
}
