package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.validation.DateIso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Élèves, inscriptions, renvois. */
public final class EleveDto {

  private EleveDto() {}

  public record Vue(
      String id,
      String matricule,
      String matriculeNational,
      String nom,
      String prenoms,
      Sexe sexe,
      LocalDate dateNaissance,
      String lieuNaissance,
      String photoCle,
      String parentNom,
      String parentTelephone,
      String parentAdresse,
      Instant creeLe,
      Instant majLe) {

    public static Vue de(Eleve e) {
      return new Vue(
          e.getId(), e.getMatricule(), e.getMatriculeNational(), e.getNom(), e.getPrenoms(), e.getSexe(), e.getDateNaissance(),
          e.getLieuNaissance(), e.getPhotoCle(), e.getParentNom(), e.getParentTelephone(), e.getParentAdresse(), e.getCreeLe(), e.getMajLe());
    }
  }

  public record Personne(String nom, String prenom) {

    static Personne de(Utilisateur u) {
      return u == null ? null : new Personne(u.getNom(), u.getPrenom());
    }
  }

  public record RenvoiVue(
      String id,
      String inscriptionId,
      LocalDate date,
      String motif,
      String parId,
      Instant creeLe,
      Instant reintegreLe,
      String reintegreParId,
      String motifReintegration,
      Personne par,
      Personne reintegrePar) {

    public static RenvoiVue de(Renvoi r) {
      return new RenvoiVue(
          r.getId(), r.getInscription().getId(), r.getDate(), r.getMotif(), r.getPar().getId(), r.getCreeLe(), r.getReintegreLe(),
          r.getReintegrePar() == null ? null : r.getReintegrePar().getId(), r.getMotifReintegration(), Personne.de(r.getPar()),
          Personne.de(r.getReintegrePar()));
    }
  }

  public record ClasseRef(String id, String nom, int niveau) {}

  public record AnneeRef(String id, String libelle) {}

  /** Élève inscrit, tel qu'il apparaît dans la liste : son dernier renvoi éventuel compris. */
  public record Ligne(
      String id,
      String eleveId,
      String anneeId,
      String classeId,
      boolean redoublant,
      StatutInscription statut,
      Instant creeLe,
      Vue eleve,
      ClasseRef classe,
      AnneeRef annee,
      List<RenvoiVue> renvois) {}

  public record InscriptionVue(
      String id,
      String eleveId,
      String anneeId,
      String classeId,
      boolean redoublant,
      StatutInscription statut,
      Instant creeLe,
      ClasseRef classe,
      AnneeRef annee,
      List<RenvoiVue> renvois) {}

  /** Fiche de l'élève : identité et parcours (inscriptions, renvois). */
  public record Fiche(
      String id,
      String matricule,
      String matriculeNational,
      String nom,
      String prenoms,
      Sexe sexe,
      LocalDate dateNaissance,
      String lieuNaissance,
      String photoCle,
      String parentNom,
      String parentTelephone,
      String parentAdresse,
      Instant creeLe,
      Instant majLe,
      List<InscriptionVue> inscriptions) {}

  /** Identité d'un élève telle que saisie (inscription, modification, ligne d'un lot ou d'un import). */
  @Getter
  @Setter
  public static class Identite {
    @Size(max = 40, message = "40 caractères au plus")
    private String matriculeNational;

    @NotBlank(message = "obligatoire")
    @Size(max = 80, message = "80 caractères au plus")
    private String nom;

    @NotBlank(message = "obligatoire")
    @Size(max = 120, message = "120 caractères au plus")
    private String prenoms;

    @NotNull(message = "à choisir (M ou F)")
    @Pattern(regexp = "M|F", message = "à choisir (M ou F)")
    private String sexe;

    @NotNull(message = "invalide")
    @DateIso(message = "invalide")
    private String dateNaissance;

    @NotBlank(message = "obligatoire")
    @Size(max = 80, message = "80 caractères au plus")
    private String lieuNaissance;

    @Size(max = 120, message = "120 caractères au plus")
    private String parentNom;

    @Size(max = 30, message = "30 caractères au plus")
    private String parentTelephone;

    @Size(max = 200, message = "200 caractères au plus")
    private String parentAdresse;

    private Boolean redoublant;

    public LocalDate date() {
      return DateIso.Controle.lire(dateNaissance);
    }

    public boolean estRedoublant() {
      return Boolean.TRUE.equals(redoublant);
    }
  }

  /** Inscription d'un élève directement dans une classe de l'année. */
  @Getter
  @Setter
  public static class Creation extends Identite {
    @NotBlank(message = "obligatoire")
    private String classeId;
  }

  /** Modification de la fiche ; classe et redoublement facultatifs (inscription de l'année). */
  @Getter
  @Setter
  public static class Modification extends Identite {
    private String classeId;
  }

  public record ResultatLot(String message, int nombre, List<Vue> eleves) {}

  public record Nombre(String message, int nombre) {}

  public record PhotoCle(String photoCle) {}

  public record ErreurLigne(int ligne, String message) {}

  public record Renvoyer(
      @NotBlank(message = "obligatoire") @Size(max = 500, message = "500 caractères au plus") String motif,
      @NotNull(message = "date invalide") LocalDate date) {}

  public record Reintegrer(@Size(max = 500, message = "500 caractères au plus") String motif) {}

  public record Reinscription(
      @NotEmpty(message = "choisissez au moins un élève") List<String> eleveIds,
      @NotBlank(message = "obligatoire") String classeId,
      @NotNull(message = "obligatoire") Boolean redoublant) {}
}
