package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Police;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.entity.Attestation;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Sexe;
import java.util.ArrayList;
import java.util.List;

/** Attestation de présence effective au poste (A4), sur le modèle administratif en usage. */
public final class AttestationPdf {

  private AttestationPdf() {}

  private static final float X0 = 62;
  private static final float L = Pdf.LARGEUR - 2 * X0;
  private static final float TAILLE = 11;
  private static final float INTERLIGNE = 17;

  /**
   * @param sexeSignataire sexe du Proviseur (« Je soussigné » ou « soussignée »), null si inconnu
   */
  public static byte[] generer(Etablissement etab, Attestation a, Sexe sexeSignataire) {
    try (Pdf pdf = new Pdf("Attestation de présence effective au poste – " + a.getBeneficiaire(), "CETIC de Mezesse")) {
      pdf.nouvellePage();
      float y = EnteteOfficiel.dessiner(pdf, etab, 26) + 14;
      pdf.ligne(a.getReference(), X0, y, L, Style.de(9.5f).gras());
      y += 34;

      String titre = "ATTESTATION DE PRÉSENCE EFFECTIVE AU POSTE";
      pdf.ligne(titre, X0, y, L, Style.de(14).gras().couleur(Couleurs.ACCENT).centre());
      float lt = Pdf.largeur(titre, Police.GRASSE, 14);
      pdf.trait(X0 + (L - lt) / 2, y + 16.5f, X0 + (L + lt) / 2, y + 16.5f, 0.8f, Couleurs.ACCENT);
      pdf.ligne("ATTESTATION OF EFFECTIVE PRESENCE AT POST", X0, y + 21, L, Style.de(10).italique().couleur(Couleurs.GRIS).centre());
      y += 58;

      String soussigne = sexeSignataire == Sexe.F ? "soussignée" : sexeSignataire == Sexe.M ? "soussigné" : "soussigné(e)";
      y = paragraphe(
          pdf,
          "Je " + soussigne + ", " + a.getSignataire() + ", Proviseur du " + etab.getNomFr() + ", atteste par la présente que :",
          y);
      y += 12;

      String civilite = a.getSexe() == Sexe.F ? "Mme " : a.getSexe() == Sexe.M ? "M. " : "";
      y = champ(pdf, "Nom et prénoms", civilite + a.getBeneficiaire(), X0 + 18, y);
      y = champ(pdf, "Matricule", a.getMatricule(), X0 + 18, y);
      y = champ(pdf, "Grade / Corps", a.getGrade(), X0 + 18, y);
      y = champ(pdf, "Fonction / Spécialité", a.getFonction(), X0 + 18, y);
      if (a.getActeNomination() != null) y = champ(pdf, "Acte de nomination / affectation", a.getActeNomination(), X0 + 18, y);
      y += 12;

      String present = a.getSexe() == Sexe.F ? "présente" : a.getSexe() == Sexe.M ? "présent" : "présent(e)";
      y = paragraphe(
          pdf,
          "est effectivement " + present + " à son poste de travail et exerce régulièrement ses fonctions au sein de notre établissement depuis le "
              + EnteteOfficiel.dateFr(a.getDatePriseService()) + ".",
          y);
      y += 12;
      y = paragraphe(pdf, "En foi de quoi, la présente attestation lui est délivrée pour servir et valoir ce que de droit.", y);

      y += 40;
      String fait = "Fait à " + etab.getLocalite() + ", le " + EnteteOfficiel.dateFr(a.getDelivreeLe().atZone(EnteteOfficiel.CAMEROUN).toLocalDate());
      float xs = X0 + L - 220;
      pdf.ligne(fait, xs, y, 220, Style.de(TAILLE).centre());
      pdf.ligne("Le Proviseur", xs, y + 24, 220, Style.de(TAILLE).gras().centre());
      pdf.ligne(a.getSignataire(), xs, y + 104, 220, Style.de(TAILLE).gras().centre());
      return pdf.enOctets();
    }
  }

  /** « Libellé : valeur » sur une ligne, libellé en gras. */
  private static float champ(Pdf pdf, String libelle, String valeur, float x, float y) {
    float fin = pdf.suite(libelle + " : ", x, y, Style.de(TAILLE).gras());
    pdf.ligne(valeur, fin, y, X0 + L - fin, Style.de(TAILLE));
    return y + INTERLIGNE + 4;
  }

  /** Paragraphe justifié à gauche, coupé aux mots ; renvoie le y suivant. */
  private static float paragraphe(Pdf pdf, String texte, float y) {
    List<String> lignes = new ArrayList<>();
    StringBuilder courante = new StringBuilder();
    for (String mot : texte.split(" ")) {
      String essai = courante.isEmpty() ? mot : courante + " " + mot;
      if (Pdf.largeur(essai, Police.NORMALE, TAILLE) <= L || courante.isEmpty()) {
        courante.setLength(0);
        courante.append(essai);
      } else {
        lignes.add(courante.toString());
        courante.setLength(0);
        courante.append(mot);
      }
    }
    lignes.add(courante.toString());
    for (String l : lignes) {
      pdf.ligne(l, X0, y, L, Style.de(TAILLE));
      y += INTERLIGNE;
    }
    return y;
  }
}
