package cm.cetic.mezesse.document;

import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.service.ResultatsService.DonneesClasse;
import cm.cetic.mezesse.service.calcul.CalculResultats;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.LigneMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.service.calcul.Resultats.StatMatiere;
import cm.cetic.mezesse.util.Nombres;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Exports Excel : liste des élèves d'une classe et relevé de notes d'une période. */
public final class ExcelEleves {

  private ExcelEleves() {}

  private static CellStyle style(XSSFWorkbook wb, boolean gras, boolean italique, short taille) {
    CellStyle s = wb.createCellStyle();
    Font f = wb.createFont();
    f.setBold(gras);
    f.setItalic(italique);
    if (taille > 0) f.setFontHeightInPoints(taille);
    s.setFont(f);
    return s;
  }

  private static void ecrire(Row r, int col, Object v, CellStyle s) {
    Cell c = r.createCell(col);
    if (v instanceof Number n) c.setCellValue(n.doubleValue());
    else c.setCellValue(v == null ? "" : v.toString());
    if (s != null) c.setCellStyle(s);
  }

  private static Row ligne(Sheet ws, List<?> valeurs, CellStyle s) {
    Row r = ws.createRow(ws.getPhysicalNumberOfRows() == 0 ? 0 : ws.getLastRowNum() + 1);
    for (int i = 0; i < valeurs.size(); i++) ecrire(r, i, valeurs.get(i), s);
    return r;
  }

  private static byte[] octets(XSSFWorkbook wb) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] liste(String classe, String annee, String profPrincipal, List<Inscription> inscriptions) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet ws = wb.createSheet(WorkbookUtil.createSafeSheetName(classe.length() > 31 ? classe.substring(0, 31) : classe));
      ligne(ws, List.of("Liste des élèves – " + classe + " – " + annee), style(wb, true, false, (short) 13));
      ligne(ws, List.of("Effectif : " + inscriptions.size() + (profPrincipal != null ? " – Professeur principal : " + profPrincipal : "")), null);
      ligne(ws, List.of(), null);
      XSSFCellStyle entete = (XSSFCellStyle) style(wb, true, false, (short) 0);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      entete.setBorderBottom(BorderStyle.THIN);
      ligne(
          ws,
          List.of(
              "N°", "Matricule", "Matricule national", "Nom", "Prénoms", "Sexe", "Date de naissance", "Lieu de naissance", "Redoublant",
              "Parent / tuteur", "Téléphone parent", "Adresse parent"),
          entete);
      for (int k = 0; k < inscriptions.size(); k++) {
        Inscription i = inscriptions.get(k);
        Eleve e = i.getEleve();
        List<Object> valeurs = new ArrayList<>();
        valeurs.add(k + 1);
        valeurs.add(e.getMatricule());
        valeurs.add(e.getMatriculeNational() == null ? "" : e.getMatriculeNational());
        valeurs.add(e.getNom());
        valeurs.add(e.getPrenoms());
        valeurs.add(e.getSexe().name());
        valeurs.add(EnteteOfficiel.dateFr(e.getDateNaissance()));
        valeurs.add(e.getLieuNaissance());
        valeurs.add(i.isRedoublant() ? "Oui" : "Non");
        valeurs.add(e.getParentNom() == null ? "" : e.getParentNom());
        valeurs.add(e.getParentTelephone() == null ? "" : e.getParentTelephone());
        valeurs.add(e.getParentAdresse() == null ? "" : e.getParentAdresse());
        ligne(ws, valeurs, null);
      }
      int[] largeurs = {5, 13, 18, 18, 24, 6, 16, 18, 11, 22, 16, 22};
      for (int i = 0; i < largeurs.length; i++) ws.setColumnWidth(i, largeurs[i] * 256);
      ws.createFreezePane(0, 4);
      return octets(wb);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Object nombre(Double v) {
    return v == null ? "" : Nombres.arrondi(v);
  }

  /** Relevé des notes d'une classe sur une période, élèves dans l'ordre du classement. */
  public static byte[] releve(DonneesClasse d, Periode p) {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet ws = wb.createSheet(p.name());
      List<StatMatiere> matieres = d.resultats().matieres();
      ligne(
          ws, List.of("Relevé de notes – " + d.classe().getNom() + " – " + p.libelle() + " – " + d.classe().getAnnee().getLibelle()),
          style(wb, true, false, (short) 13));
      ligne(ws, List.of(), null);
      List<Object> titres = new ArrayList<>(List.of("Matricule", "Nom et prénoms", "Sexe"));
      matieres.forEach(s -> titres.add(s.matiere().nom() + " (coef " + s.matiere().coefficient() + ")"));
      titres.addAll(List.of("Total coef", "Total points", "Moyenne", "Rang"));
      CellStyle entete = style(wb, true, false, (short) 0);
      entete.setWrapText(true);
      entete.setVerticalAlignment(VerticalAlignment.CENTER);
      Row r = ligne(ws, titres, entete);
      r.setHeightInPoints(42);
      List<ResultatEleve> tries = new ArrayList<>(d.resultats().eleves());
      tries.sort(Comparator.comparingInt(e -> e.rang() == null ? 999 : e.rang().rang()));
      for (ResultatEleve e : tries) {
        Eleve el = d.inscription(e.inscriptionId()).getEleve();
        List<Object> valeurs = new ArrayList<>(List.of(el.getMatricule(), el.nomComplet(), el.getSexe().name()));
        for (LigneMatiere l : e.matieres()) valeurs.add(nombre(l.note()));
        valeurs.add(e.totalCoef());
        valeurs.add(Nombres.arrondi(e.totalPoints()));
        valeurs.add(nombre(e.moyenne()));
        valeurs.add(CalculResultats.formatRang(e.rang()));
        ligne(ws, valeurs, null);
      }
      ligne(ws, List.of(), null);
      List<Object> moyennes = new ArrayList<>(List.of("", "Moyenne de la classe", ""));
      matieres.forEach(s -> moyennes.add(nombre(s.moyenne())));
      ligne(ws, moyennes, style(wb, false, true, (short) 0));
      ws.setColumnWidth(0, 13 * 256);
      ws.setColumnWidth(1, 30 * 256);
      for (int c = 3; c < 3 + matieres.size() + 4; c++) ws.setColumnWidth(c, 12 * 256);
      ws.createFreezePane(2, 3);
      return octets(wb);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
