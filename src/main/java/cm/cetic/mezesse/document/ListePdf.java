package cm.cetic.mezesse.document;

import cm.cetic.mezesse.document.Pdf.Couleurs;
import cm.cetic.mezesse.document.Pdf.Style;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Sexe;
import java.util.List;
import java.util.Locale;

/** Liste nominative des élèves d'une classe (PDF A4). */
public final class ListePdf {

  private ListePdf() {}

  public record LigneListe(Eleve eleve, boolean redoublant) {}

  private record Colonne(String titre, float largeur) {}

  public static byte[] generer(Etablissement etab, String classe, String annee, String profPrincipal, List<LigneListe> eleves) {
    try (Pdf pdf = new Pdf("Liste des élèves – " + classe, "CETIC de Mezesse")) {
      float x0 = 24;
      float l = Pdf.LARGEUR - 48;
      float[] largeurs = {22, 58, 70, 160, 26, 50, 70, 26, 0};
      float somme = 0;
      for (int i = 0; i < largeurs.length - 1; i++) somme += largeurs[i];
      largeurs[largeurs.length - 1] = l - somme;
      String[] titres = {"N°", "Matricule", "Mat. national", "Nom et prénoms", "Sexe", "Né(e) le", "À", "Red.", "Tél. parent"};
      float h = 15;

      pdf.nouvellePage();
      float[] y = {EnteteOfficiel.dessiner(pdf, etab, 22) + 4};
      pdf.ligne("LISTE DES ÉLÈVES – " + classe.toUpperCase(Locale.ROOT), x0, y[0], l, Style.de(11).gras().couleur(Couleurs.ACCENT).centre());
      y[0] += 15;
      long filles = eleves.stream().filter(e -> e.eleve().getSexe() == Sexe.F).count();
      pdf.ligne(
          "Année scolaire " + annee + "  ·  Effectif : " + eleves.size() + " (" + filles + " filles, " + (eleves.size() - filles) + " garçons)"
              + (profPrincipal != null ? "  ·  Professeur principal : " + profPrincipal : ""),
          x0, y[0], l, Style.de(7.5f).couleur(Couleurs.GRIS).centre());
      y[0] += 16;

      Runnable entete =
          () -> {
            float x = x0;
            for (int i = 0; i < titres.length; i++) {
              pdf.cadre(x, y[0], largeurs[i], h, Couleurs.ENTETE);
              pdf.ligne(titres[i], x + 3, y[0] + 4.5f, largeurs[i] - 6, Style.de(7).gras());
              x += largeurs[i];
            }
            y[0] += h;
          };
      entete.run();

      for (int i = 0; i < eleves.size(); i++) {
        if (y[0] + h > Pdf.HAUTEUR - 30) {
          pdf.nouvellePage();
          y[0] = 30;
          entete.run();
        }
        Eleve e = eleves.get(i).eleve();
        String[] valeurs = {
          String.valueOf(i + 1), e.getMatricule(), e.getMatriculeNational() == null ? "" : e.getMatriculeNational(), e.nomComplet(),
          e.getSexe().name(), EnteteOfficiel.dateFr(e.getDateNaissance()), e.getLieuNaissance(), eleves.get(i).redoublant() ? "Oui" : "Non",
          e.getParentTelephone() == null ? "" : e.getParentTelephone()
        };
        float x = x0;
        for (int k = 0; k < valeurs.length; k++) {
          pdf.cadre(x, y[0], largeurs[k], h, i % 2 == 1 ? "#f6f8fb" : null);
          Style s = Style.de(7.2f);
          pdf.ligne(valeurs[k], x + 3, y[0] + 4.5f, largeurs[k] - 6, k == 3 ? s.gras() : s);
          x += largeurs[k];
        }
        y[0] += h;
      }
      EnteteOfficiel.piedDePage(pdf, etab.getNomFr() + " · Liste " + classe + " · éditée le " + EnteteOfficiel.aujourdhui());
      return pdf.enOctets();
    }
  }
}
