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
  public static byte[] certificats(Etablissement etab, Tableau t) {
    try (Pdf pdf = new Pdf("Certificats du tableau d'honneur – " + t.periode().libelle(), "CETIC de Mezesse")) {
      int n = 0;
      for (Classe c : t.classes()) {
        for (Eleve e : c.eleves()) {
          if (n % 2 == 0) pdf.nouvellePage();
          certificat(pdf, etab, t, c, e, n % 2 == 0 ? 0 : Pdf.HAUTEUR / 2);
          n++;
        }
      }
      return pdf.enOctets();
    }
  }

  /** « au trimestre 1 de l'année scolaire 2026-2027 ». */
  private static String periode(Tableau t) {
    var p = t.periode();
    String annee = "l'année scolaire " + t.annee();
    if (p.estSequence()) return "à la séquence " + p.numero() + " de " + annee;
    if (p.estTrimestre()) return "au trimestre " + p.numero() + " de " + annee;
    return "sur " + annee;
  }

  private static void certificat(Pdf pdf, Etablissement etab, Tableau t, Classe c, Eleve e, float y0) {
    float h = Pdf.HAUTEUR / 2;
    float m = 18;
    // Double cadre doré, et trait de coupe entre les deux certificats de la page.
    pdf.cadre(m, y0 + m, Pdf.LARGEUR - 2 * m, h - 2 * m, null, OR);
    pdf.cadre(m + 4, y0 + m + 4, Pdf.LARGEUR - 2 * m - 8, h - 2 * m - 8, null, OR);
    if (y0 == 0) pdf.trait(8, h, Pdf.LARGEUR - 8, h, 0.3f, Couleurs.TRAIT_FIN);

    float x = m + 20;
    float l = Pdf.LARGEUR - 2 * x;
    float y = y0 + m + 16;
    pdf.ligne("RÉPUBLIQUE DU CAMEROUN · REPUBLIC OF CAMEROON", x, y, l, Style.de(7).gras().centre());
    pdf.ligne("Paix – Travail – Patrie · Peace – Work – Fatherland", x, y + 9, l, Style.de(6).italique().couleur(Couleurs.GRIS).centre());
    try {
      pdf.image(Pdf.logo(), Pdf.LARGEUR / 2 - 22, y + 20, 44, 44);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    pdf.ligne(EnteteOfficiel.titreFr(etab) + " · " + EnteteOfficiel.titreEn(etab), x, y + 68, l, Style.de(7.5f).gras().couleur(Couleurs.ACCENT).centre());

    y += 92;
    pdf.ligne("TABLEAU D'HONNEUR", x, y, l, Style.de(26).gras().couleur(OR).centre());
    pdf.ligne("HONOUR ROLL", x, y + 29, l, Style.de(10).italique().couleur(Couleurs.GRIS).centre());
    y += 52;
    pdf.ligne("décerné à", x, y, l, Style.de(10).couleur(Couleurs.GRIS).centre());
    pdf.ligneAjustee(e.nom().toUpperCase(Locale.ROOT) + " " + e.prenoms(), x, y + 14, l, Style.de(18).gras().centre());
    y += 42;
    pdf.ligne("de la classe de " + c.nom() + ", pour sa moyenne de " + fr(e.moyenne()) + "/20 (" + CalculResultats.formatRang(e.rang()) + " sur " + c.effectif() + ")",
        x, y, l, Style.de(10.5f).centre());
    pdf.ligne(periode(t) + ".", x, y + 15, l, Style.de(10.5f).centre());

    // Assez de place sous « Le Proviseur » pour la signature et le cachet.
    float yb = y0 + h - m - 96;
    pdf.ligne("Fait à " + etab.getLocalite() + ", le " + EnteteOfficiel.aujourdhui(), x, yb, l / 2, Style.de(9));
    pdf.ligne("Le Proviseur", x + l / 2, yb, l / 2, Style.de(9.5f).gras().droite());
  }
}
