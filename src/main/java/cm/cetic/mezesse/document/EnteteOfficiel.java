package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Police;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.util.Textes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Éléments communs des documents : en-tête officiel bilingue, pied de page, formats français. */
public final class EnteteOfficiel {

  private EnteteOfficiel() {}

  /** Fuseau du Cameroun, pour la date d'édition imprimée. */
  public static final ZoneId CAMEROUN = ZoneId.of("Africa/Douala");

  public static String dateFr(LocalDate d) {
    return d == null ? "" : String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
  }

  public static String aujourdhui() {
    return dateFr(LocalDate.now(CAMEROUN));
  }

  /** Ligne d'une colonne de l'en-tête ; espace : écart avec la ligne suivante. */
  private record LigneEntete(String fr, String en, Police police, float taille, float espace) {}

  /**
   * En-tête bilingue des documents officiels (français à gauche, anglais à droite), sceau au centre
   * avec le code de l'établissement et son nom (« C.E.T.I.C. DE MEZESSE / GOVERNMENT TECHNICAL
   * COLLEGE ») ; renvoie le y suivant.
   */
  public static float dessiner(Pdf pdf, Etablissement e, float y) {
    float marge = 24;
    float col = 220;
    float droite = Pdf.LARGEUR - marge - col;
    List<LigneEntete> lignes = new ArrayList<>();
    lignes.add(new LigneEntete("RÉPUBLIQUE DU CAMEROUN", "REPUBLIC OF CAMEROON", Police.GRASSE, 8, 9.5f));
    lignes.add(new LigneEntete("Paix – Travail – Patrie", "Peace – Work – Fatherland", Police.ITALIQUE, 6.5f, 11.5f));
    lignes.add(new LigneEntete("MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES", "MINISTRY OF SECONDARY EDUCATION", Police.GRASSE, 6.8f, 9.5f));
    lignes.add(
        new LigneEntete(
            "DÉLÉGATION RÉGIONALE " + prepositionRegion(e.getRegionFr()), e.getRegionEn().toUpperCase(Locale.ROOT) + " REGIONAL DELEGATION",
            Police.NORMALE, 6.3f, 9.5f));
    lignes.add(
        new LigneEntete(
            "DÉLÉGATION DÉPARTEMENTALE " + prepositionDepartement(e.getDepartementFr()),
            e.getDepartementEn().toUpperCase(Locale.ROOT) + " DIVISIONAL DELEGATION", Police.NORMALE, 6.3f, 9.5f));
    if (rempli(e.getArrondissement())) {
      lignes.add(
          new LigneEntete(
              "ARRONDISSEMENT DE " + e.getArrondissement().toUpperCase(Locale.ROOT),
              Textes.capitales(e.getArrondissement()) + " SUB-DIVISION", Police.NORMALE, 6.3f, 9.5f));
    }
    // Le dernier écart avant le nom laisse respirer le bloc administratif.
    LigneEntete derniere = lignes.remove(lignes.size() - 1);
    lignes.add(new LigneEntete(derniere.fr(), derniere.en(), derniere.police(), derniere.taille(), 11.5f));
    lignes.add(new LigneEntete(nomCourtFr(e), nomCourtEn(e), Police.GRASSE, 7, 9.5f));
    if (rempli(e.getTelephone())) {
      lignes.add(new LigneEntete("TÉL : " + e.getTelephone(), "Phone : " + e.getTelephone(), Police.NORMALE, 6.3f, 9.5f));
    }
    float yy = y;
    for (LigneEntete l : lignes) {
      pdf.ligne(l.fr(), marge, yy, col, Style.de(l.taille()).police(l.police()).centre());
      pdf.ligne(l.en(), droite, yy, col, Style.de(l.taille()).police(l.police()).centre());
      yy += l.espace();
    }
    // Sceau de l'établissement au centre, puis son code et son nom.
    float cx = Pdf.LARGEUR / 2;
    try {
      pdf.image(Pdf.logo(), cx - 27, y - 2, 54, 54);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    float centre = Pdf.LARGEUR - 2 * (marge + col) - 6;
    pdf.ligne(e.getCode(), cx - centre / 2, y + 54, centre, Style.de(5.5f).couleur(Couleurs.GRIS).centre());
    pdf.ligneAjustee(titreFr(e), cx - centre / 2, y + 62, centre, Style.de(8).gras().couleur(Couleurs.ACCENT).centre());
    pdf.ligneAjustee(titreEn(e), cx - centre / 2, y + 71.5f, centre, Style.de(6).gras().centre());
    return Math.max(yy, y + 80) + 2;
  }

  private static boolean rempli(String s) {
    return s != null && !s.isBlank();
  }

  /** « CETIC DE MEZESSE ». */
  static String nomCourtFr(Etablissement e) {
    return Textes.capitales(e.getSigle() + " de " + e.getLocalite());
  }

  /** « GTC MEZESSE » (sigle anglais, à défaut le sigle français). */
  static String nomCourtEn(Etablissement e) {
    return Textes.capitales((rempli(e.getSigleEn()) ? e.getSigleEn() : e.getSigle()) + " " + e.getLocalite());
  }

  /** « C.E.T.I.C. DE MEZESSE ». */
  static String titreFr(Etablissement e) {
    String sigle = Textes.capitales(e.getSigle()).replace(".", "");
    return String.join(".", sigle.split("")) + ". DE " + Textes.capitales(e.getLocalite());
  }

  /** « GOVERNMENT TECHNICAL COLLEGE » : le nom anglais, sans « of Mezesse ». */
  static String titreEn(Etablissement e) {
    String nom = e.getNomEn().strip().replaceFirst("(?i)\\s+(of|de)\\s+" + java.util.regex.Pattern.quote(e.getLocalite().strip()) + "$", "");
    return Textes.capitales(nom);
  }

  static String prepositionRegion(String region) {
    String r = region.strip();
    String bas = r.toLowerCase(Locale.ROOT);
    String haut = r.toUpperCase(Locale.ROOT);
    if (bas.startsWith("du ") || bas.startsWith("de ") || bas.startsWith("de l'")) return haut;
    if (List.of("sud", "nord", "centre", "littoral", "sud-ouest", "nord-ouest", "extrême-nord", "adamaoua").contains(bas)) return "DU " + haut;
    if (bas.matches("^[aeiouéè].*")) return "DE L'" + haut;
    return "DE " + haut;
  }

  static String prepositionDepartement(String departement) {
    String d = departement.strip();
    String bas = d.toLowerCase(Locale.ROOT);
    String haut = d.toUpperCase(Locale.ROOT);
    if (bas.startsWith("du ") || bas.startsWith("de ") || bas.startsWith("de la ")) return haut;
    if (bas.matches("^[aeiouéè].*")) return "DE L'" + haut;
    return "DU " + haut;
  }

  /** Pied de page sur toutes les pages : texte à gauche, « Page n / N » à droite. */
  public static void piedDePage(Pdf pdf, String texte) {
    int total = pdf.nombrePages();
    for (int i = 0; i < total; i++) {
      pdf.reprendrePage(i);
      float y = Pdf.HAUTEUR - 14;
      pdf.ligne(texte, 24, y, 400, Style.de(5.5f).couleur(Couleurs.GRIS));
      pdf.ligne("Page " + (i + 1) + " / " + total, Pdf.LARGEUR - 124, y, 100, Style.de(5.5f).couleur(Couleurs.GRIS).droite());
    }
  }
}
