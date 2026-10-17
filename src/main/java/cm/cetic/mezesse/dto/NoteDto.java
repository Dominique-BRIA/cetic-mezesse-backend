package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.dto.AnneeDto.SequenceVue;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.ProfilClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import cm.cetic.mezesse.util.Textes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Saisie des notes, suivi de la saisie et relevés. */
public final class NoteDto {

  private NoteDto() {}

  public record ClasseRef(String id, String nom, String anneeId) {}

  public record ClasseNiveau(String id, String nom, int niveau) {}

  public record GroupeNom(String nom) {}

  public record Personne(String nom, String prenom) {}

  public record MatiereFeuille(String id, String nom, int coefficient, ClasseRef classe, GroupeNom groupe) {}

  public record EleveFeuille(String inscriptionId, String matricule, String nom, String prenoms, Sexe sexe, Double note, Instant majLe) {}

  /** Feuille de notes d'une matière pour une séquence : tous les élèves actifs de la classe. */
  public record Feuille(MatiereFeuille matiere, SequenceVue sequence, String competence, List<EleveFeuille> eleves) {}

  public record NoteSaisie(
      @NotBlank(message = "obligatoire") String inscriptionId,
      @DecimalMin(value = "0", message = "une note est comprise entre 0 et 20")
          @DecimalMax(value = "20", message = "une note est comprise entre 0 et 20")
          @Digits(integer = 2, fraction = 2, message = "deux décimales au plus")
          Double valeur) {}

  /**
   * Enregistrement d'une feuille (ou d'une partie). valeur = null efface la note. competence absente :
   * inchangée ; vide : effacée.
   */
  public record SaisieFeuille(
      @NotBlank(message = "obligatoire") String matiereId,
      @NotBlank(message = "obligatoire") String sequenceId,
      @Size(max = 300, message = "300 caractères au plus") String competence,
      @NotNull(message = "obligatoire") List<@Valid NoteSaisie> notes) {

    public SaisieFeuille {
      competence = Textes.nettoyer(competence);
    }
  }

  public record MatiereAvancement(
      String id, String nom, int coefficient, ClasseNiveau classe, Personne professeur, long effectif, Map<String, Long> saisies) {}

  public record Avancement(List<SequenceVue> sequences, List<MatiereAvancement> matieres) {}

  public record EleveRef(String inscriptionId, String matricule, String nom, String prenoms, Sexe sexe) {}

  public record MatiereManquante(String id, String nom, ClasseNiveau classe, Personne professeur) {}

  public record Manquants(MatiereManquante matiere, long effectif, List<EleveRef> manquants) {}

  public record Manquantes(SequenceVue sequence, List<Manquants> matieres) {}

  public record ClasseReleve(String id, String nom, String annee) {}

  public record MatiereReleve(String id, String nom, int coefficient, String groupe, String professeur, boolean evaluee, Double moyenne) {}

  public record EleveReleve(
      String inscriptionId, String eleveId, String matricule, String nom, String prenoms, Sexe sexe, List<Double> notes, Double moyenne, Rang rang) {}

  /** Relevé de toute une classe pour une période (consultation par la direction). */
  public record ReleveClasse(ClasseReleve classe, Periode periode, List<MatiereReleve> matieres, List<EleveReleve> eleves, ProfilClasse profil) {}
}
