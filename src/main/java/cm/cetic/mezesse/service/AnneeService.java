package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.AnneeDto.Comptes;
import cm.cetic.mezesse.dto.AnneeDto.Creation;
import cm.cetic.mezesse.dto.AnneeDto.Ligne;
import cm.cetic.mezesse.dto.AnneeDto.SequenceVue;
import cm.cetic.mezesse.dto.AnneeDto.Vue;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.GroupeMatiere;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.GroupeMatiereRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.SequenceRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Années scolaires : création (avec reprise de l'organisation), activation, ouverture de la saisie. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnneeService {

  private final AnneeScolaireRepository annees;
  private final SequenceRepository sequences;
  private final ClasseRepository classes;
  private final GroupeMatiereRepository groupes;
  private final MatiereRepository matieres;
  private final InscriptionRepository inscriptions;
  private final JournalDecisions journal;
  private final NotificationService notifications;

  public List<Ligne> liste() {
    List<AnneeScolaire> toutes = annees.findAllByOrderByLibelleDesc();
    Map<String, List<SequenceVue>> parAnnee = new HashMap<>();
    if (!toutes.isEmpty()) {
      for (Sequence s : sequences.findByAnnee_IdInOrderByNumeroAsc(toutes.stream().map(AnneeScolaire::getId).toList())) {
        parAnnee.computeIfAbsent(s.getAnneeId(), k -> new java.util.ArrayList<>()).add(SequenceVue.de(s));
      }
    }
    Map<String, Long> nbClasses = Compte.enMap(classes.compterParAnnee());
    Map<String, Long> nbInscriptions = Compte.enMap(inscriptions.compterParAnnee());
    return toutes.stream()
        .map(
            a ->
                new Ligne(
                    a.getId(), a.getLibelle(), a.isActive(), a.getCreeLe(), parAnnee.getOrDefault(a.getId(), List.of()),
                    new Comptes(nbClasses.getOrDefault(a.getId(), 0L), nbInscriptions.getOrDefault(a.getId(), 0L))))
        .toList();
  }

  /**
   * Création d'une année : 6 séquences, et éventuellement reprise de la structure (groupes, classes,
   * matières, professeurs) d'une année précédente.
   */
  @Transactional
  public Vue creer(UtilisateurConnecte moi, Creation d) {
    String[] bornes = d.libelle().split("-");
    if (Integer.parseInt(bornes[1]) != Integer.parseInt(bornes[0]) + 1) {
      throw ApiException.conflit("L'année de fin doit suivre l'année de début.");
    }
    String activeAvant = annees.findFirstByActiveTrue().map(AnneeScolaire::getId).orElse(null);
    AnneeScolaire a = new AnneeScolaire();
    a.setLibelle(d.libelle());
    for (int n = 1; n <= 6; n++) {
      Sequence s = new Sequence();
      s.setAnnee(a);
      s.setNumero(n);
      s.setTrimestre((n + 1) / 2);
      a.getSequences().add(s);
    }
    annees.saveAndFlush(a);

    String reprise = null;
    if (d.copierDepuisId() != null) {
      AnneeScolaire source = annees.findById(d.copierDepuisId()).orElseThrow(() -> ApiException.introuvable("Année source"));
      reprise = source.getLibelle();
      copierOrganisation(source, a);
    }
    if (d.activer()) activer(a);
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "annee.creer",
        "Création de l'année scolaire " + d.libelle() + (reprise != null ? " (organisation reprise de " + reprise + ")" : "")
            + (d.activer() ? ", déclarée année en cours" : ""),
        "/annees", Donnees.objet("id", a.getId(), "activeAvantId", d.activer() ? activeAvant : null));
    return Vue.de(a);
  }

  /** Groupes, classes et matières de l'année source, avec leurs professeurs. */
  private void copierOrganisation(AnneeScolaire source, AnneeScolaire cible) {
    Map<String, GroupeMatiere> nouveaux = new HashMap<>();
    for (GroupeMatiere g : groupes.findByAnnee_IdOrderByOrdreAsc(source.getId())) {
      GroupeMatiere n = new GroupeMatiere();
      n.setAnnee(cible);
      n.setNom(g.getNom());
      n.setNomEn(g.getNomEn());
      n.setOrdre(g.getOrdre());
      nouveaux.put(g.getId(), groupes.save(n));
    }
    for (Classe c : classes.deLAnnee(source.getId())) {
      Classe n = new Classe();
      n.setAnnee(cible);
      n.setNom(c.getNom());
      n.setNomEn(c.getNomEn());
      n.setNiveau(c.getNiveau());
      n.setNumero(c.getNumero());
      n.setSpecialite(c.getSpecialite());
      n.setProfPrincipal(c.getProfPrincipal());
      classes.save(n);
      for (GroupeMatiere g : c.getGroupes()) nouveaux.get(g.getId()).getClasses().add(n);
      for (Matiere m : c.getMatieres()) {
        Matiere copie = new Matiere();
        copie.setClasse(n);
        copie.setGroupe(nouveaux.get(m.getGroupeId()));
        copie.setNom(m.getNom());
        copie.setNomEn(m.getNomEn());
        copie.setCoefficient(m.getCoefficient());
        copie.setOrdre(m.getOrdre());
        copie.setProfesseur(m.getProfesseur());
        matieres.save(copie);
      }
    }
  }

  @Transactional
  public void activer(UtilisateurConnecte moi, String id) {
    AnneeScolaire a = annees.findById(id).orElseThrow(() -> ApiException.introuvable("Année scolaire"));
    String activeAvant = annees.findFirstByActiveTrueAndIdNot(id).map(AnneeScolaire::getId).orElse(null);
    activer(a);
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "annee.activer", "Activation de l'année scolaire " + a.getLibelle(), "/annees",
        Donnees.objet("activeAvantId", activeAvant));
  }

  /** Une seule année est en cours à la fois. */
  void activer(AnneeScolaire a) {
    annees.desactiverSauf(a.getId());
    AnneeScolaire rechargee = annees.findById(a.getId()).orElseThrow();
    rechargee.setActive(true);
    a.setActive(true);
  }

  /** Ouverture ou fermeture de la saisie des notes d'une séquence ; les professeurs sont prévenus à l'ouverture. */
  @Transactional
  public SequenceVue saisie(UtilisateurConnecte moi, String sequenceId, boolean ouverte) {
    Sequence s = sequences.findById(sequenceId).orElseThrow(() -> ApiException.introuvable("Séquence"));
    boolean avant = s.isSaisieOuverte();
    s.setSaisieOuverte(ouverte);
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, ouverte ? "sequence.ouvrir" : "sequence.fermer",
        (ouverte ? "Ouverture" : "Fermeture") + " de la saisie des notes de la séquence " + s.getNumero() + " (" + s.getAnnee().getLibelle() + ")",
        "/annees", Donnees.objet("id", s.getId(), "avant", avant));
    if (ouverte && !avant) notifications.notifierOuvertureSequence(s);
    return SequenceVue.de(s);
  }
}
