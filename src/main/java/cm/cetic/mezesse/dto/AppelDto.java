package cm.cetic.mezesse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Feuille d'appel du jour : séances en colonnes, élèves en lignes, absences cochées. */
public final class AppelDto {

  private AppelDto() {}

  private static final String HEURE = "^([01]\\d|2[0-3]):[0-5]\\d$";

  public record SequenceRef(String id, int numero, int trimestre, boolean ouverte) {}

  public record Personne(String id, String nom, String prenom) {}

  /** « id » vide : séance prévue par l'emploi du temps, pas encore enregistrée (feuille non commencée). */
  public record SeanceVue(
      String id, String creneauId, String matiereId, String matiere, Personne enseignant, Personne remplacant, String debut, String fin, int duree,
      String statut, boolean ajoutee, boolean enseignantAbsent, String motif) {}

  public record EleveVue(String inscriptionId, String eleveId, String nom, String prenoms, String matricule, boolean renvoye) {}

  public record Case(@NotBlank String seanceId, @NotBlank String inscriptionId) {}

  /** Feuille enregistrée : par qui, quand, dernière correction. */
  public record FicheVue(String id, String sequenceId, Instant creeLe, String creePar, Instant modifieLe) {}

  public record Vue(
      String classeId, String classe, LocalDate date, int jour, FicheVue fiche, List<SequenceRef> sequences, String sequenceProposee,
      List<SeanceVue> seances, List<EleveVue> eleves, List<Case> absences, boolean emploiDuTemps, List<Case> justifiees) {}

  /** Absence avant l'enregistrement : la séance est désignée par son créneau de l'emploi du temps. */
  public record CaseCreneau(@NotBlank String creneauId, @NotBlank String inscriptionId) {}

  /** Premier enregistrement de l'appel du jour : la feuille, ses séances et ses absences, d'un coup. */
  public record Enregistrer(@NotBlank String classeId, @NotNull LocalDate date, String sequenceId, @NotNull @Valid List<CaseCreneau> absences) {}

  /**
   * Correction : toutes les absences de la feuille (ce qui manque est remis présent) et, si elle a changé, la
   * séquence ; le tout dans la même opération.
   */
  public record Absences(@NotNull @Valid List<Case> absences, String sequenceId) {}

  public record ChoixSequence(@NotBlank String sequenceId) {}

  /** Ajustement d'une séance pour ce jour : horaire, remplaçant, annulation, enseignant absent. */
  public record SeanceModif(
      @NotBlank @Pattern(regexp = HEURE, message = "heure au format 07:30") String debut,
      @NotBlank @Pattern(regexp = HEURE, message = "heure au format 07:30") String fin,
      String remplacantId,
      boolean enseignantAbsent,
      boolean annulee,
      @Size(max = 300) String motif) {}

  /** Séance ajoutée pour ce jour seulement (cours déplacé ici, rattrapage). */
  public record SeanceAjout(
      @NotBlank String matiereId,
      @NotBlank @Pattern(regexp = HEURE, message = "heure au format 07:30") String debut,
      @NotBlank @Pattern(regexp = HEURE, message = "heure au format 07:30") String fin,
      @Size(max = 300) String motif) {}

  public record Historique(String id, String action, String libelle, Map<String, Object> avant, Map<String, Object> apres, String par, Instant creeLe) {}

  /** État de l'appel d'une classe pour un jour : séances prévues, appel enregistré, élèves absents. */
  public record EtatClasse(String classeId, String classe, int niveau, boolean emploiDuTemps, int seancesPrevues, boolean fait, int elevesAbsents) {}
}
