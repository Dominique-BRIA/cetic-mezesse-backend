package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.DecisionDto.Page;
import cm.cetic.mezesse.dto.DecisionDto.Personne;
import cm.cetic.mezesse.dto.DecisionDto.Totaux;
import cm.cetic.mezesse.dto.DecisionDto.Vue;
import cm.cetic.mezesse.entity.Decision;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.StatutDecision;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.DecisionRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnnulationService.Resultat;
import cm.cetic.mezesse.util.Horloge;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Journal des décisions du SG, réservé au Directeur (rôle PROVISEUR, RG-30). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DecisionService {

  private static final int PAR_PAGE = 50;

  private final DecisionRepository decisions;
  private final UtilisateurRepository utilisateurs;
  private final AnnulationService annulation;
  private final NotificationService notifications;
  private final StockageService stockage;

  private static Personne personne(Utilisateur u) {
    return u == null ? null : new Personne(u.getId(), u.getNom(), u.getPrenom());
  }

  private static Vue presenter(Decision d) {
    Map<String, Object> a = d.getAnnulation();
    return new Vue(
        d.getId(), d.getDomaine(), d.getAction(), d.getLibelle(), d.getLien(), d.getAuteur().getId(), d.getCreeLe(), d.getStatut(),
        d.getDesapprouveeLe(), d.getDesapprouveePar() == null ? null : d.getDesapprouveePar().getId(), d.getMotifDesapprobation(), d.isAnnulee(),
        personne(d.getAuteur()), personne(d.getDesapprouveePar()), JournalDecisions.limiteDesapprobation(d.getCreeLe()),
        d.getStatut() == StatutDecision.APPROUVEE && JournalDecisions.dansLeDelai(d.getCreeLe(), Instant.now()),
        a == null ? null : AnnulationService.EFFETS.get(d.getAction()),
        a == null || a.get("effet") == null ? null : a.get("effet").toString());
  }

  /**
   * Décisions les plus récentes d'abord, 50 par page.
   *
   * @param statut APPROUVEE, DESAPPROUVEE ou EN_DELAI (approuvées encore désapprouvables)
   * @param apres curseur : id de la dernière décision reçue
   */
  public Page liste(DomaineDecision domaine, String statut, String q, String apres) {
    Instant debutDelai = Instant.now().minus(Duration.ofDays(JournalDecisions.DELAI_DESAPPROBATION_JOURS));
    List<Specification<Decision>> filtres = new ArrayList<>();
    if (domaine != null) filtres.add((r, query, cb) -> cb.equal(r.get("domaine"), domaine));
    if ("EN_DELAI".equals(statut)) {
      filtres.add((r, query, cb) -> cb.and(cb.equal(r.get("statut"), StatutDecision.APPROUVEE), cb.greaterThanOrEqualTo(r.get("creeLe"), debutDelai)));
    } else if (statut != null) {
      StatutDecision s;
      try {
        s = StatutDecision.valueOf(statut);
      } catch (IllegalArgumentException e) {
        throw ApiException.invalide("statut : APPROUVEE, DESAPPROUVEE ou EN_DELAI");
      }
      filtres.add((r, query, cb) -> cb.equal(r.get("statut"), s));
    }
    if (q != null) {
      String motif = "%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      filtres.add((r, query, cb) -> cb.like(cb.lower(r.get("libelle")), motif, '\\'));
    }
    if (apres != null) {
      Decision curseur = decisions.findById(apres).orElseThrow(() -> ApiException.introuvable("Décision"));
      filtres.add(
          (r, query, cb) ->
              cb.or(
                  cb.lessThan(r.get("creeLe"), curseur.getCreeLe()),
                  cb.and(cb.equal(r.get("creeLe"), curseur.getCreeLe()), cb.lessThan(r.get("id"), curseur.getId()))));
    }
    Specification<Decision> spec = Specification.allOf(filtres);
    List<Decision> lignes =
        decisions.findBy(spec, f -> f.sortBy(Sort.by(Sort.Order.desc("creeLe"), Sort.Order.desc("id"))).limit(PAR_PAGE + 1).all());
    List<Vue> page = lignes.stream().limit(PAR_PAGE).map(DecisionService::presenter).toList();
    return new Page(
        page,
        lignes.size() > PAR_PAGE ? lignes.get(PAR_PAGE - 1).getId() : null,
        new Totaux(
            decisions.countByStatutAndCreeLeGreaterThanEqual(StatutDecision.APPROUVEE, debutDelai), decisions.countByStatut(StatutDecision.DESAPPROUVEE)),
        JournalDecisions.DELAI_DESAPPROBATION_JOURS);
  }

  /**
   * Désapprouver défait l'action dans la même transaction : si elle ne peut plus être défaite, rien
   * n'est enregistré et le Proviseur reçoit la raison. La désapprobation est définitive. Le SG est
   * prévenu.
   */
  @Transactional
  public Vue desapprouver(UtilisateurConnecte moi, String id, String motif) {
    Decision d = decisions.findById(id).orElseThrow(() -> ApiException.introuvable("Décision"));
    if (d.getStatut() == StatutDecision.DESAPPROUVEE) throw ApiException.conflit("Cette décision est déjà désapprouvée.");
    if (!JournalDecisions.dansLeDelai(d.getCreeLe(), Instant.now())) {
      throw ApiException.conflit(
          "Le délai de " + JournalDecisions.DELAI_DESAPPROBATION_JOURS / 7 + " semaines est dépassé : la décision reste approuvée.");
    }
    Map<String, Object> donnees = d.getAnnulation();
    String action = d.getAction();
    String libelle = d.getLibelle();
    String lien = d.getLien();
    String auteurId = d.getAuteur().getId();
    Resultat resultat = donnees == null ? null : annulation.annuler(action, donnees);

    // L'annulation peut avoir vidé le contexte de persistance : la décision est relue avant mise à jour.
    Decision maj = decisions.findById(id).orElseThrow();
    maj.setStatut(StatutDecision.DESAPPROUVEE);
    maj.setDesapprouveeLe(Horloge.maintenant());
    maj.setDesapprouveePar(utilisateurs.getReferenceById(moi.id()));
    maj.setMotifDesapprobation(motif);
    maj.setAnnulee(resultat != null);
    if (resultat != null) {
      Map<String, Object> avecEffet = new LinkedHashMap<>(donnees);
      avecEffet.put("effet", resultat.effet());
      maj.setAnnulation(avecEffet);
    }
    decisions.saveAndFlush(maj);
    notifications.notifier(
        List.of(auteurId), "Décision désapprouvée",
        TitreDirection.avecArticle(utilisateurs.findById(moi.id()).map(u -> u.getSexe()).orElse(null)) + " a désapprouvé : « " + libelle + " ». Motif : " + motif + (resultat != null ? " " + resultat.effet() : ""),
        resultat != null ? lien : null);

    // Les fichiers devenus inutiles (photos) ne sont retirés qu'une fois la transaction validée.
    List<String> fichiers = resultat == null ? List.of() : resultat.fichiersASupprimer();
    if (!fichiers.isEmpty()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              fichiers.forEach(stockage::supprimer);
            }
          });
    }
    return presenter(maj);
  }
}
