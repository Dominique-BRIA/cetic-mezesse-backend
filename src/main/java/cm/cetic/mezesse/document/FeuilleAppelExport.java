package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.dto.AppelDto.Case;
import cm.cetic.mezesse.dto.AppelDto.EleveVue;
import cm.cetic.mezesse.dto.AppelDto.Personne;
import cm.cetic.mezesse.dto.AppelDto.SeanceVue;
import cm.cetic.mezesse.dto.AppelDto.Vue;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.service.BilanAbsences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

/**
 * Feuille d'appel d'une classe pour un jour : élèves en lignes, séances en colonnes, absences marquées (A : absent,
 * J : absence justifiée). Une feuille pas encore enregistrée sort vide, à remplir à la main.
 */
public final class FeuilleAppelExport {

  private FeuilleAppelExport() {}

  private static final String[] JOURS = {"", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
  private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

  public static String jour(Vue v) {
    return JOURS[v.date().getDayOfWeek().getValue()] + " " + EnteteOfficiel.dateFr(v.date());
  }

  private static String cle(String seanceId, String inscriptionId) {
    return seanceId + "|" + inscriptionId;
  }

  private static Set<String> cles(List<Case> l) {
    Set<String> s = new HashSet<>();
    if (l != null) for (Case c : l) s.add(cle(c.seanceId(), c.inscriptionId()));
    return s;
  }

  private static boolean annulee(SeanceVue s) {
    return "ANNULEE".equals(s.statut());
  }

  private static String nom(Personne p) {
    return p == null ? "" : p.prenom() + " " + p.nom();
  }

  /** Qui assure la séance, ou ce qui l'en empêche. */
  private static String assurePar(SeanceVue s) {
    if (annulee(s)) return "Annulée";
    if (s.remplacant() != null) return "Remplacé par " + nom(s.remplacant());
    if (s.enseignantAbsent()) return "Enseignant absent";
    return s.enseignant() == null ? "" : s.enseignant().nom();
  }

  /** Marque d'une case : « J » justifiée, « A » absent, « » présent ; null pour une séance annulée. */
  private static String marque(SeanceVue s, EleveVue e, Set<String> abs, Set<String> just) {
    if (annulee(s)) return null;
    if (s.id() == null) return "";
    String k = cle(s.id(), e.inscriptionId());
    if (!abs.contains(k)) return "";
    return just.contains(k) ? "J" : "A";
  }

  private static int minutesAbsent(Vue v, EleveVue e, Set<String> abs) {
    int m = 0;
    for (SeanceVue s : v.seances()) if (s.id() != null && !annulee(s) && abs.contains(cle(s.id(), e.inscriptionId()))) m += s.duree();
    return m;
  }

  /** « Appel enregistré le … par … » ou « pas encore enregistré ». */
  private static String etat(Vue v) {
    if (v.fiche() == null) return "Appel pas encore enregistré : feuille à remplir.";
    String s = "Appel enregistré le " + HEURE.format(v.fiche().creeLe().atZone(EnteteOfficiel.CAMEROUN));
    if (v.fiche().creePar() != null) s += " par " + v.fiche().creePar();
    if (v.fiche().modifieLe() != null) s += " · corrigé le " + HEURE.format(v.fiche().modifieLe().atZone(EnteteOfficiel.CAMEROUN));
    return s;
  }

  private static int elevesAbsents(Vue v, Set<String> abs) {
    int n = 0;
    for (EleveVue e : v.eleves()) if (minutesAbsent(v, e, abs) > 0) n++;
    return n;
  }

  public static byte[] pdf(Etablissement etab, String titre, Vue v) {
    Set<String> abs = cles(v.absences());
    Set<String> just = cles(v.justifiees());
    try (Pdf pdf = new Pdf(titre, "CETIC de Mezesse")) {
      float x0 = 24;
      float l = Pdf.LARGEUR - 48;
      pdf.nouvellePage();
      float[] y = {EnteteOfficiel.dessiner(pdf, etab, 22) + 4};
      pdf.ligne(titre.toUpperCase(Locale.ROOT), x0, y[0], l, Style.de(11).gras().couleur(Couleurs.ACCENT).centre());
      y[0] += 15;
      String resume = etat(v);
      if (v.fiche() != null) resume += " · " + elevesAbsents(v, abs) + " élève(s) absent(s)";
      pdf.ligne(resume, x0, y[0], l, Style.de(7.5f).couleur(Couleurs.GRIS).centre());
      y[0] += 16;

      List<SeanceVue> seances = v.seances();
      float lNum = 20;
      float lEleve = seances.size() > 6 ? 120 : 150;
      float lTotal = 42;
      float lSeance = seances.isEmpty() ? 0 : (l - lNum - lEleve - lTotal) / seances.size();
      float he = 50;
      // Feuille pas encore enregistrée : des lignes plus hautes, pour cocher à la main.
      float h = v.fiche() == null ? 19 : 14;
      Runnable entete =
          () -> {
            pdf.cadre(x0, y[0], lNum, he, Couleurs.ENTETE);
            pdf.ligne("N°", x0, y[0] + 21, lNum, Style.de(7).gras().centre());
            pdf.cadre(x0 + lNum, y[0], lEleve, he, Couleurs.ENTETE);
            pdf.ligne("Élève", x0 + lNum + 4, y[0] + 21, lEleve - 8, Style.de(7.5f).gras());
            float x = x0 + lNum + lEleve;
            for (SeanceVue s : seances) {
              pdf.cadre(x, y[0], lSeance, he, annulee(s) ? "#eceff3" : Couleurs.ENTETE);
              // Matière sur trois lignes au plus, puis l'horaire et qui assure la séance.
              pdf.bloc(s.matiere(), x + 2, y[0] + 3, lSeance - 4, 22, Style.de(6f).gras().centre());
              pdf.ligne(s.debut() + "–" + s.fin(), x + 2, y[0] + 28, lSeance - 4, Style.de(6.5f).centre());
              String a = assurePar(s);
              boolean alerte = annulee(s) || s.enseignantAbsent();
              pdf.ligneAjustee(a, x + 2, y[0] + 38, lSeance - 4, Style.de(6).couleur(alerte ? "#b3261e" : Couleurs.GRIS).centre());
              x += lSeance;
            }
            pdf.cadre(x, y[0], lTotal, he, Couleurs.ENTETE);
            pdf.ligne("Absent", x, y[0] + 21, lTotal, Style.de(7).gras().centre());
            y[0] += he;
          };
      entete.run();
      for (int k = 0; k < v.eleves().size(); k++) {
        if (y[0] + h > Pdf.HAUTEUR - 70) {
          pdf.nouvellePage();
          y[0] = 30;
          entete.run();
        }
        EleveVue e = v.eleves().get(k);
        String fond = e.renvoye() ? "#eceff3" : k % 2 == 1 ? "#f6f8fb" : null;
        pdf.cadre(x0, y[0], lNum, h, fond);
        pdf.ligne(String.valueOf(e.numero()), x0 + 2, y[0] + h / 2 - 3, lNum - 5, Style.de(7).couleur(Couleurs.GRIS).droite());
        pdf.cadre(x0 + lNum, y[0], lEleve, h, fond);
        String n = e.nom() + " " + e.prenoms() + (e.renvoye() ? " (renvoyé)" : "");
        pdf.ligneAjustee(n, x0 + lNum + 4, y[0] + h / 2 - 3, lEleve - 8, e.renvoye() ? Style.de(7).couleur(Couleurs.GRIS) : Style.de(7).gras());
        float x = x0 + lNum + lEleve;
        for (SeanceVue s : seances) {
          String m = marque(s, e, abs, just);
          pdf.cadre(x, y[0], lSeance, h, m == null ? "#eceff3" : fond);
          if (m != null && !m.isEmpty()) pdf.ligne(m, x, y[0] + 4, lSeance, Style.de(7.5f).gras().couleur("J".equals(m) ? "#1e764c" : "#b3261e").centre());
          x += lSeance;
        }
        int min = minutesAbsent(v, e, abs);
        pdf.cadre(x, y[0], lTotal, h, fond);
        if (min > 0) pdf.ligne(BilanAbsences.duree(min), x + 2, y[0] + 4, lTotal - 5, Style.de(7).gras().couleur("#b3261e").droite());
        y[0] += h;
      }
      // Ligne des absents par séance.
      if (v.fiche() != null && !seances.isEmpty()) {
        pdf.cadre(x0, y[0], lNum + lEleve, h, Couleurs.GROUPE);
        pdf.ligne("Absents", x0 + 4, y[0] + 4, lNum + lEleve - 8, Style.de(7).gras());
        float x = x0 + lNum + lEleve;
        for (SeanceVue s : seances) {
          int n = 0;
          if (s.id() != null && !annulee(s)) for (EleveVue e : v.eleves()) if (abs.contains(cle(s.id(), e.inscriptionId()))) n++;
          pdf.cadre(x, y[0], lSeance, h, Couleurs.GROUPE);
          if (!annulee(s)) pdf.ligne(String.valueOf(n), x, y[0] + 4, lSeance, Style.de(7).gras().centre());
          x += lSeance;
        }
        pdf.cadre(x, y[0], lTotal, h, Couleurs.GROUPE);
        y[0] += h;
      }
      y[0] += 8;
      pdf.ligne("A : absent · J : absence justifiée · case grisée : séance annulée.", x0, y[0], l, Style.de(6.5f).couleur(Couleurs.GRIS));
      y[0] += 22;
      if (y[0] + 30 > Pdf.HAUTEUR - 30) {
        pdf.nouvellePage();
        y[0] = 40;
      }
      pdf.ligne("Le Surveillant Général", x0 + l - 200, y[0], 200, Style.de(8).gras().centre());
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · " + titre + " · édité le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }

  public static byte[] excel(String titre, Vue v) {
    Set<String> abs = cles(v.absences());
    Set<String> just = cles(v.justifiees());
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet f = wb.createSheet("Feuille d'appel");
      Font gras = wb.createFont();
      gras.setBold(true);
      CellStyle styleGras = wb.createCellStyle();
      styleGras.setFont(gras);
      XSSFCellStyle entete = wb.createCellStyle();
      entete.setFont(gras);
      entete.setWrapText(true);
      entete.setAlignment(HorizontalAlignment.CENTER);
      entete.setVerticalAlignment(VerticalAlignment.CENTER);
      entete.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xDD, (byte) 0xE7, (byte) 0xF1}, null));
      entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      XSSFCellStyle centre = wb.createCellStyle();
      centre.setAlignment(HorizontalAlignment.CENTER);
      for (CellStyle s : new CellStyle[] {entete, centre}) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
      }
      Cell c0 = f.createRow(0).createCell(0);
      c0.setCellValue(titre);
      c0.setCellStyle(styleGras);
      f.createRow(1).createCell(0).setCellValue(etat(v) + " · A : absent, J : absence justifiée");
      Row e = f.createRow(3);
      String[] fixes = {"N°", "Élève", "Matricule"};
      for (int i = 0; i < fixes.length; i++) {
        Cell c = e.createCell(i);
        c.setCellValue(fixes[i]);
        c.setCellStyle(entete);
      }
      List<SeanceVue> seances = v.seances();
      for (int j = 0; j < seances.size(); j++) {
        SeanceVue s = seances.get(j);
        Cell c = e.createCell(3 + j);
        c.setCellValue(s.matiere() + "\n" + s.debut() + "–" + s.fin() + "\n" + assurePar(s));
        c.setCellStyle(entete);
        f.setColumnWidth(3 + j, 18 * 256);
      }
      Cell ct = e.createCell(3 + seances.size());
      ct.setCellValue("Absent (min)");
      ct.setCellStyle(entete);
      e.setHeightInPoints(46);
      for (int k = 0; k < v.eleves().size(); k++) {
        EleveVue x = v.eleves().get(k);
        Row r = f.createRow(4 + k);
        r.createCell(0).setCellValue(x.numero());
        r.createCell(1).setCellValue(x.nom() + " " + x.prenoms() + (x.renvoye() ? " (renvoyé)" : ""));
        r.createCell(2).setCellValue(x.matricule() == null ? "" : x.matricule());
        for (int j = 0; j < seances.size(); j++) {
          String m = marque(seances.get(j), x, abs, just);
          Cell c = r.createCell(3 + j);
          c.setCellValue(m == null ? "—" : m);
          c.setCellStyle(centre);
        }
        r.createCell(3 + seances.size()).setCellValue(minutesAbsent(v, x, abs));
      }
      f.setColumnWidth(0, 5 * 256);
      f.setColumnWidth(1, 30 * 256);
      f.setColumnWidth(2, 14 * 256);
      f.setColumnWidth(3 + seances.size(), 12 * 256);
      f.createFreezePane(2, 4);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        wb.write(out);
        return out.toByteArray();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
