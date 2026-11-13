package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.AbsencesDto.Ligne;
import cm.cetic.mezesse.dto.AbsencesDto.Totaux;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.service.BilanAbsences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

/** Export des absences filtrées (conseils de classe, administration) : classeur Excel ou PDF imprimable. */
public final class AbsencesExport {

  private AbsencesExport() {}

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String[] TITRES = {"Élève", "Matricule", "Classe", "Date", "Matière", "Début", "Fin", "Durée (min)", "État", "Motif"};

  private static String[] valeurs(Ligne l) {
    return new String[] {
      l.nom() + " " + l.prenoms(), l.matricule(), l.classe(), DATE.format(l.date()), l.matiere(), l.debut(), l.fin(), String.valueOf(l.duree()),
      l.justification() == null ? "Non justifiée" : "Justifiée", l.justification() == null ? "" : l.justification().motif()
    };
  }

  public static byte[] excel(String titre, List<Ligne> lignes, Totaux t) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet f = wb.createSheet("Absences");
      Font gras = wb.createFont();
      gras.setBold(true);
      CellStyle styleTitre = wb.createCellStyle();
      styleTitre.setFont(gras);
      XSSFCellStyle entete = wb.createCellStyle();
      entete.setFont(gras);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      Row r0 = f.createRow(0);
      Cell c0 = r0.createCell(0);
      c0.setCellValue(titre);
      c0.setCellStyle(styleTitre);
      f.createRow(1).createCell(0).setCellValue(
          t.absences() + " séance(s) manquée(s) · total " + BilanAbsences.duree((int) t.minutes()) + " · justifiées " + BilanAbsences.duree((int) t.minutesJustifiees())
              + " · non justifiées " + BilanAbsences.duree((int) t.minutesNonJustifiees()));
      Row e = f.createRow(3);
      for (int i = 0; i < TITRES.length; i++) {
        Cell c = e.createCell(i);
        c.setCellValue(TITRES[i]);
        c.setCellStyle(entete);
      }
      for (int k = 0; k < lignes.size(); k++) {
        Row r = f.createRow(4 + k);
        String[] v = valeurs(lignes.get(k));
        for (int i = 0; i < v.length; i++) {
          if (i == 7) r.createCell(i).setCellValue(lignes.get(k).duree());
          else r.createCell(i).setCellValue(v[i]);
        }
      }
      int[] largeurs = {28, 13, 22, 11, 26, 7, 7, 11, 14, 22};
      for (int i = 0; i < largeurs.length; i++) f.setColumnWidth(i, largeurs[i] * 256);
      f.createFreezePane(0, 4);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        wb.write(out);
        return out.toByteArray();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static byte[] pdf(Etablissement etab, String titre, List<Ligne> lignes, Totaux t) {
    try (Pdf pdf = new Pdf(titre, "CETIC de Mezesse")) {
      float x0 = 24;
      float l = Pdf.LARGEUR - 48;
      String[] titres = {"Élève", "Classe", "Date", "Matière", "Horaire", "Durée", "État"};
      float[] largeurs = {130, 88, 52, 105, 56, 38, 0};
      float somme = 0;
      for (int i = 0; i < largeurs.length - 1; i++) somme += largeurs[i];
      largeurs[largeurs.length - 1] = l - somme;
      float h = 14;
      pdf.nouvellePage();
      float[] y = {EnteteOfficiel.dessiner(pdf, etab, 22) + 4};
      pdf.ligne(titre.toUpperCase(Locale.ROOT), x0, y[0], l, Style.de(11).gras().couleur(Couleurs.ACCENT).centre());
      y[0] += 15;
      pdf.ligne(
          t.absences() + " séance(s) manquée(s)  ·  total " + BilanAbsences.duree((int) t.minutes()) + "  ·  justifiées " + BilanAbsences.duree((int) t.minutesJustifiees())
              + "  ·  non justifiées " + BilanAbsences.duree((int) t.minutesNonJustifiees()),
          x0, y[0], l, Style.de(7.5f).couleur(Couleurs.GRIS).centre());
      y[0] += 16;
      Runnable entete =
          () -> {
            float x = x0;
            for (int i = 0; i < titres.length; i++) {
              pdf.cadre(x, y[0], largeurs[i], h, Couleurs.ENTETE);
              pdf.ligne(titres[i], x + 3, y[0] + 4, largeurs[i] - 6, Style.de(7).gras());
              x += largeurs[i];
            }
            y[0] += h;
          };
      entete.run();
      for (int k = 0; k < lignes.size(); k++) {
        if (y[0] + h > Pdf.HAUTEUR - 30) {
          pdf.nouvellePage();
          y[0] = 30;
          entete.run();
        }
        Ligne a = lignes.get(k);
        String[] v = {
          a.nom() + " " + a.prenoms(), a.classe(), DATE.format(a.date()), a.matiere(), a.debut() + "–" + a.fin(), BilanAbsences.duree(a.duree()),
          a.justification() == null ? "Non justifiée" : "Justifiée · " + a.justification().motif()
        };
        float x = x0;
        for (int i = 0; i < v.length; i++) {
          pdf.cadre(x, y[0], largeurs[i], h, k % 2 == 1 ? "#f6f8fb" : null);
          Style s = Style.de(6.8f);
          if (i == 6) s = s.couleur(a.justification() == null ? "#b3261e" : "#1e764c");
          pdf.ligne(v[i], x + 3, y[0] + 4, largeurs[i] - 6, i == 0 ? s.gras() : s);
          x += largeurs[i];
        }
        y[0] += h;
      }
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · " + titre + " · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }
}
