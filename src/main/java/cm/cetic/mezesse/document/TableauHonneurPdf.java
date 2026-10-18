package cm.cetic.mezesse.document;

import static cm.cetic.mezesse.util.Nombres.fr;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.TableauHonneurDto.Classe;
import cm.cetic.mezesse.dto.TableauHonneurDto.Eleve;
import cm.cetic.mezesse.dto.TableauHonneurDto.Tableau;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.service.calcul.CalculResultats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/** Tableau d'honneur : liste par classe, et certificats à remettre aux élèves (deux par page A4). */
public final class TableauHonneurPdf {

  private TableauHonneurPdf() {}

  private static final float X0 = 24;
  private static final float L = Pdf.LARGEUR - 48;
  private static final String OR = "#a8801f";

  private static String sousTitre(Tableau t) {
    return t.periode().libelle() + " · Année scolaire " + t.annee() + " · Moyenne d'au moins " + fr(t.seuil()) + "/20";
  }

  public static byte[] liste(Etablissement etab, Tableau t) {
    try (Pdf pdf = new Pdf("Tableau d'honneur – " + t.periode().libelle(), "CETIC de Mezesse")) {
      float[] largeurs = {40, 70, 0, 34, 60};
      largeurs[2] = L - largeurs[0] - largeurs[1] - largeurs[3] - largeurs[4];
      String[] titres = {"Rang", "Matricule", "Nom et prénoms", "Sexe", "Moyenne"};
      float h = 15;
      pdf.nouvellePage();
      float y = EnteteOfficiel.dessiner(pdf, etab, 22) + 6;
      pdf.ligne("TABLEAU D'HONNEUR", X0, y, L, Style.de(13).gras().couleur(Couleurs.ACCENT).centre());
      pdf.ligne("HONOUR ROLL", X0, y + 15, L, Style.de(8).italique().couleur(Couleurs.GRIS).centre());
      pdf.ligne(sousTitre(t), X0, y + 26, L, Style.de(8).couleur(Couleurs.GRIS).centre());
      y += 44;
      for (Classe c : t.classes()) {
        if (c.eleves().isEmpty()) continue;
        if (y + 2 * h + 20 > Pdf.HAUTEUR - 30) {
          pdf.nouvellePage();
          y = 30;
        }
        pdf.ligne(c.nom() + "  ·  " + c.eleves().size() + " élève(s) sur " + c.effectif(), X0, y, L, Style.de(9.5f).gras());
        y += 14;
        float x = X0;
        for (int i = 0; i < titres.length; i++) {
          pdf.cadre(x, y, largeurs[i], h, Couleurs.ENTETE);
          pdf.ligne(titres[i], x + 3, y + 4.5f, largeurs[i] - 6, Style.de(7).gras());
          x += largeurs[i];
        }
        y += h;
        for (Eleve e : c.eleves()) {
          if (y + h > Pdf.HAUTEUR - 30) {
            pdf.nouvellePage();
            y = 30;
          }
          String[] valeurs = {CalculResultats.formatRang(e.rang()), e.matricule(), e.nom() + " " + e.prenoms(), e.sexe().name(), fr(e.moyenne())};
          x = X0;
          for (int i = 0; i < valeurs.length; i++) {
            pdf.cadre(x, y, largeurs[i], h);
            Style s = Style.de(7.4f);
            pdf.ligne(valeurs[i], x + 3, y + 4.5f, largeurs[i] - 6, i == 2 || i == 4 ? s.gras() : s);
            x += largeurs[i];
          }
          y += h;
        }
        y += 12;
      }
      EnteteOfficiel.piedDePage(
          pdf, etab.getNomFr() + " · Tableau d'honneur, " + t.periode().libelle() + " · " + t.total() + " élève(s) · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }

  /** Un certificat par élève, au format A5 paysage : deux par page A4, à couper au milieu. */
  /** @param signataire « Le Directeur » ou « La Directrice » */
  public static byte[] certificats(Etablissement etab, Tableau t, String signataire) {
    try (Pdf pdf = new Pdf("Certificats du tableau d'honneur – " + t.periode().libelle(), "CETIC de Mezesse")) {
      int n = 0;
      for (Classe c : t.classes()) {
        for (Eleve e : c.eleves()) {
          if (n % 2 == 0) pdf.nouvellePage();
          certificat(pdf, etab, t, c, e, n % 2 == 0 ? 0 : Pdf.HAUTEUR / 2, signataire);
          n++;
        }
      }
      return pdf.enOctets();
    }
  }

  private static final String VERT = "#007a5e";
  private static final String ROUGE = "#ce1126";
  private static final String JAUNE = "#fcd116";
  private static final String VALEUR = "#1d3f7a";
  private static final String[] ORDINAUX_EN = {"first", "second", "third"};

  /** Période, en français et en anglais : « 1er trimestre » / « first term ». */
  private static String[] periode(Tableau t) {
    var p = t.periode();
    if (p.estSequence()) return new String[] {"Séquence " + p.numero(), "Sequence " + p.numero()};
    if (p.estTrimestre()) return new String[] {(p.numero() == 1 ? "1er" : p.numero() + "e") + " trimestre", ORDINAUX_EN[p.numero() - 1] + " term"};
    return new String[] {"Année entière", "Whole year"};
  }

  /**
   * Certificat du tableau d'honneur (A5 paysage, deux par page A4), sur le modèle en usage dans les
   * lycées : formule du conseil de classe, champs bilingues remplis, signatures du Professeur
   * principal et du chef d'établissement.
   */
  private static void certificat(Pdf pdf, Etablissement etab, Tableau t, Classe c, Eleve e, float y0, String signataire) {
    float h = Pdf.HAUTEUR / 2;
    float m = 14;
    float xc = m;
    float yc = y0 + m;
    float lc = Pdf.LARGEUR - 2 * m;
    float hc = h - 2 * m;
    // Double cadre vert, rubans aux couleurs du drapeau dans deux coins, trait de coupe au milieu de la page.
    pdf.cadre(xc, yc, lc, hc, null, VERT);
    pdf.cadre(xc + 3, yc + 3, lc - 6, hc - 6, null, VERT);
    rubans(pdf, xc + 3, yc + 3, lc - 6, hc - 6);
    if (y0 == 0) pdf.trait(6, h, Pdf.LARGEUR - 6, h, 0.3f, Couleurs.TRAIT_FIN);

    // En-tête bilingue, sceau et nom de l'établissement au centre.
    float col = 165;
    float xg = xc + 26;
    float xd = xc + lc - 26 - col;
    float y = yc + 12;
    String[][] lignes = {
      {"RÉPUBLIQUE DU CAMEROUN", "REPUBLIC OF CAMEROON"},
      {"Paix – Travail – Patrie", "Peace – Work – Fatherland"},
      {"MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES", "MINISTRY OF SECONDARY EDUCATION"},
      {"DÉLÉGATION RÉGIONALE " + EnteteOfficiel.prepositionRegion(etab.getRegionFr()), etab.getRegionEn().toUpperCase(Locale.ROOT) + " REGIONAL DELEGATION"},
      {"DÉLÉGATION DÉPARTEMENTALE " + EnteteOfficiel.prepositionDepartement(etab.getDepartementFr()),
        etab.getDepartementEn().toUpperCase(Locale.ROOT) + " DIVISIONAL DELEGATION"}
    };
    for (int i = 0; i < lignes.length; i++) {
      Style s = i == 1 ? Style.de(5).italique() : i == 0 ? Style.de(6).gras() : Style.de(5.2f);
      pdf.ligneAjustee(lignes[i][0], xg, y + i * 7.5f, col, s.centre());
      pdf.ligneAjustee(lignes[i][1], xd, y + i * 7.5f, col, s.centre());
    }
    float cx = Pdf.LARGEUR / 2;
    try {
      pdf.image(Pdf.logo(), cx - 19, yc + 8, 38, 38);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    float lt = 230;
    pdf.ligneAjustee(EnteteOfficiel.titreFr(etab), cx - lt / 2, yc + 49, lt, Style.de(11).gras().couleur(VERT).centre());
    pdf.ligneAjustee(EnteteOfficiel.titreEn(etab), cx - lt / 2, yc + 61, lt, Style.de(7).gras().centre());
    String contact = String.join(
        " · ",
        java.util.stream.Stream.of(
                etab.getBoitePostale() == null ? null : "B.P. " + etab.getBoitePostale(),
                etab.getTelephone() == null ? null : "Tél./Phone : " + etab.getTelephone())
            .filter(java.util.Objects::nonNull)
            .toList());
    if (!contact.isEmpty()) pdf.ligne(contact, cx - lt / 2, yc + 70, lt, Style.de(5.5f).couleur(Couleurs.GRIS).centre());

    // Titre et formule du conseil de classe.
    float x = xc + 30;
    float l = lc - 60;
    y = yc + 82;
    pdf.ligne("TABLEAU D'HONNEUR", x, y, l, Style.de(26).gras().couleur(VERT).centre());
    pdf.ligne("HONOUR ROLL", x, y + 27, l, Style.de(10).gras().couleur(ROUGE).centre());
    y += 44;
    pdf.ligneAjustee(
        "Le Conseil de classe, en vertu des pouvoirs qui lui sont conférés, décerne le TABLEAU D'HONNEUR", x, y, l, Style.de(8.5f).gras().centre());
    pdf.ligneAjustee("By virtue of the power conferred on it, the class council awards an HONOUR ROLL", x, y + 10, l, Style.de(7).italique().centre());

    // Champs remplis : libellé français en gras, anglais en italique dessous, valeur sur une ligne.
    y += 28;
    float ligne = 25;
    champ(pdf, "À l'élève", "To the student", e.nom().toUpperCase(Locale.ROOT) + " " + e.prenoms(), x, y, l, 12);
    y += ligne;
    float tiers = l / 3;
    champ(pdf, "Né(e) le", "Born on", EnteteOfficiel.dateFr(e.dateNaissance()), x, y, tiers - 8, 10);
    champ(pdf, "à", "at", e.lieuNaissance(), x + tiers, y, tiers - 8, 10);
    champ(pdf, "Classe de", "Class", c.nom(), x + 2 * tiers, y, tiers, 10);
    y += ligne;
    String[] p = periode(t);
    champ(pdf, "Pour le", "For the", p[0] + " / " + p[1], x, y, l / 2 - 8, 10);
    champ(pdf, "Année scolaire", "School year", t.annee(), x + l / 2, y, l / 2, 10);
    y += ligne;
    champ(pdf, "Moyenne obtenue", "Average obtained", fr(e.moyenne()) + " / 20", x, y, tiers - 8, 10);
    champ(pdf, "Rang", "Rank", CalculResultats.formatRang(e.rang()) + " / " + c.effectif(), x + tiers, y, tiers - 8, 10);
    champ(pdf, "Moyenne de la classe", "Class average", c.moyenne() == null ? "" : fr(c.moyenne()) + " / 20", x + 2 * tiers, y, tiers, 10);
    y += ligne;
    champ(pdf, "Conseil de classe du", "Class council of", "", x, y, l / 2 - 8, 10);
    champ(pdf, etab.getLocalite() + ", le", "on", "", x + l / 2, y, l / 2, 10);

    // Signatures : Professeur principal à gauche, Directeur à droite ; la place du cachet en dessous.
    y += 34;
    float ls = 190;
    pdf.ligne("Le Professeur principal", x, y, ls, Style.de(8.5f).gras().centre());
    pdf.ligne("The Class Master", x, y + 10, ls, Style.de(6.5f).italique().centre());
    pdf.ligne(signataire, x + l - ls, y, ls, Style.de(8.5f).gras().centre());
    pdf.ligne("The Principal", x + l - ls, y + 10, ls, Style.de(6.5f).italique().centre());
    if (c.profPrincipal() != null) pdf.ligne(c.profPrincipal(), x, yc + hc - 18, ls, Style.de(8).centre());
  }

  /** « Libellé / Label : valeur », la valeur posée sur un trait fin. */
  private static void champ(Pdf pdf, String fr, String en, String valeur, float x, float y, float largeur, float taille) {
    float lFr = Pdf.largeur(fr + " :", Pdf.Police.GRASSE, 8);
    float lEn = Pdf.largeur(en, Pdf.Police.ITALIQUE, 6.2f);
    pdf.ligne(fr + " :", x, y, lFr + 2, Style.de(8).gras());
    pdf.ligne(en, x, y + 9, lEn + 2, Style.de(6.2f).italique().couleur(Couleurs.GRIS));
    float xv = x + Math.max(lFr, lEn) + 5;
    float lv = x + largeur - xv;
    pdf.trait(xv, y + 11, x + largeur, y + 11, 0.4f, Couleurs.TRAIT_FIN);
    if (!valeur.isEmpty()) pdf.ligneAjustee(valeur, xv + 2, y + 10 - taille, lv - 4, Style.de(taille).gras().couleur(VALEUR));
  }

  /** Rubans vert, rouge, jaune en travers du coin haut gauche et du coin bas droit. */
  private static void rubans(Pdf pdf, float x, float y, float l, float h) {
    String[] couleurs = {VERT, ROUGE, JAUNE};
    float w = 7;
    pdf.decouper(x, y, l, h);
    for (int k = 0; k < 3; k++) {
      float a = 14 + k * w;
      pdf.polygone(new float[] {x, x + a, x + a + w, x}, new float[] {y + a, y, y, y + a + w}, couleurs[k]);
      float xr = x + l;
      float yb = y + h;
      pdf.polygone(new float[] {xr, xr - a, xr - a - w, xr}, new float[] {yb - a, yb, yb, yb - a - w}, couleurs[2 - k]);
    }
    pdf.finDecoupe();
  }
}
