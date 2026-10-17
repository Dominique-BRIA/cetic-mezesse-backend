package cm.cetic.mezesse.document;

import static cm.cetic.mezesse.util.Nombres.fr;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.service.ResultatsService.DonneesClasse;
import cm.cetic.mezesse.service.calcul.CalculResultats;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Groupe;
import cm.cetic.mezesse.service.calcul.Resultats.LigneGroupe;
import cm.cetic.mezesse.service.calcul.Resultats.LigneMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.MatiereEntree;
import cm.cetic.mezesse.service.calcul.Resultats.ProfilClasse;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatsClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Seuil;
import cm.cetic.mezesse.service.calcul.Resultats.StatMatiere;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Bulletin de notes bilingue, une page A4 par élève, sur le modèle des bulletins APC du MINESEC. */
public final class BulletinPdf {

  private BulletinPdf() {}

  private static final float X0 = 24;
  private static final float L = Pdf.LARGEUR - 48;
  private static final String[] ORDINAUX_EN = {"First", "Second", "Third", "Fourth", "Fifth", "Sixth"};

  /** Résultats d'une sous-période rappelée sur le bulletin (séquences d'un trimestre, trimestres de l'année). */
  public record Rappel(Periode periode, ResultatsClasse resultats) {}

  /**
   * Tout ce qu'il faut pour dessiner les bulletins d'une classe.
   *
   * @param competences compétence visée par matière (bulletin de séquence)
   * @param photos photo par élève (id de l'élève)
   */
  public record Contexte(Etablissement etab, DonneesClasse donnees, List<Rappel> rappels, Map<String, String> competences, Map<String, byte[]> photos) {}

  /** « Compétences très bien acquises » → « Très bien acquises » : le titre du bloc dit déjà de quoi il s'agit. */
  static String sansPrefixe(String s) {
    String r = (s == null ? "" : s).replaceFirst("(?i)^comp[ée]tences?\\s+", "");
    return r.isEmpty() ? r : r.substring(0, 1).toUpperCase(Locale.ROOT) + r.substring(1);
  }

  private static String[] titres(Periode p) {
    if (p == Periode.A) return new String[] {"BULLETIN ANNUEL", "ANNUAL REPORT CARD"};
    int n = p.numero();
    if (p.estSequence()) return new String[] {"BULLETIN DE LA SÉQUENCE " + n, ORDINAUX_EN[n - 1].toUpperCase(Locale.ROOT) + " SEQUENCE REPORT"};
    return new String[] {"BULLETIN DU TRIMESTRE " + n, ORDINAUX_EN[n - 1].toUpperCase(Locale.ROOT) + " TERM REPORT"};
  }

  private static String[] libelleCourt(Periode p) {
    if (p.estSequence()) return new String[] {"Séq. " + p.numero(), "Seq. " + p.numero()};
    return new String[] {"Trim. " + p.numero(), "Term " + p.numero()};
  }

  private record Colonne(String cle, String fr, String en, float largeur, boolean gauche) {
    Colonne(String cle, String fr, String en, float largeur) {
      this(cle, fr, en, largeur, false);
    }

    Colonne largeur(float l) {
      return new Colonne(cle, fr, en, l, gauche);
    }
  }

  private static List<Colonne> colonnes(Periode p, List<Periode> rappels) {
    List<Colonne> fin =
        List.of(
            new Colonne("coef", "Coef", "Coef", 26), new Colonne("total", "N x C", "Total", 36), new Colonne("rang", "Rang", "Rank", 34),
            new Colonne("moycl", "Moy. cl.", "Cl. avg", 34), new Colonne("reussite", "% réuss.", "% pass", 38),
            new Colonne("grade", "Grade", "Grade", 28), new Colonne("extrema", "Min – Max", "Extrema", 54));
    List<Colonne> cols = new ArrayList<>();
    if (p.estSequence()) {
      cols.add(new Colonne("matiere", "Matières", "Subjects", 112, true));
      cols.add(new Colonne("competence", "Compétence visée", "Target skills", 110, true));
      cols.add(new Colonne("note", "Note/20", "Mark/20", 34));
      cols.addAll(fin);
      float utilise = (float) cols.stream().mapToDouble(Colonne::largeur).sum();
      cols.add(new Colonne("visa", "Visa prof.", "Signature", L - utilise));
      return cols;
    }
    cols.add(new Colonne("matiere", "Matières", "Subjects", 0, true));
    for (Periode r : rappels) {
      String[] lib = libelleCourt(r);
      cols.add(new Colonne("d-" + r.name(), lib[0], lib[1], p == Periode.A ? 30 : 34));
    }
    cols.add(new Colonne("note", "Moy./20", "Avg/20", 36));
    cols.addAll(fin);
    cols.add(new Colonne("visa", "Visa prof.", "Signature", 44));
    float utilise = (float) cols.stream().mapToDouble(Colonne::largeur).sum();
    cols.set(0, cols.get(0).largeur(L - utilise));
    return cols;
  }

  /** Dessine le bulletin d'un élève sur la page courante. */
  public static void dessiner(Pdf pdf, Contexte ctx, String inscriptionId) {
    Etablissement etab = ctx.etab();
    DonneesClasse donnees = ctx.donnees();
    Classe classe = donnees.classe();
    ResultatsClasse resultats = donnees.resultats();
    Inscription ins = donnees.inscription(inscriptionId);
    ResultatEleve r = resultats.eleve(inscriptionId);
    Periode p = resultats.periode();
    int effectif = resultats.profil().effectif();
    long classesAvecMoyenne = resultats.eleves().stream().filter(e -> e.moyenne() != null).count();
    Utilisateur pp = classe.getProfPrincipal();

    // ---- En-tête ----
    float y = EnteteOfficiel.dessiner(pdf, etab, 22);

    // ---- Titre ----
    String[] titre = titres(p);
    pdf.cadre(X0 + 150, y, L - 300, 25, Couleurs.ENTETE, Couleurs.ACCENT);
    pdf.ligne(titre[0], X0 + 150, y + 4, L - 300, Style.de(9.5f).gras().couleur(Couleurs.ACCENT).centre());
    pdf.ligne(titre[1] + "  ·  Année scolaire / School year : " + classe.getAnnee().getLibelle(), X0 + 150, y + 15, L - 300, Style.de(6.3f).italique().centre());
    y += 30;

    // ---- Identité ----
    float hId = 60;
    pdf.cadre(X0, y, L, hId);
    byte[] photo = ctx.photos().get(ins.getEleveId());
    pdf.cadre(X0 + 3, y + 3, 46, hId - 6, null, Couleurs.TRAIT_FIN);
    boolean photoDessinee = false;
    if (photo != null) {
      try {
        pdf.imageAjustee(photo, X0 + 3.5f, y + 3.5f, 45, hId - 7);
        photoDessinee = true;
      } catch (Exception ignoree) {
        // photo illisible : l'emplacement reste marqué « Photo »
      }
    }
    if (!photoDessinee) pdf.ligne("Photo", X0 + 3, y + hId / 2 - 3, 46, Style.de(6).couleur(Couleurs.GRIS).centre());
    Eleve e = ins.getEleve();
    float xId = X0 + 56;
    float lId = L - 60;
    pdf.ligne(e.nomComplet(), xId, y + 5, lId * 0.62f, Style.de(10.5f).gras());
    float c1 = xId;
    float c2 = xId + lId * 0.62f;
    String parent = String.join(" · ", java.util.stream.Stream.of(e.getParentNom(), e.getParentTelephone()).filter(Objects::nonNull).toList());
    champ(pdf, "Né(e) le", "Born on", EnteteOfficiel.dateFr(e.getDateNaissance()) + " à " + e.getLieuNaissance(), c1, y + 20, lId * 0.6f);
    champ(pdf, "Sexe", "Gender", e.getSexe() == Sexe.F ? "F" : "M", c1, y + 31, 60);
    champ(pdf, "Redoublant", "Repeater", ins.isRedoublant() ? "Oui / Yes" : "Non / No", c1 + 90, y + 31, 80);
    champ(pdf, "Parent / tuteur", "Guardian", parent.isEmpty() ? "—" : parent, c1, y + 42, lId * 0.6f);
    champ(pdf, "Matricule", "Reg. no", e.getMatricule(), c2, y + 5, lId * 0.38f);
    champ(pdf, "Mat. national", "National ID", e.getMatriculeNational() == null ? "—" : e.getMatriculeNational(), c2, y + 16, lId * 0.38f);
    champ(pdf, "Classe", "Class", classe.getNom(), c2, y + 27, lId * 0.38f);
    champ(pdf, "Effectif", "Enrolment", String.valueOf(effectif), c2, y + 38, lId * 0.38f);
    champ(pdf, "Prof. principal", "Class master", pp == null ? "—" : pp.nomComplet(), c2, y + 49, lId * 0.38f);
    y += hId + 5;

    // ---- Tableau des notes ----
    List<Periode> rappelsPeriodes = ctx.rappels().stream().map(Rappel::periode).toList();
    List<Colonne> cols = colonnes(p, rappelsPeriodes);
    float[] xs = new float[cols.size()];
    float cumul = X0;
    for (int i = 0; i < cols.size(); i++) {
      xs[i] = cumul;
      cumul += cols.get(i).largeur();
    }
    float hBas = 88 + 6 + 74 + 16;
    int nbLignes = resultats.matieres().size() + resultats.groupes().size();
    float hEntete = 20;
    float dispo = Pdf.HAUTEUR - y - hBas - hEntete - 6;
    float h = Math.max(11, Math.min(17, dispo / Math.max(nbLignes, 1)));
    float taille = h < 13 ? 6.2f : 6.8f;

    for (int i = 0; i < cols.size(); i++) {
      Colonne c = cols.get(i);
      pdf.cadre(xs[i], y, c.largeur(), hEntete, Couleurs.ENTETE);
      Style s = Style.de(6.3f).gras();
      pdf.ligne(c.fr(), xs[i] + 2, y + 4, c.largeur() - 4, c.gauche() ? s : s.centre());
      Style s2 = Style.de(5.4f).italique().couleur(Couleurs.GRIS);
      pdf.ligne(c.en(), xs[i] + 2, y + 11.5f, c.largeur() - 4, c.gauche() ? s2 : s2.centre());
    }
    y += hEntete;

    Tableau t = new Tableau(pdf, cols, xs, h, taille);
    for (int gi = 0; gi < resultats.groupes().size(); gi++) {
      Groupe g = resultats.groupes().get(gi);
      for (int mi = 0; mi < resultats.matieres().size(); mi++) {
        StatMatiere s = resultats.matieres().get(mi);
        if (!s.matiere().groupe().id().equals(g.id())) continue;
        for (int i = 0; i < cols.size(); i++) pdf.cadre(xs[i], y, cols.get(i).largeur(), h);
        t.ligneMatiere(y, s, r.matieres().get(mi), ctx, rappelsPeriodes);
        y += h;
      }
      // Sous-total du groupe
      LigneGroupe lg = r.groupes().stream().filter(x -> x.groupeId().equals(g.id())).findFirst().orElseThrow();
      int iCoef = t.col("coef");
      int iTotal = t.col("total");
      float largeurLibelle = xs[iCoef] - X0;
      pdf.cadre(X0, y, largeurLibelle, h, Couleurs.GROUPE);
      pdf.cadre(xs[iCoef], y, cols.get(iCoef).largeur(), h, Couleurs.GROUPE);
      pdf.cadre(xs[iTotal], y, cols.get(iTotal).largeur(), h, Couleurs.GROUPE);
      pdf.cadre(xs[iTotal + 1], y, X0 + L - xs[iTotal + 1], h, Couleurs.GROUPE);
      pdf.ligne(
          "Total groupe " + (gi + 1) + " / Group " + (gi + 1) + " total : " + g.nom(), X0 + 3, y + (h - taille) / 2, largeurLibelle - 6,
          Style.de(taille).gras());
      t.cellule(y, iCoef, String.valueOf(lg.coefficient()), true, null);
      t.cellule(y, iTotal, fr(lg.total()), true, null);
      pdf.ligne(
          "Moyenne / Average : " + (lg.moyenne() == null ? "—" : fr(lg.moyenne()) + " / 20"), xs[iTotal + 1] + 4, y + (h - taille) / 2,
          X0 + L - xs[iTotal + 1] - 8, Style.de(taille).gras());
      y += h;
    }
    y += 6;

    // ---- Blocs du bas : discipline, travail de l'élève, profil de la classe ----
    float hb = 88;
    float l1 = 150;
    float l3 = 160;
    float l2 = L - l1 - l3 - 8;
    float xB2 = X0 + l1 + 4;
    float xB3 = xB2 + l2 + 4;

    // Discipline : laissée vide, remplie à la main.
    pdf.cadre(X0, y, l1, hb);
    titreBloc(pdf, "Discipline / Conduct", X0, y, l1);
    String[] discipline = {
      "Absences non justifiées (h)", "Absences justifiées (h)", "Retards / Late", "Consignes (h) / Detention", "Avertissement / Warning",
      "Blâme / Reprimand", "Exclusion (jours / days)"
    };
    for (int i = 0; i < discipline.length; i++) rangee(pdf, X0, l1, y + 13 + i * 10.6f, discipline[i], "", false, 42);

    // Travail de l'élève
    pdf.cadre(xB2, y, l2, hb);
    titreBloc(pdf, "Travail de l'élève / Student's performance", xB2, y, l2);
    float lGauche = l2 * 0.52f;
    float yT = y + 13;
    rangee(pdf, xB2, lGauche, yT, "Total des points / Total", fr(r.totalPoints()), false, 42);
    rangee(pdf, xB2, lGauche, yT + 10.6f, "Total des coef. / Coef", String.valueOf(r.totalCoef()), false, 42);
    rangee(pdf, xB2, lGauche, yT + 21.2f, "Moyenne / Average", r.moyenne() == null ? "—" : fr(r.moyenne()) + "/20", true, 46);
    rangee(pdf, xB2, lGauche, yT + 31.8f, "Rang / Rank", r.rang() != null ? CalculResultats.formatRang(r.rang()) + " / " + classesAvecMoyenne : "—", true, 42);
    for (int k = 0; k < ctx.rappels().size(); k++) {
      Rappel rp = ctx.rappels().get(k);
      ResultatEleve er = rp.resultats().eleve(inscriptionId);
      String valeur = er == null || er.moyenne() == null ? "—" : fr(er.moyenne()) + " (" + CalculResultats.formatRang(er.rang()) + ")";
      rangee(pdf, xB2, lGauche, yT + 42.4f + k * 10.6f, libelleCourt(rp.periode())[0], valeur, false, 58);
    }
    // Cotes de compétence, la cote obtenue est cochée
    float xC = xB2 + lGauche;
    float lC = l2 - lGauche;
    pdf.trait(xC, y + 12, xC, y + hb, 0.5f, Couleurs.TRAIT);
    List<Seuil> cotes = donnees.baremes().cotes().stream().sorted(Comparator.comparingDouble(Seuil::min).reversed()).toList();
    for (int i = 0; i < cotes.size(); i++) {
      Seuil c = cotes.get(i);
      float yy = yT + i * 12.4f;
      boolean coche = c.libelle().equals(r.cote());
      pdf.cadre(xC + 4, yy + 2.5f, 7, 7, coche ? Couleurs.ACCENT : null, Couleurs.TRAIT);
      if (coche) pdf.ligne("X", xC + 4, yy + 3.2f, 7, Style.de(6).gras().couleur("#ffffff").centre());
      pdf.ligne(c.libelle(), xC + 14, yy + 2.6f, 26, Style.de(6.4f).gras());
      pdf.ligne(sansPrefixe(c.libelleLong()), xC + 38, yy + 3, lC - 41, Style.de(5.2f).couleur(Couleurs.GRIS));
    }

    // Profil de la classe
    ProfilClasse pf = resultats.profil();
    pdf.cadre(xB3, y, l3, hb);
    titreBloc(pdf, "Profil de la classe / Class profile", xB3, y, l3);
    rangee(pdf, xB3, l3, yT, "Moyenne générale / Class average", fr(pf.moyenneGenerale()), true, 42);
    rangee(pdf, xB3, l3, yT + 10.6f, "Min – Max", pf.min() == null ? "—" : fr(pf.min()) + " – " + fr(pf.max()), false, 58);
    rangee(pdf, xB3, l3, yT + 21.2f, "Nombre de moyennes / Passes", String.valueOf(pf.nombreMoyennes()), false, 42);
    rangee(pdf, xB3, l3, yT + 31.8f, "Taux de réussite / Pass rate", pourcent(pf.tauxReussite()), false, 42);
    rangee(pdf, xB3, l3, yT + 42.4f, "Élèves classés / Ranked", String.valueOf(classesAvecMoyenne), false, 42);
    rangee(pdf, xB3, l3, yT + 53, "Écart-type / Std deviation", fr(pf.ecartType()), false, 42);
    y += hb + 6;

    // ---- Signatures ----
    float hs = 72;
    float[] ls = {190, 105, 122, L - 190 - 105 - 122};
    String[][] titresSig = {
      {"Appréciation du travail de l'élève", "Points forts et points à améliorer"},
      {"Visa du parent / tuteur", "Parent's signature"},
      {"Professeur principal", "Class master"},
      {"Le Chef d'établissement", "The Principal"}
    };
    float xs2 = X0;
    for (int i = 0; i < ls.length; i++) {
      pdf.cadre(xs2, y, ls[i], hs);
      pdf.ligne(titresSig[i][0], xs2 + 3, y + 3, ls[i] - 6, Style.de(6.5f).gras());
      pdf.ligne(titresSig[i][1], xs2 + 3, y + 11, ls[i] - 6, Style.de(5.6f).italique().couleur(Couleurs.GRIS));
      xs2 += ls[i];
    }
    if (pp != null) pdf.ligne(pp.nomComplet(), X0 + ls[0] + ls[1] + 3, y + 21, ls[2] - 6, Style.de(6.6f));
    if (p == Periode.A) {
      pdf.ligne("Décision du conseil de classe / Class council decision :", X0 + 3, y + hs - 22, ls[0] - 6, Style.de(6).gras());
      pdf.ligne("Admis(e) en classe supérieure  /  Redouble  /  Exclu(e)", X0 + 3, y + hs - 12, ls[0] - 6, Style.de(6).couleur(Couleurs.GRIS));
    }

    // ---- Pied ----
    pdf.ligne(
        etab.getNomFr() + " · " + classe.getNom() + " · " + e.nomComplet() + " · édité le " + EnteteOfficiel.aujourdhui(), X0, Pdf.HAUTEUR - 13, L,
        Style.de(5.4f).couleur(Couleurs.GRIS));
  }

  private static String pourcent(Double v) {
    return v == null ? "" : fr(v) + " %";
  }

  /** « Libellé / Label : valeur » sur une ligne, en trois styles. */
  private static void champ(Pdf pdf, String fr, String en, String valeur, float x, float y, float largeur) {
    float fin = pdf.suite(fr + " / ", x, y, Style.de(6.3f).gras().couleur(Couleurs.GRIS));
    fin = pdf.suite(en + " : ", fin, y, Style.de(6.3f).italique().couleur(Couleurs.GRIS));
    // La valeur peut déborder sur l'espace libre à droite du libellé (comme sur les bulletins papier).
    pdf.ligne(valeur, fin, y, largeur, Style.de(7));
  }

  private static void titreBloc(Pdf pdf, String s, float x, float y, float l) {
    pdf.cadre(x, y, l, 12, Couleurs.ENTETE);
    pdf.ligne(s, x, y + 3, l, Style.de(6.6f).gras().centre());
  }

  private static void rangee(Pdf pdf, float x, float l, float yy, String libelle, String valeur, boolean gras, float lv) {
    pdf.trait(x, yy + 10.4f, x + l, yy + 10.4f, 0.3f, Couleurs.TRAIT_FIN);
    pdf.ligne(libelle, x + 3, yy + 2.6f, l - lv - 6, Style.de(6.2f));
    Style s = Style.de(gras ? 7.6f : 6.6f).droite();
    pdf.ligne(valeur, x + l - lv - 3, yy + 2.3f, lv, gras ? s.gras() : s);
  }

  /** Cellules du tableau des notes. */
  private record Tableau(Pdf pdf, List<Colonne> cols, float[] xs, float h, float taille) {

    int col(String cle) {
      for (int i = 0; i < cols.size(); i++) if (cols.get(i).cle().equals(cle)) return i;
      return -1;
    }

    void cellule(float y, int i, String s, boolean gras, String couleur) {
      Colonne c = cols.get(i);
      Style st = Style.de(taille);
      if (gras) st = st.gras();
      if (couleur != null) st = st.couleur(couleur);
      if (!c.gauche()) st = st.centre();
      pdf.ligne(s, xs[i] + 2, y + (h - taille) / 2, c.largeur() - 4, st);
    }

    void ligneMatiere(float y, StatMatiere s, LigneMatiere l, Contexte ctx, List<Periode> rappels) {
      MatiereEntree m = s.matiere();
      // Matière : nom, puis nom anglais et professeur en petit
      int iM = col("matiere");
      String sousTitre = java.util.stream.Stream.of(m.nomEn(), m.professeur()).filter(Objects::nonNull).collect(Collectors.joining(" · "));
      if (!sousTitre.isEmpty() && h >= 13) {
        pdf.ligne(m.nom(), xs[iM] + 2, y + 1.6f, cols.get(iM).largeur() - 4, Style.de(taille).gras());
        pdf.ligne(sousTitre, xs[iM] + 2, y + 1.6f + taille + 0.8f, cols.get(iM).largeur() - 4, Style.de(5.2f).italique().couleur(Couleurs.GRIS));
      } else {
        cellule(y, iM, m.nom(), true, null);
      }
      int iComp = col("competence");
      if (!s.evaluee()) {
        cellule(y, col("note"), "—", false, null);
        if (iComp >= 0) cellule(y, iComp, "Pas d'évaluation / No exam", false, Couleurs.GRIS);
        cellule(y, col("coef"), String.valueOf(m.coefficient()), false, Couleurs.GRIS);
        return;
      }
      if (iComp >= 0) {
        pdf.bloc(ctx.competences().getOrDefault(m.id(), ""), xs[iComp] + 2, y + 1.5f, cols.get(iComp).largeur() - 4, h - 2, Style.de(5.4f));
      }
      for (int k = 0; k < rappels.size(); k++) {
        Double v = k < l.detail().size() ? l.detail().get(k) : null;
        cellule(y, col("d-" + rappels.get(k).name()), v == null ? "—" : fr(v), false, null);
      }
      cellule(y, col("note"), fr(l.note()), true, l.note() != null && l.note() < 10 ? "#a3261b" : Couleurs.ENCRE);
      cellule(y, col("coef"), String.valueOf(m.coefficient()), false, null);
      cellule(y, col("total"), fr(l.total()), false, null);
      cellule(y, col("rang"), CalculResultats.formatRang(l.rang()), false, null);
      cellule(y, col("moycl"), fr(s.moyenne()), false, null);
      cellule(y, col("reussite"), pourcent(s.tauxReussite()), false, null);
      cellule(y, col("grade"), l.grade() == null ? "" : l.grade(), true, null);
      cellule(y, col("extrema"), fr(s.min()) + " – " + fr(s.max()), false, null);
    }
  }
}
