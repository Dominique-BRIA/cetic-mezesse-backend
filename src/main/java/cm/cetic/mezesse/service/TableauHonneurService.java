package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.TableauHonneurPdf;
import cm.cetic.mezesse.dto.DocumentDto.Fichier;
import cm.cetic.mezesse.dto.TableauHonneurDto.Classe;
import cm.cetic.mezesse.dto.TableauHonneurDto.Eleve;
import cm.cetic.mezesse.dto.TableauHonneurDto.Tableau;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.service.ResultatsService.DonneesClasse;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Baremes;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.util.Nombres;
import cm.cetic.mezesse.util.Textes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tableau d'honneur d'une période : dans chaque classe, les élèves dont la moyenne (arrondie au
 * centième) atteint le seuil réglé dans les paramètres (12,00 par défaut), dans l'ordre du classement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableauHonneurService {

  private final ResultatsService resultats;
  private final ClasseRepository classes;
  private final AnneeScolaireRepository annees;
  private final EtablissementService etablissement;
  private final TitreDirection titre;

  /** Élève au tableau d'honneur si sa moyenne, arrondie au centième, atteint le seuil. */
  public static boolean auTableau(Double moyenne, double seuil) {
    return moyenne != null && Nombres.arrondi(moyenne) >= seuil;
  }

  /** Moyenne atteinte, et pas plus d'heures d'absence non justifiées sur la période que permis (null : pas de limite). */
  public static boolean auTableau(ResultatEleve r, double seuil, Integer heuresRefus) {
    return auTableau(r.moyenne(), seuil) && !refusePourAbsences(r, heuresRefus);
  }

  /** Refusé du tableau d'honneur : heures d'absence non justifiées de la période atteignant la limite. */
  public static boolean refusePourAbsences(ResultatEleve r, Integer heuresRefus) {
    return heuresRefus != null && r.minutesNonJustifiees() >= heuresRefus * 60;
  }

  public Tableau tableau(String anneeId, Periode p, String classeId) {
    AnneeScolaire annee = annees.findById(anneeId).orElseThrow(() -> ApiException.introuvable("Année scolaire"));
    double seuil = etablissement.requis().getSeuilTableauHonneur().doubleValue();
    Integer heuresRefus = etablissement.requis().getHeuresRefusTableauHonneur();
    int refuses = 0;
    List<cm.cetic.mezesse.entity.Classe> liste =
        classeId == null
            ? classes.findByAnnee_IdOrderByNiveauAscNomAsc(anneeId)
            : List.of(classes.findById(classeId).orElseThrow(() -> ApiException.introuvable("Classe")));
    Baremes b = resultats.baremes();
    List<Classe> parClasse = new ArrayList<>();
    for (cm.cetic.mezesse.entity.Classe c : liste) {
      DonneesClasse d = resultats.resultatsClasse(c.getId(), p, b);
      List<Eleve> eleves = new ArrayList<>();
      for (ResultatEleve r : d.resultats().eleves()) {
        if (!auTableau(r.moyenne(), seuil)) continue;
        if (refusePourAbsences(r, heuresRefus)) {
          refuses++;
          continue;
        }
        var e = d.inscription(r.inscriptionId()).getEleve();
        eleves.add(
            new Eleve(
                r.inscriptionId(), e.getId(), e.getMatricule(), e.getNom(), e.getPrenoms(), e.getSexe(), e.getDateNaissance(), e.getLieuNaissance(),
                r.moyenne(), r.rang()));
      }
      // Ordre du classement (un seul premier), puis la moyenne.
      eleves.sort(Comparator.comparingInt((Eleve x) -> x.rang() == null ? 999 : x.rang().rang()).thenComparing(Comparator.comparingDouble(Eleve::moyenne).reversed()));
      int classes = (int) d.resultats().eleves().stream().filter(r -> r.moyenne() != null).count();
      String pp = c.getProfPrincipal() == null ? null : c.getProfPrincipal().getPrenom() + " " + Textes.majuscules(c.getProfPrincipal().getNom());
      parClasse.add(new Classe(c.getId(), c.getNom(), classes, d.resultats().profil().moyenneGenerale(), pp, eleves));
    }
    int total = parClasse.stream().mapToInt(c -> c.eleves().size()).sum();
    return new Tableau(seuil, p, annee.getLibelle(), total, parClasse, heuresRefus, refuses);
  }

  /** Liste du tableau d'honneur (PDF), ou un certificat par élève. */
  public Fichier document(String anneeId, Periode p, String classeId, boolean certificats) {
    Tableau t = tableau(anneeId, p, classeId);
    if (t.total() == 0) {
      throw ApiException.invalide("Aucun élève n'atteint " + Nombres.fr(t.seuil()) + " de moyenne pour cette période.");
    }
    Etablissement etab = etablissement.requis();
    String suffixe = classeId != null && t.classes().size() == 1 ? Textes.nomFichier(t.classes().get(0).nom()) + "-" : "";
    byte[] contenu = certificats ? TableauHonneurPdf.certificats(etab, t, titre.avecArticleEnFonction()) : TableauHonneurPdf.liste(etab, t);
    return new Fichier(contenu, (certificats ? "certificats-tableau-honneur-" : "tableau-honneur-") + suffixe + p.name(), 0);
  }
}
