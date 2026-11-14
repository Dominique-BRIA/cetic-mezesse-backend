package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.dto.AppelDto.Historique;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Consultation des absences et justifications (SG ; Proviseur en lecture). Durées en minutes. */
public final class AbsencesDto {

  private AbsencesDto() {}

  public record Piece(String id, String nom, String type, int taille) {}

  public record JustificationVue(
      String id, String motifId, String motif, String description, LocalDate dateReception, String par, Instant creeLe, Instant modifieLe, List<Piece> pieces) {}

  /** Une absence : un élève, une séance d'un jour. « justification » vide : NON_JUSTIFIEE. */
  public record Ligne(
      String id, String inscriptionId, String eleveId, String nom, String prenoms, String matricule, String classeId, String classe, LocalDate date,
      String seanceId, String matiere, String debut, String fin, int duree, boolean enseignantAbsent, JustificationVue justification,
      /** Numéro d'ordre de l'élève dans sa classe. */
      int numero) {}

  /** Totaux du filtre : total = justifiées + non justifiées. */
  public record Totaux(long absences, long minutes, long minutesJustifiees, long minutesNonJustifiees) {}

  public record Page(List<Ligne> lignes, int page, int taille, long total, Totaux totaux) {}

  public record Detail(Ligne absence, List<Historique> historique) {}

  /** Justification de plusieurs absences d'un même élève : une justification pour chacune, sans dossier commun. */
  public record Justifier(
      @NotEmpty(message = "choisissez au moins une absence") List<String> absenceIds,
      @NotBlank(message = "obligatoire") String motifId,
      @Size(max = 1000, message = "1000 caractères au plus") String description,
      @NotNull(message = "obligatoire") LocalDate dateReception) {}

  /** Correction d'une justification ; les pièces retirées sont désignées par leur identifiant. */
  public record ModifierJustification(
      @NotBlank(message = "obligatoire") String motifId,
      @Size(max = 1000, message = "1000 caractères au plus") String description,
      @NotNull(message = "obligatoire") LocalDate dateReception,
      List<String> piecesRetirees) {}

  public record Motif(String id, String libelle, int ordre, boolean actif) {}

  public record MotifRequete(@NotBlank(message = "obligatoire") @Size(max = 80, message = "80 caractères au plus") String libelle, Boolean actif) {}

  public record Resultat(String message, int nombre) {}

  /**
   * Bilan d'un élève pour une période : heures de la période, heures non justifiées cumulées depuis la
   * rentrée, sanction retenue pour le bulletin, jours d'exclusion, palier d'exclusion atteint.
   */
  public record BilanEleve(
      String inscriptionId, String eleveId, String nom, String prenoms, String matricule, int seances, int minutes, int justifiees, int nonJustifiees,
      int cumulNonJustifiees, String sanction, String libelleSanction, int joursExclusion, boolean palierExclusion, boolean proviseurPrevenu,
      DecisionVue decision,
      /** Numéro d'ordre de l'élève dans sa classe. */
      int numero,
      String classeId,
      String classe) {}

  /** Décision du Directeur pour un élève qui a atteint les heures d'exclusion. */
  public record DecisionVue(String id, String type, Integer jours, LocalDate date, String motif) {}

  /** Exclusion temporaire (1 à 8 jours ouvrés, à partir de {@code date}), définitive, ou sursis. */
  public record DeciderExclusion(
      @NotBlank(message = "obligatoire") String inscriptionId,
      @NotNull(message = "à choisir") cm.cetic.mezesse.entity.DecisionExclusion.Type type,
      Integer jours,
      LocalDate date,
      @Size(max = 500, message = "500 caractères au plus") String motif) {}

  /** Bilan d'une classe, avec les sanctions réglées par le SG (minutes, de la plus faible à la plus forte). */
  public record Bilan(String classeId, String periode, List<cm.cetic.mezesse.service.BilanAbsences.Regle> sanctions, List<BilanEleve> eleves) {}

  /** Séance non assurée : l'enseignant prévu était absent (marqué par le SG sur la feuille d'appel). */
  public record SeanceNonAssuree(
      String seanceId, LocalDate date, String classeId, String classe, String matiere, String debut, String fin, int duree, String motif, String enseignantId,
      String enseignant) {}

  /** Total d'un enseignant sur la période choisie. */
  public record TotalEnseignant(String id, String nom, String prenom, int seances, int minutes) {}

  public record EnseignantsAbsents(List<TotalEnseignant> totaux, List<SeanceNonAssuree> seances) {}
}
