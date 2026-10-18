package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Attestation;
import cm.cetic.mezesse.entity.Sexe;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;

/** Attestations de présence effective au poste, délivrées par le Proviseur. */
public final class AttestationDto {

  private AttestationDto() {}

  public record Vue(
      String id,
      String reference,
      int numero,
      String anneeLibelle,
      String utilisateurId,
      String beneficiaire,
      Sexe sexe,
      String matricule,
      String grade,
      String fonction,
      LocalDate datePriseService,
      String acteNomination,
      String signataire,
      Instant delivreeLe) {

    public static Vue de(Attestation a) {
      return new Vue(
          a.getId(), a.getReference(), a.getNumero(), a.getAnneeLibelle(), a.getUtilisateur().getId(), a.getBeneficiaire(), a.getSexe(),
          a.getMatricule(), a.getGrade(), a.getFonction(), a.getDatePriseService(), a.getActeNomination(), a.getSignataire(), a.getDelivreeLe());
    }
  }

  public record Demande(@NotBlank(message = "obligatoire") String utilisateurId) {}
}
