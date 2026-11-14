package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.DocumentDto.Colonne;
import cm.cetic.mezesse.dto.DocumentDto.Ligne;
import cm.cetic.mezesse.dto.DocumentDto.Tableau;
import cm.cetic.mezesse.entity.Etablissement;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Tableau affiché par une page (élèves, classes, matières, notes, classement…) mis en PDF officiel ou en
 * classeur Excel : la page envoie ses colonnes et ses lignes telles qu'elle les montre, filtres et tri compris.
 */
public final class TableauExport {

  private TableauExport() {}

  private static final Pattern NOMBRE = Pattern.compile("^-?\\d+([.,]\\d+)?$");

  private static float poids(Colonne c) {
    return c.largeur() == null || c.largeur() <= 0 ? 1 : c.largeur();
  }

  private static String cellule(List<String> l, int i) {
    return l == null || i >= l.size() || l.get(i) == null ? "" : l.get(i);
  }

  /** Paysage demandé, ou d'office au-delà de huit colonnes. */
  public static boolean paysage(Tableau t) {
    return t.paysage() != null ? t.paysage() : t.colonnes().size() > 8;
  }

  public static byte[] pdf(Etablissement etab, Tableau t) {
    boolean paysage = paysage(t);
    try (Pdf pdf = new Pdf(t.titre(), "CETIC de Mezesse")) {
      pdf.nouvellePage(paysage);
      float x0 = 24;
      float l = pdf.largeurPage() - 48;
      float bas = pdf.hauteurPage() - 30;
      List<Colonne> cols = t.colonnes();
      float somme = 0;
      for (Colonne c : cols) somme += poids(c);
      float[] largeurs = new float[cols.size()];
      for (int i = 0; i < cols.size(); i++) largeurs[i] = l * poids(cols.get(i)) / somme;
      float taille = cols.size() > 12 ? 6.2f : 7f;
      float h = 14;
      float he = 22;

      float[] y = {EnteteOfficiel.dessiner(pdf, etab, 22) + 4};
      pdf.ligne(t.titre().toUpperCase(Locale.ROOT), x0, y[0], l, Style.de(11).gras().couleur(Couleurs.ACCENT).centre());
      y[0] += 15;
      if (t.sousTitre() != null && !t.sousTitre().isBlank()) {
        pdf.ligne(t.sousTitre(), x0, y[0], l, Style.de(7.5f).couleur(Couleurs.GRIS).centre());
        y[0] += 12;
      }
      y[0] += 4;

      Runnable entete =
          () -> {
            float x = x0;
            for (int i = 0; i < cols.size(); i++) {
              pdf.cadre(x, y[0], largeurs[i], he, Couleurs.ENTETE);
              pdf.bloc(cols.get(i).titre(), x + 2, y[0] + 3.5f, largeurs[i] - 4, he - 4, aligne(Style.de(taille).gras(), cols.get(i)));
              x += largeurs[i];
            }
            y[0] += he;
          };
      entete.run();
      int k = 0;
      for (Ligne ligne : t.lignes()) {
        if (y[0] + h > bas) {
          pdf.nouvellePage(paysage);
          y[0] = 30;
          entete.run();
        }
        if (ligne.groupe() != null) {
          // Ligne de groupe (une classe, un niveau…) sur toute la largeur.
          pdf.cadre(x0, y[0], l, h, Couleurs.GROUPE);
          pdf.ligne(ligne.groupe(), x0 + 4, y[0] + 4, l - 8, Style.de(taille + 0.3f).gras().couleur(Couleurs.ACCENT));
          y[0] += h;
          k = 0;
          continue;
        }
        boolean gras = Boolean.TRUE.equals(ligne.gras());
        // Un texte trop long passe sur plusieurs lignes (six au plus) : la ligne prend la hauteur de sa plus longue case.
        Style[] styles = new Style[cols.size()];
        int[] nbLignes = new int[cols.size()];
        int max = 1;
        for (int i = 0; i < cols.size(); i++) {
          Style s = aligne(Style.de(taille), cols.get(i));
          if (gras || Boolean.TRUE.equals(cols.get(i).gras())) s = s.gras();
          styles[i] = s;
          nbLignes[i] = Math.min(6, Pdf.couper(cellule(ligne.c(), i), s, largeurs[i] - 6).size());
          max = Math.max(max, nbLignes[i]);
        }
        float hLigne = Pdf.hauteurLigne(Style.de(taille));
        float hRang = max == 1 ? h : max * hLigne + 7;
        if (y[0] + hRang > bas) {
          pdf.nouvellePage(paysage);
          y[0] = 30;
          entete.run();
        }
        float x = x0;
        for (int i = 0; i < cols.size(); i++) {
          pdf.cadre(x, y[0], largeurs[i], hRang, gras ? Couleurs.GROUPE : k % 2 == 1 ? "#f6f8fb" : null);
          String v = cellule(ligne.c(), i);
          if (nbLignes[i] <= 1) pdf.ligne(v, x + 3, y[0] + (max == 1 ? 4 : 3.5f), largeurs[i] - 6, styles[i]);
          else pdf.bloc(v, x + 3, y[0] + 3.5f, largeurs[i] - 6, hRang - 5, styles[i]);
          x += largeurs[i];
        }
        y[0] += hRang;
        k++;
      }
      if (t.lignes().isEmpty()) {
        pdf.ligne("Aucune ligne.", x0, y[0] + 8, l, Style.de(8).italique().centre());
        y[0] += 20;
      }
      if (t.signature() != null && !t.signature().isBlank()) {
        y[0] += 22;
        if (y[0] + 30 > bas) {
          pdf.nouvellePage(paysage);
          y[0] = 40;
        }
        pdf.ligne("Fait à Mezesse, le " + EnteteOfficiel.aujourdhui(), x0 + l - 200, y[0], 200, Style.de(8).centre());
        pdf.ligne(t.signature(), x0 + l - 200, y[0] + 13, 200, Style.de(8).gras().centre());
      }
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · " + t.titre() + " · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }

  private static Style aligne(Style s, Colonne c) {
    if ("droite".equals(c.align())) return s.droite();
    if ("centre".equals(c.align())) return s.centre();
    return s;
  }

  public static byte[] excel(Tableau t) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      String nomFeuille = t.titre().replaceAll("[\\\\/?*\\[\\]:]", " ");
      Sheet f = wb.createSheet(nomFeuille.length() > 31 ? nomFeuille.substring(0, 31) : nomFeuille);
      Font gras = wb.createFont();
      gras.setBold(true);
      CellStyle styleGras = wb.createCellStyle();
      styleGras.setFont(gras);
      XSSFCellStyle entete = wb.createCellStyle();
      entete.setFont(gras);
      entete.setWrapText(true);
      entete.setVerticalAlignment(VerticalAlignment.CENTER);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      XSSFCellStyle groupe = wb.createCellStyle();
      groupe.setFont(gras);
      groupe.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xEE, (byte) 0xF3, (byte) 0xF8}, null));
      groupe.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      CellStyle droite = wb.createCellStyle();
      droite.setAlignment(HorizontalAlignment.RIGHT);
      CellStyle decimal = wb.createCellStyle();
      decimal.setDataFormat(wb.createDataFormat().getFormat("0.00"));

      Cell c0 = f.createRow(0).createCell(0);
      c0.setCellValue(t.titre());
      c0.setCellStyle(styleGras);
      if (t.sousTitre() != null && !t.sousTitre().isBlank()) f.createRow(1).createCell(0).setCellValue(t.sousTitre());
      List<Colonne> cols = t.colonnes();
      int[] largeurs = new int[cols.size()];
      Row e = f.createRow(3);
      for (int i = 0; i < cols.size(); i++) {
        Cell c = e.createCell(i);
        c.setCellValue(cols.get(i).titre());
        c.setCellStyle(entete);
        largeurs[i] = Math.min(cols.get(i).titre().length() + 2, 18);
      }
      int r = 4;
      for (Ligne ligne : t.lignes()) {
        Row row = f.createRow(r);
        if (ligne.groupe() != null) {
          Cell c = row.createCell(0);
          c.setCellValue(ligne.groupe());
          c.setCellStyle(groupe);
          if (cols.size() > 1) f.addMergedRegion(new CellRangeAddress(r, r, 0, cols.size() - 1));
          r++;
          continue;
        }
        for (int i = 0; i < cols.size(); i++) {
          String v = cellule(ligne.c(), i);
          Cell c = row.createCell(i);
          if (NOMBRE.matcher(v).matches()) {
            double d = Double.parseDouble(v.replace(',', '.'));
            c.setCellValue(d);
            if (v.contains(",") || v.contains(".")) c.setCellStyle(decimal);
          } else {
            c.setCellValue(v);
            if ("droite".equals(cols.get(i).align())) c.setCellStyle(droite);
          }
          if (Boolean.TRUE.equals(ligne.gras())) c.setCellStyle(styleGras);
          largeurs[i] = Math.max(largeurs[i], Math.min(v.length() + 2, 50));
        }
        r++;
      }
      for (int i = 0; i < cols.size(); i++) f.setColumnWidth(i, Math.max(6, largeurs[i]) * 256);
      f.createFreezePane(0, 4);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        wb.write(out);
        return out.toByteArray();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
