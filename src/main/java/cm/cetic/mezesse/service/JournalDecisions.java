package cm.cetic.mezesse.service;

import cm.cetic.mezesse.entity.Decision;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.repository.DecisionRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Textes;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal des décisions (RG-30) : chaque décision du SG est approuvée d'office ; le Proviseur peut la
 * désapprouver pendant 4 semaines, après quoi elle reste approuvée. La décision est enregistrée dans
 * la transaction de l'action : elles sont validées ou annulées ensemble.
 */
@Service
@RequiredArgsConstructor
public class JournalDecisions {

  public static final int DELAI_DESAPPROBATION_JOURS = 28;

  private final DecisionRepository decisions;
  private final UtilisateurRepository utilisateurs;

  public static Instant limiteDesapprobation(Instant creeLe) {
    return creeLe.plus(Duration.ofDays(DELAI_DESAPPROBATION_JOURS));
  }

  public static boolean dansLeDelai(Instant creeLe, Instant maintenant) {
    return !maintenant.isAfter(limiteDesapprobation(creeLe));
  }

  /**
   * N'enregistre que les décisions du SG.
   *
   * @param annulation de quoi défaire l'action (voir AnnulationService)
   */
  @Transactional
  public void consigner(
      UtilisateurConnecte auteur,
      DomaineDecision domaine,
      String action,
      String libelle,
      String lien,
      Map<String, Object> annulation) {
    if (!auteur.estSurveillantGeneral()) return;
    Decision d = new Decision();
    d.setDomaine(domaine);
    d.setAction(action);
    d.setLibelle(Textes.couper(libelle, 1000));
    d.setLien(lien);
    d.setAnnulation(annulation);
    d.setAuteur(utilisateurs.getReferenceById(auteur.id()));
    decisions.save(d);
  }
}
