package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.AbsencesDto.Bilan;
import cm.cetic.mezesse.dto.AbsencesDto.BilanEleve;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.service.BilanAbsences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Bilan des absences d'une classe pour une période : heures de chaque élève et sanction du bulletin (PDF ou Excel). */
public final class BilanAbsencesExport {

  private BilanAbsencesExport() {}

  /** Heures « 3 h 20 », ou un tiret quand il n'y en a pas. */
  private static String h(int minutes) {
    return minutes == 0 ? "—" : BilanAbsences.duree(minutes);
  }

  /** Sanction telle que la page l'affiche, avec la décision du Directeur ou l'exclusion encore à décider. */
  static String sanction(BilanEleve e) {
    String s = e.libelleSanction() == null ? "" : e.libelleSanction();
    if ("Exclusion temporaire".equals(s) && e.joursExclusion() > 0) s += " · " + e.joursExclusion() + " jour" + (e.joursExclusion() > 1 ? "s" : "");
    if (e.palierExclusion() && e.decision() == null) s += (s.isEmpty() ? "" : " · ") + "exclusion à décider";
    return s;
  }

  /** Les seuils réglés, en une ligne. */
  private static String regles(Bilan b) {
    if (b.sanctions().isEmpty()) return "Aucune sanction n'est réglée.";
    StringBuilder s = new StringBuilder("Sanctions (heures non justifiées depuis la rentrée) : ");
    for (int i = 0; i < b.sanctions().size(); i++) {
      var r = b.sanctions().get(i);
      if (i > 0) s.append(" · ");
      s.append(r.libelle()).append(" dès ").append(BilanAbsences.duree(r.minutes()));
    }
    return s.toString();
  }

  private static int[] totaux(Bilan b) {
    int[] t = new int[3];
    for (BilanEleve e : b.eleves()) {
      t[0] += e.minutes();
      t[1] += e.justifiees();
      t[2] += e.nonJustifiees();
    }
    return t;
  }

  private static final String[] TITRES = {"N°", "Élève", "Matricule", "Séances", "Total", "Justifiées", "Non justifiées", "Non justifiées depuis la rentrée", "Sanction au bulletin"};

  public static byte[] excel(String titre, Bilan b) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet f = wb.createSheet("Bilan des absences");
      Font gras = wb.createFont();
      gras.setBold(true);
      CellStyle styleGras = wb.createCellStyle();
      styleGras.setFont(gras);
      XSSFCellStyle entete = wb.createCellStyle();
      entete.setFont(gras);
      entete.setWrapText(true);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      Cell c0 = f.createRow(0).createCell(0);
      c0.setCellValue(titre);
      c0.setCellStyle(styleGras);
      f.createRow(1).createCell(0).setCellValue(regles(b));
      Row e = f.createRow(3);
      for (int i = 0; i < TITRES.length; i++) {
        Cell c = e.createCell(i);
        c.setCellValue(TITRES[i]);
        c.setCellStyle(entete);
      }
      int k = 4;
      for (BilanEleve x : b.eleves()) {
        Row r = f.createRow(k++);
        r.createCell(0).setCellValue(x.numero());
        r.createCell(1).setCellValue(x.nom() + " " + x.prenoms());
        r.createCell(2).setCellValue(x.matricule() == null ? "" : x.matricule());
        r.createCell(3).setCellValue(x.seances());
        r.createCell(4).setCellValue(BilanAbsences.duree(x.minutes()));
        r.createCell(5).setCellValue(BilanAbsences.duree(x.justifiees()));
        r.createCell(6).setCellValue(BilanAbsences.duree(x.nonJustifiees()));
        r.createCell(7).setCellValue(BilanAbsences.duree(x.cumulNonJustifiees()));
        r.createCell(8).setCellValue(sanction(x));
      }
      int[] t = totaux(b);
      Row r = f.createRow(k);
      Cell ct = r.createCell(1);
      ct.setCellValue("Classe");
      ct.setCellStyle(styleGras);
      r.createCell(4).setCellValue(BilanAbsences.duree(t[0]));
      r.createCell(5).setCellValue(BilanAbsences.duree(t[1]));
      r.createCell(6).setCellValue(BilanAbsences.duree(t[2]));
      int[] largeurs = {5, 30, 14, 9, 10, 11, 13, 16, 34};
      for (int i = 0; i < largeurs.length; i++) f.setColumnWidth(i, largeurs[i] * 256);
      e.setHeightInPoints(30);
      f.createFreezePane(2, 4);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        wb.write(out);
        return out.toByteArray();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static byte[] pdf(Etablissement etab, String titre, Bilan b) {
    try (Pdf pdf = new Pdf(titre, "CETIC de Mezesse")) {
      float x0 = 24;
      float l = Pdf.LARGEUR - 48;
      float h = 15;
      pdf.nouvellePage();
      float[] y = {EnteteOfficiel.dessiner(pdf, etab, 22) + 4};
      pdf.ligne(titre.toUpperCase(Locale.ROOT), x0, y[0], l, Style.de(11).gras().couleur(Couleurs.ACCENT).centre());
      y[0] += 15;
      pdf.ligne(regles(b), x0, y[0], l, Style.de(7.5f).couleur(Couleurs.GRIS).centre());
      y[0] += 16;

      String[] titres = {"N°", "Élève", "Séances", "Total", "Justifiées", "Non just.", "Cumul non just.", "Sanction au bulletin"};
      float[] largeurs = {20, 136, 38, 42, 46, 46, 70, 0};
      float somme = 0;
      for (int i = 0; i < largeurs.length - 1; i++) somme += largeurs[i];
      largeurs[largeurs.length - 1] = l - somme;
      Runnable entete =
          () -> {
            float x = x0;
            for (int i = 0; i < titres.length; i++) {
              pdf.cadre(x, y[0], largeurs[i], h, Couleurs.ENTETE);
              Style s = Style.de(7).gras();
              pdf.ligneAjustee(titres[i], x + 3, y[0] + 4.5f, largeurs[i] - 6, i >= 2 && i <= 6 ? s.droite() : s);
              x += largeurs[i];
            }
            y[0] += h;
          };
      entete.run();
      for (int k = 0; k < b.eleves().size(); k++) {
        if (y[0] + h > Pdf.HAUTEUR - 30) {
          pdf.nouvellePage();
          y[0] = 30;
          entete.run();
        }
        BilanEleve e = b.eleves().get(k);
        String[] v = {
          String.valueOf(e.numero()), e.nom() + " " + e.prenoms(), e.seances() == 0 ? "—" : String.valueOf(e.seances()), h(e.minutes()), h(e.justifiees()),
          h(e.nonJustifiees()), h(e.cumulNonJustifiees()), sanction(e)
        };
        float x = x0;
        for (int i = 0; i < v.length; i++) {
          pdf.cadre(x, y[0], largeurs[i], h, k % 2 == 1 ? "#f6f8fb" : null);
          Style s = Style.de(7);
          if (i == 0) s = s.couleur(Couleurs.GRIS).droite();
          else if (i == 1) s = s.gras();
          else if (i == 4 && e.justifiees() > 0) s = s.couleur("#1e764c").droite();
          else if (i == 5 && e.nonJustifiees() > 0) s = s.couleur("#b3261e").gras().droite();
          else if (i >= 2 && i <= 6) s = s.droite();
          else if (i == 7 && !v[7].isEmpty()) s = s.couleur(e.sanction() != null && !"AVERTISSEMENT".equals(e.sanction()) ? "#b3261e" : "#9a5b00").gras();
          pdf.ligneAjustee(v[i], x + 3, y[0] + 4.5f, largeurs[i] - 6, s);
          x += largeurs[i];
        }
        y[0] += h;
      }
      // Ligne de la classe.
      if (y[0] + h > Pdf.HAUTEUR - 30) {
        pdf.nouvellePage();
        y[0] = 30;
      }
      int[] t = totaux(b);
      String[] v = {"", "Classe", "", BilanAbsences.duree(t[0]), BilanAbsences.duree(t[1]), BilanAbsences.duree(t[2]), "", ""};
      float x = x0;
      for (int i = 0; i < v.length; i++) {
        pdf.cadre(x, y[0], largeurs[i], h, Couleurs.GROUPE);
        pdf.ligne(v[i], x + 3, y[0] + 4.5f, largeurs[i] - 6, i >= 2 ? Style.de(7).gras().droite() : Style.de(7).gras());
        x += largeurs[i];
      }
      y[0] += h + 26;
      // Visa du Surveillant Général.
      if (y[0] + 50 > Pdf.HAUTEUR - 30) {
        pdf.nouvellePage();
        y[0] = 40;
      }
      pdf.ligne("Fait à Mezesse, le " + EnteteOfficiel.aujourdhui(), x0 + l - 200, y[0], 200, Style.de(8).centre());
      pdf.ligne("Le Surveillant Général", x0 + l - 200, y[0] + 13, 200, Style.de(8).gras().centre());
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · " + titre + " · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }
}
