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

  private record LigneEntete(String texte, Police police, float taille) {}

  /** En-tête bilingue (français à gauche, anglais à droite), sceau au centre ; renvoie le y suivant. */
  public static float dessiner(Pdf pdf, Etablissement e, float y) {
    float marge = 24;
    float col = 220;
    float droite = Pdf.LARGEUR - marge - col;
    List<LigneEntete> fr =
        List.of(
            new LigneEntete("RÉPUBLIQUE DU CAMEROUN", Police.GRASSE, 8),
            new LigneEntete("Paix – Travail – Patrie", Police.ITALIQUE, 6.5f),
            new LigneEntete("MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES", Police.GRASSE, 6.8f),
            new LigneEntete("DÉLÉGATION RÉGIONALE " + prepositionRegion(e.getRegionFr()), Police.NORMALE, 6.3f),
            new LigneEntete("DÉLÉGATION DÉPARTEMENTALE " + prepositionDepartement(e.getDepartementFr()), Police.NORMALE, 6.3f),
            new LigneEntete(nomCourt(e.getNomFr(), e.getSigle() + " de " + e.getLocalite(), col), Police.GRASSE, 7));
    List<LigneEntete> en =
        List.of(
            new LigneEntete("REPUBLIC OF CAMEROON", Police.GRASSE, 8),
            new LigneEntete("Peace – Work – Fatherland", Police.ITALIQUE, 6.5f),
            new LigneEntete("MINISTRY OF SECONDARY EDUCATION", Police.GRASSE, 6.8f),
            new LigneEntete(e.getRegionEn().toUpperCase(Locale.ROOT) + " REGIONAL DELEGATION", Police.NORMALE, 6.3f),
            new LigneEntete(e.getDepartementEn().toUpperCase(Locale.ROOT) + " DIVISIONAL DELEGATION", Police.NORMALE, 6.3f),
            new LigneEntete(nomCourt(e.getNomEn(), e.getSigle() + " " + e.getLocalite(), col), Police.GRASSE, 7));
    float yy = y;
    for (int i = 0; i < fr.size(); i++) {
      pdf.ligne(fr.get(i).texte(), marge, yy, col, Style.de(fr.get(i).taille()).police(fr.get(i).police()).centre());
      pdf.ligne(en.get(i).texte(), droite, yy, col, Style.de(en.get(i).taille()).police(en.get(i).police()).centre());
      yy += i == 1 || i == 4 ? 11.5f : 9.5f;
    }
    // Sceau de l'établissement au centre, code en dessous.
    float cx = Pdf.LARGEUR / 2;
    try {
      pdf.image(Pdf.logo(), cx - 27, y - 2, 54, 54);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    pdf.ligne(e.getCode(), cx - 50, y + 54, 100, Style.de(5.5f).couleur(Couleurs.GRIS).centre());
    return yy + 2;
  }

  /** Nom complet en capitales s'il tient sur la ligne, sinon la forme courte (sigle et localité). */
  static String nomCourt(String complet, String court, float largeur) {
    return Pdf.largeur(Textes.capitales(complet), Police.GRASSE, 7) <= largeur ? Textes.capitales(complet) : Textes.capitales(court);
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
