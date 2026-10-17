package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.BulletinPdf;
import cm.cetic.mezesse.document.BulletinPdf.Rappel;
import cm.cetic.mezesse.document.CartePdf;
import cm.cetic.mezesse.document.CartePdf.EleveCarte;
import cm.cetic.mezesse.document.ExcelEleves;
import cm.cetic.mezesse.document.ListePdf;
import cm.cetic.mezesse.document.ListePdf.LigneListe;
import cm.cetic.mezesse.document.Pdf;
import cm.cetic.mezesse.dto.DocumentDto.Fichier;
import cm.cetic.mezesse.dto.DocumentDto.SansPhoto;
import cm.cetic.mezesse.dto.DocumentDto.Selection;
import cm.cetic.mezesse.dto.DocumentDto.Verification;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.CompetenceVisee;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.CompetenceViseeRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.service.ResultatsService.DonneesClasse;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Baremes;
import cm.cetic.mezesse.util.Textes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Documents imprimables : bulletins, cartes scolaires, listes de classe et relevés de notes. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

  private final EtablissementService etablissement;
  private final ResultatsService resultats;
  private final ClasseRepository classes;
  private final InscriptionRepository inscriptions;
  private final CompetenceViseeRepository competences;
  private final StockageService stockage;

  /** Une requête « in » ne peut pas recevoir une liste vide. */
  private static List<String> nonVide(List<String> ids) {
    return ids.isEmpty() ? List.of("") : ids;
  }

  /** Bulletins d'une ou plusieurs classes, ou de quelques élèves : un seul PDF, une page par élève. */
  public Fichier bulletins(Selection s) {
    Periode p = Periode.lire(s.periode());
    if (s.classeIds().isEmpty() && s.inscriptionIds().isEmpty()) throw ApiException.invalide("Choisissez au moins une classe ou un élève.");
    Etablissement etab = etablissement.requis();
    Baremes baremes = resultats.baremes();
    // Pour chaque classe : null = toute la classe, sinon les élèves choisis.
    Map<String, Set<String>> parClasse = new LinkedHashMap<>();
    for (String c : s.classeIds()) parClasse.put(c, null);
    if (!s.inscriptionIds().isEmpty()) {
      for (Inscription i : inscriptions.activesParIds(s.inscriptionIds())) {
        if (parClasse.containsKey(i.getClasseId()) && parClasse.get(i.getClasseId()) == null) continue;
        parClasse.computeIfAbsent(i.getClasseId(), k -> new LinkedHashSet<>()).add(i.getId());
      }
    }
    List<Classe> liste = parClasse.isEmpty() ? List.of() : classes.parIdsTriees(parClasse.keySet());
    try (Pdf pdf = new Pdf("Bulletins – " + p.libelle(), "CETIC de Mezesse")) {
      for (Classe c : liste) {
        DonneesClasse donnees = resultats.resultatsClasse(c.getId(), p, baremes);
        List<Rappel> rappels = new ArrayList<>();
        for (Periode sp : p.sousPeriodes()) rappels.add(new Rappel(sp, resultats.resultatsClasse(c.getId(), sp, baremes).resultats()));
        Map<String, String> comp = new HashMap<>();
        if (p.estSequence()) {
          for (CompetenceVisee cv : competences.deLaClasse(c.getId(), c.getAnneeId(), p.numero())) comp.put(cv.getMatiereId(), cv.getTexte());
        }
        Set<String> selection = parClasse.get(c.getId());
        List<Inscription> aImprimer = donnees.inscriptions().stream().filter(i -> selection == null || selection.contains(i.getId())).toList();
        Map<String, byte[]> photos = new HashMap<>();
        for (Inscription i : aImprimer) {
          String cle = i.getEleve().getPhotoCle();
          byte[] b = cle == null ? null : stockage.lire(cle);
          if (b != null) photos.put(i.getEleveId(), b);
        }
        BulletinPdf.Contexte ctx = new BulletinPdf.Contexte(etab, donnees, rappels, comp, photos);
        for (Inscription i : aImprimer) {
          pdf.nouvellePage();
          BulletinPdf.dessiner(pdf, ctx, i.getId());
        }
      }
      if (pdf.nombrePages() == 0) throw ApiException.invalide("Aucun élève actif à imprimer.");
      String nom =
          liste.size() == 1 ? "bulletins-" + Textes.nomFichier(liste.get(0).getNom()) + "-" + p.name() : "bulletins-" + p.name() + "-" + liste.size() + "-classes";
      return new Fichier(pdf.enOctets(), nom, 0);
    }
  }

  // ---------- Cartes scolaires (RG-31) ----------

  private List<Inscription> elevesChoisis(Selection s) {
    if (s.classeIds().isEmpty() && s.inscriptionIds().isEmpty()) throw ApiException.invalide("Choisissez au moins une classe ou un élève.");
    return inscriptions.selection(nonVide(s.classeIds()), nonVide(s.inscriptionIds()));
  }

  /** Vérification avant impression : la photo est obligatoire sur la carte. */
  public Verification verification(Selection s) {
    List<Inscription> eleves = elevesChoisis(s);
    return new Verification(
        eleves.size(),
        eleves.stream()
            .filter(i -> i.getEleve().getPhotoCle() == null)
            .map(i -> new SansPhoto(i.getId(), i.getEleveId(), i.getEleve().getNom(), i.getEleve().getPrenoms(), i.getEleve().getMatricule(), i.getClasse().getNom()))
            .toList());
  }

  /** Un seul PDF, dix cartes par page A4. Les élèves sans photo (ou dont la photo est introuvable) sont écartés. */
  public Fichier cartes(Selection s) {
    List<Inscription> eleves = elevesChoisis(s);
    Etablissement etab = etablissement.requis();
    List<EleveCarte> cartes = new ArrayList<>();
    for (Inscription i : eleves) {
      String cle = i.getEleve().getPhotoCle();
      byte[] photo = cle == null ? null : stockage.lire(cle);
      if (photo != null) cartes.add(new EleveCarte(i.getEleve(), i.getClasse().getNom(), photo));
    }
    if (cartes.isEmpty()) {
      throw ApiException.invalide("Aucun des élèves choisis n'a de photo : ajoutez-les depuis leur fiche avant d'imprimer les cartes.");
    }
    List<String> annees = eleves.stream().map(i -> i.getAnnee().getLibelle()).distinct().toList();
    List<String> nomsClasses = eleves.stream().map(i -> i.getClasse().getNom()).distinct().toList();
    String nom = nomsClasses.size() == 1 ? "cartes-" + Textes.nomFichier(nomsClasses.get(0)) : "cartes-scolaires-" + cartes.size() + "-eleves";
    return new Fichier(CartePdf.generer(etab, String.join(" / ", annees), cartes), nom, eleves.size() - cartes.size());
  }

  // ---------- Listes et relevés ----------

  /** Liste des élèves d'une classe, en PDF ou en Excel. */
  public Fichier liste(String classeId, boolean excel) {
    Classe classe = classes.findById(classeId).orElseThrow(() -> ApiException.introuvable("Classe"));
    List<Inscription> actives = inscriptions.activesDeLaClasse(classeId);
    String annee = classe.getAnnee().getLibelle();
    String nom = "liste-" + Textes.nomFichier(classe.getNom()) + "-" + annee;
    String pp = classe.getProfPrincipal() == null ? null : classe.getProfPrincipal().nomComplet();
    if (excel) return new Fichier(ExcelEleves.liste(classe.getNom(), annee, pp, actives), nom, 0);
    byte[] pdf =
        ListePdf.generer(
            etablissement.requis(), classe.getNom(), annee, pp, actives.stream().map(i -> new LigneListe(i.getEleve(), i.isRedoublant())).toList());
    return new Fichier(pdf, nom, 0);
  }

  /** Relevé des notes d'une classe sur une période (Excel). */
  public Fichier releve(String classeId, Periode p) {
    DonneesClasse d = resultats.resultatsClasse(classeId, p);
    return new Fichier(ExcelEleves.releve(d, p), "releve-" + Textes.nomFichier(d.classe().getNom()) + "-" + p.name(), 0);
  }
}
