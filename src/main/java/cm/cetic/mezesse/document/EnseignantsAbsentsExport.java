package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.AbsencesDto.EnseignantsAbsents;
import cm.cetic.mezesse.dto.AbsencesDto.SeanceNonAssuree;
import cm.cetic.mezesse.dto.AbsencesDto.TotalEnseignant;
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

/** Export des séances non assurées (enseignants absents) : totaux par enseignant puis détail des séances. */
public final class EnseignantsAbsentsExport {

  private EnseignantsAbsentsExport() {}

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private static String resume(EnseignantsAbsents d) {
    int total = d.totaux().stream().mapToInt(TotalEnseignant::minutes).sum();
    return d.seances().size() + " séance(s) non assurée(s) · total " + BilanAbsences.duree(total);
  }

  private static String nom(TotalEnseignant t) {
    return t.id() == null ? "Sans enseignant" : t.nom() + " " + t.prenom();
  }

  private static String texte(String s) {
    return s == null ? "" : s;
  }

  public static byte[] excel(String titre, EnseignantsAbsents d) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Font gras = wb.createFont();
      gras.setBold(true);
      CellStyle styleTitre = wb.createCellStyle();
      styleTitre.setFont(gras);
      XSSFCellStyle entete = wb.createCellStyle();
      entete.setFont(gras);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      Sheet s = wb.createSheet("Séances non assurées");
      enTete(s, titre, resume(d), new String[] {"Date", "Classe", "Matière", "Début", "Fin", "Durée (min)", "Enseignant", "Motif"}, styleTitre, entete);
      for (int k = 0; k < d.seances().size(); k++) {
        SeanceNonAssuree x = d.seances().get(k);
        Row r = s.createRow(4 + k);
        r.createCell(0).setCellValue(DATE.format(x.date()));
        r.createCell(1).setCellValue(texte(x.classe()));
        r.createCell(2).setCellValue(texte(x.matiere()));
        r.createCell(3).setCellValue(x.debut());
        r.createCell(4).setCellValue(x.fin());
        r.createCell(5).setCellValue(x.duree());
        r.createCell(6).setCellValue(texte(x.enseignant()));
        r.createCell(7).setCellValue(texte(x.motif()));
      }
      largeurs(s, 11, 22, 24, 7, 7, 11, 24, 28);

      Sheet t = wb.createSheet("Par enseignant");
      enTete(t, titre, resume(d), new String[] {"Enseignant", "Séances", "Durée (min)", "Durée"}, styleTitre, entete);
      for (int k = 0; k < d.totaux().size(); k++) {
        TotalEnseignant x = d.totaux().get(k);
        Row r = t.createRow(4 + k);
        r.createCell(0).setCellValue(nom(x));
        r.createCell(1).setCellValue(x.seances());
        r.createCell(2).setCellValue(x.minutes());
        r.createCell(3).setCellValue(BilanAbsences.duree(x.minutes()));
      }
      largeurs(t, 30, 10, 12, 10);

      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        wb.write(out);
        return out.toByteArray();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static void enTete(Sheet f, String titre, String resume, String[] titres, CellStyle styleTitre, CellStyle entete) {
    Cell c0 = f.createRow(0).createCell(0);
    c0.setCellValue(titre);
    c0.setCellStyle(styleTitre);
    f.createRow(1).createCell(0).setCellValue(resume);
    Row e = f.createRow(3);
    for (int i = 0; i < titres.length; i++) {
      Cell c = e.createCell(i);
      c.setCellValue(titres[i]);
      c.setCellStyle(entete);
    }
    f.createFreezePane(0, 4);
  }

  private static void largeurs(Sheet f, int... l) {
    for (int i = 0; i < l.length; i++) f.setColumnWidth(i, l[i] * 256);
  }

  public static byte[] pdf(Etablissement etab, String titre, EnseignantsAbsents d) {
    try (Pdf pdf = new Pdf(titre, "CETIC de Mezesse")) {
      float x0 = 24;
      float l = Pdf.LARGEUR - 48;
      float h = 14;
      pdf.nouvellePage();
      float[] y = {EnteteOfficiel.dessiner(pdf, etab, 22) + 4};
      pdf.ligne(titre.toUpperCase(Locale.ROOT), x0, y[0], l, Style.de(11).gras().couleur(Couleurs.ACCENT).centre());
      y[0] += 15;
      pdf.ligne(resume(d), x0, y[0], l, Style.de(7.5f).couleur(Couleurs.GRIS).centre());
      y[0] += 18;

      // Un seul enseignant (filtre) : son total est déjà sous le titre.
      if (d.totaux().size() > 1) {
        pdf.ligne("Par enseignant", x0, y[0], l, Style.de(8.5f).gras());
        y[0] += 12;
        tableau(pdf, y, x0, h, new String[] {"Enseignant", "Séances", "Durée"}, new float[] {l - 160, 70, 90},
            d.totaux().stream().map(t -> new String[] {nom(t), String.valueOf(t.seances()), BilanAbsences.duree(t.minutes())}).toList(), 2);
        y[0] += 14;
      }

      if (y[0] + 12 + 2 * h > Pdf.HAUTEUR - 30) {
        pdf.nouvellePage();
        y[0] = 30;
      }
      pdf.ligne("Toutes les séances non assurées", x0, y[0], l, Style.de(8.5f).gras());
      y[0] += 12;
      float[] ls = {50, 82, 100, 56, 36, 100, 0};
      float somme = 0;
      for (int i = 0; i < ls.length - 1; i++) somme += ls[i];
      ls[ls.length - 1] = l - somme;
      tableau(pdf, y, x0, h, new String[] {"Date", "Classe", "Matière", "Horaire", "Durée", "Enseignant", "Motif"}, ls,
          d.seances().stream()
              .map(s -> new String[] {
                DATE.format(s.date()), texte(s.classe()), texte(s.matiere()), s.debut() + "–" + s.fin(), BilanAbsences.duree(s.duree()),
                texte(s.enseignant()), texte(s.motif())})
              .toList(),
          -1);
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · " + titre + " · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }

  /** Tableau simple sur plusieurs pages ; « rouge » = colonne écrite en rouge (-1 : aucune). */
  private static void tableau(Pdf pdf, float[] y, float x0, float h, String[] titres, float[] largeurs, List<String[]> lignes, int rouge) {
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
      String[] v = lignes.get(k);
      float x = x0;
      for (int i = 0; i < v.length; i++) {
        pdf.cadre(x, y[0], largeurs[i], h, k % 2 == 1 ? "#f6f8fb" : null);
        Style s = Style.de(6.8f);
        if (i == rouge) s = s.couleur("#b3261e").gras();
        pdf.ligne(v[i], x + 3, y[0] + 4, largeurs[i] - 6, s);
        x += largeurs[i];
      }
      y[0] += h;
    }
  }
}
