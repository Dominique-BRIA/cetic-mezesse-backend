package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Creneau;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Plage;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Vue;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.service.BilanAbsences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Emploi du temps d'une classe en tableau (heures en lignes, jours en colonnes) : classeur Excel ou PDF à afficher. */
public final class EmploiDuTempsExport {

  private EmploiDuTempsExport() {}

  private static final String[] JOURS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi"};

  /** Une ligne horaire : celles saisies et celles des cours, dans l'ordre. */
  private record Horaire(String debut, String fin) {}

  private static List<Horaire> horaires(Vue v) {
    TreeSet<String> cles = new TreeSet<>();
    if (v.plages() != null) for (Plage p : v.plages()) cles.add(p.debut() + "|" + p.fin());
    for (Creneau c : v.creneaux()) cles.add(c.debut() + "|" + c.fin());
    List<Horaire> l = new ArrayList<>();
    for (String k : cles) {
      String[] p = k.split("\\|");
      l.add(new Horaire(p[0], p[1]));
    }
    return l;
  }

  /** Cours d'une case : [matière, professeur] pour chacun (en principe un seul). */
  private static List<String[]> cours(Vue v, Horaire h, int jour) {
    List<String[]> l = new ArrayList<>();
    for (Creneau c : v.creneaux()) {
      if (c.jour() != jour || !c.debut().equals(h.debut()) || !c.fin().equals(h.fin())) continue;
      String matiere = c.matiere() == null ? "" : c.matiere().nom();
      String prof = c.matiere() == null || c.matiere().professeur() == null ? "" : c.matiere().professeur().prenom() + " " + c.matiere().professeur().nom();
      l.add(new String[] {matiere, prof});
    }
    return l;
  }

  private static String resume(Vue v) {
    String s = "";
    if (v.version() != null) {
      var ver = v.version();
      s = ver.valableDu() == null ? "Depuis la rentrée" : "Depuis le " + EnteteOfficiel.dateFr(ver.valableDu());
      if (ver.valableAu() != null) s += " jusqu'au " + EnteteOfficiel.dateFr(ver.valableAu());
      s += " · ";
    }
    return s + BilanAbsences.duree(v.minutesParSemaine()) + " de cours par semaine";
  }

  public static byte[] excel(String titre, Vue v) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet f = wb.createSheet("Emploi du temps");
      Font gras = wb.createFont();
      gras.setBold(true);
      CellStyle styleTitre = wb.createCellStyle();
      styleTitre.setFont(gras);
      XSSFCellStyle entete = wb.createCellStyle();
      entete.setFont(gras);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      entete.setAlignment(HorizontalAlignment.CENTER);
      entete.setVerticalAlignment(VerticalAlignment.CENTER);
      bordures(entete);
      XSSFCellStyle caseCours = wb.createCellStyle();
      caseCours.setWrapText(true);
      caseCours.setAlignment(HorizontalAlignment.CENTER);
      caseCours.setVerticalAlignment(VerticalAlignment.CENTER);
      bordures(caseCours);

      Cell c0 = f.createRow(0).createCell(0);
      c0.setCellValue(titre);
      c0.setCellStyle(styleTitre);
      f.createRow(1).createCell(0).setCellValue(resume(v));
      Row e = f.createRow(3);
      Cell h0 = e.createCell(0);
      h0.setCellValue("Horaire");
      h0.setCellStyle(entete);
      for (int j = 0; j < JOURS.length; j++) {
        Cell c = e.createCell(j + 1);
        c.setCellValue(JOURS[j]);
        c.setCellStyle(entete);
      }
      List<Horaire> lignes = horaires(v);
      for (int k = 0; k < lignes.size(); k++) {
        Horaire h = lignes.get(k);
        Row r = f.createRow(4 + k);
        int lignesTexte = 1;
        Cell ch = r.createCell(0);
        ch.setCellValue(h.debut() + " – " + h.fin());
        ch.setCellStyle(entete);
        for (int j = 1; j <= JOURS.length; j++) {
          List<String> morceaux = new ArrayList<>();
          for (String[] c : cours(v, h, j)) {
            morceaux.add(c[0]);
            if (!c[1].isEmpty()) morceaux.add(c[1]);
          }
          Cell c = r.createCell(j);
          c.setCellValue(String.join("\n", morceaux));
          c.setCellStyle(caseCours);
          lignesTexte = Math.max(lignesTexte, morceaux.size());
        }
        r.setHeightInPoints(Math.max(30, lignesTexte * 15f + 4));
      }
      f.setColumnWidth(0, 15 * 256);
      for (int j = 1; j <= JOURS.length; j++) f.setColumnWidth(j, 24 * 256);
      f.createFreezePane(1, 4);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        wb.write(out);
        return out.toByteArray();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static void bordures(CellStyle s) {
    s.setBorderTop(BorderStyle.THIN);
    s.setBorderBottom(BorderStyle.THIN);
    s.setBorderLeft(BorderStyle.THIN);
    s.setBorderRight(BorderStyle.THIN);
  }

  public static byte[] pdf(Etablissement etab, String titre, Vue v) {
    try (Pdf pdf = new Pdf(titre, "CETIC de Mezesse")) {
      float x0 = 24;
      float l = Pdf.LARGEUR - 48;
      pdf.nouvellePage();
      float y = EnteteOfficiel.dessiner(pdf, etab, 22) + 4;
      pdf.ligne(titre.toUpperCase(Locale.ROOT), x0, y, l, Style.de(12).gras().couleur(Couleurs.ACCENT).centre());
      y += 16;
      pdf.ligne(resume(v), x0, y, l, Style.de(8).couleur(Couleurs.GRIS).centre());
      y += 18;
      float lh = 62;
      float lj = (l - lh) / JOURS.length;
      float he = 18;
      pdf.cadre(x0, y, lh, he, Couleurs.ENTETE);
      pdf.ligne("Horaire", x0, y + 5.5f, lh, Style.de(8).gras().centre());
      for (int j = 0; j < JOURS.length; j++) {
        pdf.cadre(x0 + lh + j * lj, y, lj, he, Couleurs.ENTETE);
        pdf.ligne(JOURS[j], x0 + lh + j * lj, y + 5.5f, lj, Style.de(8.5f).gras().centre());
      }
      y += he;
      List<Horaire> lignes = horaires(v);
      // Les lignes occupent la page : entre 28 et 52 points chacune.
      float dispo = Pdf.HAUTEUR - 40 - y;
      float hl = lignes.isEmpty() ? 30 : Math.max(28, Math.min(52, dispo / lignes.size()));
      for (Horaire h : lignes) {
        if (y + hl > Pdf.HAUTEUR - 36) {
          pdf.nouvellePage();
          y = 30;
        }
        pdf.cadre(x0, y, lh, hl, Couleurs.GROUPE);
        pdf.ligne(h.debut(), x0, y + hl / 2 - 8, lh, Style.de(8).gras().centre());
        pdf.ligne(h.fin(), x0, y + hl / 2 + 1, lh, Style.de(8).couleur(Couleurs.GRIS).centre());
        for (int j = 1; j <= JOURS.length; j++) {
          float x = x0 + lh + (j - 1) * lj;
          pdf.cadre(x, y, lj, hl, null);
          List<String[]> cs = cours(v, h, j);
          if (cs.isEmpty()) continue;
          float hTexte = cs.size() * 19f;
          float yt = y + Math.max(3, (hl - hTexte) / 2);
          for (String[] c : cs) {
            if (yt + 9 > y + hl) break;
            pdf.ligneAjustee(c[0], x + 3, yt, lj - 6, Style.de(8).gras().centre());
            yt += 9.5f;
            if (!c[1].isEmpty() && yt + 8 <= y + hl) pdf.ligneAjustee(c[1], x + 3, yt, lj - 6, Style.de(7).couleur(Couleurs.GRIS).centre());
            yt += 9.5f;
          }
        }
        y += hl;
      }
      if (lignes.isEmpty()) pdf.ligne("Aucun cours saisi.", x0, y + 10, l, Style.de(9).italique().centre());
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · " + titre + " · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }
}
