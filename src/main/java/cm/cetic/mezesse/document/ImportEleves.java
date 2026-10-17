package cm.cetic.mezesse.document;

import cm.cetic.mezesse.dto.EleveDto.Identite;
import cm.cetic.mezesse.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Import en masse des élèves : modèle Excel à remplir, puis lecture du fichier rempli (Excel ou CSV). */
@Component
public class ImportEleves {

  public static final List<String> COLONNES =
      List.of(
          "Nom", "Prénoms", "Sexe (M/F)", "Date de naissance (JJ/MM/AAAA)", "Lieu de naissance", "Redoublant (Oui/Non)",
          "Matricule national", "Parent / tuteur", "Téléphone parent", "Adresse parent");

  /** Ligne du fichier (numéro de ligne Excel) et l'identité lue. */
  public record LigneLue(int numero, Identite identite) {}

  public byte[] modele() {
    try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet ws = wb.createSheet("Élèves");
      var gras = wb.createCellStyle();
      var police = wb.createFont();
      police.setBold(true);
      gras.setFont(police);
      Row entete = ws.createRow(0);
      for (int i = 0; i < COLONNES.size(); i++) {
        Cell c = entete.createCell(i);
        c.setCellValue(COLONNES.get(i));
        c.setCellStyle(gras);
        ws.setColumnWidth(i, 22 * 256);
      }
      String[] exemple = {"MBALLA", "Jean Pierre", "M", "14/03/2011", "Sangmélima", "Non", "", "MBALLA Paul", "699000000", "Mezesse"};
      Row r = ws.createRow(1);
      for (int i = 0; i < exemple.length; i++) r.createCell(i).setCellValue(exemple[i]);
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Création du modèle impossible", e);
    }
  }

  /** Lit les lignes du fichier (la première porte les en-têtes ; les lignes vides sont ignorées). */
  public List<LigneLue> lire(byte[] contenu, String nomFichier) {
    List<List<Object>> lignes = nomFichier != null && nomFichier.toLowerCase(Locale.ROOT).endsWith(".csv") ? lireCsv(contenu) : lireExcel(contenu);
    List<LigneLue> resultat = new ArrayList<>();
    for (int n = 1; n < lignes.size(); n++) {
      List<Object> l = lignes.get(n);
      if (texte(l, 0).isEmpty() && texte(l, 1).isEmpty()) continue;
      Identite id = new Identite();
      id.setNom(texte(l, 0));
      id.setPrenoms(texte(l, 1));
      String sexe = texte(l, 2).toUpperCase(Locale.ROOT);
      id.setSexe(sexe.isEmpty() ? null : sexe.substring(0, 1));
      LocalDate date = date(l.size() > 3 ? l.get(3) : null);
      id.setDateNaissance(date == null ? null : date.toString());
      id.setLieuNaissance(texte(l, 4));
      id.setRedoublant(Pattern.compile("^(o|y|1)", Pattern.CASE_INSENSITIVE).matcher(texte(l, 5)).find());
      id.setMatriculeNational(vide(texte(l, 6)));
      id.setParentNom(vide(texte(l, 7)));
      id.setParentTelephone(vide(texte(l, 8)));
      id.setParentAdresse(vide(texte(l, 9)));
      // Numéro affiché : celui de la ligne dans Excel (1 = en-têtes).
      resultat.add(new LigneLue(n + 1, id));
    }
    return resultat;
  }

  private static String vide(String s) {
    return s.isEmpty() ? null : s;
  }

  private static String texte(List<Object> ligne, int i) {
    if (i >= ligne.size() || ligne.get(i) == null) return "";
    Object v = ligne.get(i);
    return v instanceof LocalDate d ? d.toString() : v.toString().strip();
  }

  private static final Pattern DATE_FR = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");

  /** Date d'une cellule : date Excel, « 14/03/2011 » ou « 2011-03-14 ». */
  static LocalDate date(Object v) {
    if (v instanceof LocalDate d) return d;
    if (v == null) return null;
    String t = v.toString().strip();
    Matcher m = DATE_FR.matcher(t);
    try {
      if (m.matches()) return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
      return t.length() >= 10 ? LocalDate.parse(t.substring(0, 10)) : null;
    } catch (Exception e) {
      return null;
    }
  }

  private List<List<Object>> lireExcel(byte[] contenu) {
    try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
      if (wb.getNumberOfSheets() == 0) throw ApiException.invalide("Le fichier ne contient aucune feuille.");
      Sheet ws = wb.getSheetAt(0);
      DataFormatter format = new DataFormatter(Locale.FRANCE);
      FormulaEvaluator calcul = wb.getCreationHelper().createFormulaEvaluator();
      List<List<Object>> lignes = new ArrayList<>();
      for (int r = 0; r <= ws.getLastRowNum(); r++) {
        Row row = ws.getRow(r);
        List<Object> valeurs = new ArrayList<>();
        if (row != null) {
          for (int c = 0; c < COLONNES.size(); c++) {
            Cell cell = row.getCell(c);
            if (cell == null) valeurs.add(null);
            else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
              valeurs.add(cell.getDateCellValue().toInstant().atZone(ZoneOffset.UTC).toLocalDate());
            } else valeurs.add(format.formatCellValue(cell, calcul));
          }
        }
        lignes.add(valeurs);
      }
      return lignes;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.invalide("Fichier illisible : envoyez le modèle Excel (.xlsx) ou un fichier CSV.");
    }
  }

  /** CSV séparé par « ; » ou « , » (le plus fréquent sur la première ligne), guillemets acceptés. */
  private static List<List<Object>> lireCsv(byte[] contenu) {
    String texte = new String(contenu, StandardCharsets.UTF_8).replace("﻿", "");
    String premiere = texte.split("\\r?\\n", 2)[0];
    char sep = premiere.chars().filter(c -> c == ';').count() > premiere.chars().filter(c -> c == ',').count() ? ';' : ',';
    List<List<Object>> lignes = new ArrayList<>();
    List<Object> ligne = new ArrayList<>();
    StringBuilder champ = new StringBuilder();
    boolean guillemets = false;
    for (int i = 0; i < texte.length(); i++) {
      char c = texte.charAt(i);
      if (guillemets) {
        if (c == '"' && i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
          champ.append('"');
          i++;
        } else if (c == '"') guillemets = false;
        else champ.append(c);
      } else if (c == '"') guillemets = true;
      else if (c == sep) {
        ligne.add(champ.toString());
        champ.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') i++;
        ligne.add(champ.toString());
        champ.setLength(0);
        lignes.add(ligne);
        ligne = new ArrayList<>();
      } else champ.append(c);
    }
    if (champ.length() > 0 || !ligne.isEmpty()) {
      ligne.add(champ.toString());
      lignes.add(ligne);
    }
    return lignes;
  }
}
