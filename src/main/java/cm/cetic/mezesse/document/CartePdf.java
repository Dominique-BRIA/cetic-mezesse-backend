package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.util.Textes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cartes d'identité scolaires (RG-31) : format carte bancaire ISO/CEI 7810 ID-1 (85,6 × 54 mm), dix
 * par page A4 (2 × 5) avec des traits de coupe, à découper à la main.
 */
public final class CartePdf {

  private CartePdf() {}

  private static final float MM = 72 / 25.4f;
  static final float LARGEUR = 85.6f * MM;
  static final float HAUTEUR = 54 * MM;
  private static final int COLONNES = 2;
  private static final int LIGNES = 5;
  /** Entre deux cartes : de quoi couper sans entamer la voisine. */
  private static final float ECART = 3 * MM;
  private static final float ORIGINE_X = (Pdf.LARGEUR - COLONNES * LARGEUR - (COLONNES - 1) * ECART) / 2;
  private static final float ORIGINE_Y = (Pdf.HAUTEUR - LIGNES * HAUTEUR - (LIGNES - 1) * ECART) / 2;

  private static final String VERT = "#007a5e";
  private static final String ROUGE = "#ce1126";
  private static final String JAUNE = "#fcd116";

  public record EleveCarte(Eleve eleve, String classe, byte[] photo) {}

  public static byte[] generer(Etablissement etab, String annee, List<EleveCarte> eleves) {
    try (Pdf pdf = new Pdf("Cartes scolaires " + annee, etab.getNomFr())) {
      int parPage = COLONNES * LIGNES;
      for (int i = 0; i < eleves.size(); i++) {
        int k = i % parPage;
        if (k == 0) {
          pdf.nouvellePage();
          tracerCoupes(pdf);
        }
        float x = ORIGINE_X + (k % COLONNES) * (LARGEUR + ECART);
        float y = ORIGINE_Y + (float) Math.floor(k / (double) COLONNES) * (HAUTEUR + ECART);
        dessinerCarte(pdf, etab, annee, eleves.get(i), x, y);
      }
      return pdf.enOctets();
    }
  }

  /** Traits de coupe dans les marges de la page, dans le prolongement des bords de chaque carte. */
  private static void tracerCoupes(Pdf pdf) {
    float longueur = 4 * MM;
    float recul = 1.5f * MM;
    List<Float> xs = new ArrayList<>();
    for (int c = 0; c < COLONNES; c++) {
      xs.add(ORIGINE_X + c * (LARGEUR + ECART));
      xs.add(ORIGINE_X + c * (LARGEUR + ECART) + LARGEUR);
    }
    List<Float> ys = new ArrayList<>();
    for (int l = 0; l < LIGNES; l++) {
      ys.add(ORIGINE_Y + l * (HAUTEUR + ECART));
      ys.add(ORIGINE_Y + l * (HAUTEUR + ECART) + HAUTEUR);
    }
    float haut = ORIGINE_Y;
    float bas = Pdf.HAUTEUR - ORIGINE_Y;
    float gauche = ORIGINE_X;
    float droite = Pdf.LARGEUR - ORIGINE_X;
    for (float x : xs) {
      pdf.trait(x, haut - recul - longueur, x, haut - recul, 0.3f, Couleurs.TRAIT);
      pdf.trait(x, bas + recul, x, bas + recul + longueur, 0.3f, Couleurs.TRAIT);
    }
    for (float y : ys) {
      pdf.trait(gauche - recul - longueur, y, gauche - recul, y, 0.3f, Couleurs.TRAIT);
      pdf.trait(droite + recul, y, droite + recul + longueur, y, 0.3f, Couleurs.TRAIT);
    }
  }

  private static void dessinerCarte(Pdf pdf, Etablissement etab, String annee, EleveCarte carte, float x, float y) {
    float l = LARGEUR;
    float h = HAUTEUR;
    float m = 2.2f * MM;
    float bande = 1.6f;
    Eleve e = carte.eleve();
    try {
      // Fond, filet aux couleurs du drapeau et sceau en filigrane, dans les coins arrondis.
      pdf.decouperArrondi(x, y, l, h, 2.5f * MM);
      pdf.remplir(x, y, l, h, "#ffffff");
      pdf.remplir(x, y, l / 3, bande, VERT);
      pdf.remplir(x + l / 3, y, l / 3, bande, ROUGE);
      pdf.remplir(x + 2 * l / 3, y, l / 3, bande, JAUNE);
      pdf.opacite(0.06f);
      pdf.image(Pdf.logo(), x + l / 2 - 26, y + 52, 82, 82);
      pdf.opacite(1);
      pdf.finDecoupe();
      pdf.contourArrondi(x, y, l, h, 2.5f * MM, 0.4f, Couleurs.TRAIT_FIN);

      // En-tête bilingue autour du sceau.
      float col = (l - 2 * m - 30) / 2;
      float yy = y + bande + 2.6f;
      String[][] fr = {{"RÉPUBLIQUE DU CAMEROUN", "G", "5.2"}, {"Paix – Travail – Patrie", "I", "4.3"}, {"MINESEC", "G", "4.6"}};
      String[][] en = {{"REPUBLIC OF CAMEROON", "G", "5.2"}, {"Peace – Work – Fatherland", "I", "4.3"}, {"MINESEC", "G", "4.6"}};
      for (int i = 0; i < fr.length; i++) {
        float taille = Float.parseFloat(fr[i][2]);
        Style s = fr[i][1].equals("G") ? Style.de(taille).gras().centre() : Style.de(taille).italique().centre();
        pdf.ligne(fr[i][0], x + m, yy, col, s);
        pdf.ligne(en[i][0], x + l - m - col, yy, col, s);
        yy += taille + 1.4f;
      }
      pdf.image(Pdf.logo(), x + l / 2 - 12.5f, y + bande + 1.6f, 25, 25);

      // Nom de l'établissement sur un bandeau.
      float yBandeau = y + bande + 28;
      pdf.remplir(x, yBandeau, l, 10.5f, Couleurs.ACCENT);
      String nom =
          Pdf.largeur(Textes.capitales(etab.getNomFr()), Pdf.Police.GRASSE, 5.6f) <= l - 2 * m
              ? Textes.capitales(etab.getNomFr())
              : Textes.capitales(etab.getSigle() + " de " + etab.getLocalite());
      pdf.ligne(nom, x + m, yBandeau + 2.6f, l - 2 * m, Style.de(5.6f).gras().couleur("#ffffff").centre());

      // Titre et année scolaire.
      float yTitre = yBandeau + 13.5f;
      pdf.ligne("CARTE D'IDENTITÉ SCOLAIRE", x + m, yTitre, l - 2 * m - 60, Style.de(7).gras().couleur(Couleurs.ACCENT));
      pdf.ligne("STUDENT IDENTITY CARD", x + m, yTitre + 8, l - 2 * m - 60, Style.de(4.6f).italique().couleur(Couleurs.GRIS));
      pdf.ligne(annee, x + l - m - 60, yTitre + 0.5f, 60, Style.de(6.6f).gras().droite());
      pdf.ligne("Année scolaire / School year", x + l - m - 60, yTitre + 8, 60, Style.de(4).couleur(Couleurs.GRIS).droite());

      // Photo d'identité (format 3,5 × 4,5 réduit à la carte).
      float yCorps = yTitre + 16;
      float hPhoto = y + h - yCorps - 4.5f * MM;
      float lPhoto = hPhoto * (3.5f / 4.5f);
      pdf.cadre(x + m, yCorps, lPhoto, hPhoto, null, Couleurs.TRAIT);
      try {
        pdf.decouper(x + m + 0.5f, yCorps + 0.5f, lPhoto - 1, hPhoto - 1);
        pdf.imageCouvrante(carte.photo(), x + m + 0.5f, yCorps + 0.5f, lPhoto - 1, hPhoto - 1);
        pdf.finDecoupe();
      } catch (IOException | RuntimeException ex) {
        pdf.finDecoupe();
        pdf.ligne("Photo illisible", x + m, yCorps + hPhoto / 2 - 3, lPhoto, Style.de(5).couleur(Couleurs.GRIS).centre());
      }

      // Identité de l'élève.
      float xc = x + m + lPhoto + 2.4f * MM;
      float lc = x + l - m - xc;
      float[] yc = {yCorps - 0.5f};
      champ(pdf, "Nom / Surname", e.getNom().toUpperCase(Locale.ROOT), xc, yc[0], lc, 7, true);
      yc[0] += 12.2f;
      champ(pdf, "Prénoms / Given names", e.getPrenoms(), xc, yc[0], lc, 6.4f, true);
      yc[0] += 11.6f;
      champ(pdf, "Né(e) le / Born on", EnteteOfficiel.dateFr(e.getDateNaissance()) + " à " + e.getLieuNaissance(), xc, yc[0], lc, 5.8f, false);
      yc[0] += 11;
      float demi = (lc - 3) / 2;
      champ(pdf, "Sexe / Sex", e.getSexe() == Sexe.F ? "F" : "M", xc, yc[0], 20, 5.8f, true);
      champ(pdf, "Classe / Class", carte.classe(), xc + 22, yc[0], lc - 22, 5.8f, true);
      yc[0] += 11;
      champ(pdf, "Matricule", e.getMatricule(), xc, yc[0], demi, 6, true);
      if (e.getMatriculeNational() != null) champ(pdf, "Mat. national", e.getMatriculeNational(), xc + demi + 3, yc[0], demi, 5.4f, false);

      // Pied : contact du parent et visa du Proviseur.
      float yPied = y + h - 4.3f * MM;
      pdf.trait(x + m, yPied, x + l - m, yPied, 0.3f, Couleurs.TRAIT_FIN);
      pdf.ligne(
          e.getParentTelephone() != null ? "En cas d'urgence / Emergency : " + e.getParentTelephone() : "", x + m, yPied + 2.4f, l / 2,
          Style.de(4.4f).couleur(Couleurs.GRIS));
      pdf.ligne("Le Proviseur / The Principal", x + l / 2, yPied + 2.4f, l / 2 - m, Style.de(4.6f).gras().droite());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static void champ(Pdf pdf, String libelle, String valeur, float x, float y, float largeur, float taille, boolean gras) {
    pdf.ligne(libelle, x, y, largeur, Style.de(3.9f).couleur(Couleurs.GRIS));
    Style s = Style.de(taille);
    pdf.ligne(valeur, x, y + 4.4f, largeur, gras ? s.gras() : s);
  }
}
