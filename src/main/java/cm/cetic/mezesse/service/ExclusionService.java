package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.EnteteOfficiel;
import cm.cetic.mezesse.dto.AbsencesDto.DeciderExclusion;
import cm.cetic.mezesse.dto.AbsencesDto.Resultat;
import cm.cetic.mezesse.entity.DecisionExclusion;
import cm.cetic.mezesse.entity.DecisionExclusion.Type;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.DecisionExclusionRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.RenvoiRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Textes;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Décision du Directeur (ou de la Directrice) quand un élève atteint les heures d'exclusion pour absences non
 * justifiées : exclusion temporaire (nombre de jours ouvrés choisi par le Directeur, puis retour en classe), exclusion
 * définitive (radié pour toujours : il figure parmi les élèves renvoyés et ne peut plus être réinscrit), ou sursis
 * (maintenu en classe ; le bulletin porte le blâme avec la mention du sursis).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExclusionService {

  private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final DecisionExclusionRepository decisions;
  private final InscriptionRepository inscriptions;
  private final RenvoiRepository renvois;
  private final UtilisateurRepository utilisateurs;
  private final BilanAbsences bilan;
  private final NotificationService notifications;
  private final cm.cetic.mezesse.repository.SequenceRepository sequences;

  /** Jour de reprise après une exclusion de {@code jours} jours ouvrés commencée le {@code debut} (week-ends sautés). */
  static LocalDate reprise(LocalDate debut, int jours) {
    LocalDate d = debut;
    int comptes = 0;
    while (true) {
      if (!AppelService.weekEnd(d)) {
        comptes++;
        if (comptes == jours) break;
      }
      d = d.plusDays(1);
    }
    d = d.plusDays(1);
    while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
    return d;
  }

  public Resultat decider(UtilisateurConnecte moi, DeciderExclusion d) {
    Inscription ins = inscriptions.findById(d.inscriptionId()).orElseThrow(() -> ApiException.introuvable("Inscription"));
    if (ins.getStatut() == StatutInscription.RENVOYE) throw ApiException.conflit("Cet élève est déjà renvoyé.");
    LocalDate aujourdhui = LocalDate.now(EnteteOfficiel.CAMEROUN);
    LocalDate date = d.date() == null ? aujourdhui : d.date();
    boolean renvoyeCeJour = renvois.desInscriptions(List.of(ins.getId())).stream().anyMatch(r -> AppelService.renvoyeLe(List.of(r), date));
    if (renvoyeCeJour) throw ApiException.conflit("Cet élève est déjà exclu à cette date.");
    Integer palier = BilanAbsences.palierExclusion(bilan.regles());
    int nj = bilan.heures(List.of(ins.getId()), List.of(1, 2, 3, 4, 5, 6)).getOrDefault(ins.getId(), BilanAbsences.Heures.RIEN).nonJustifiees();
    if (palier == null) throw ApiException.invalide("Aucune heure d'exclusion n'est réglée (Paramètres, sanctions pour absences).");
    if (nj < palier) {
      throw ApiException.invalide(
          ins.getEleve().nomComplet() + " a " + BilanAbsences.duree(nj) + " d'absences non justifiées : l'exclusion se décide à partir de " + BilanAbsences.duree(palier) + ".");
    }
    Type type = d.type();
    if (type == Type.TEMPORAIRE && (d.jours() == null || d.jours() < 1)) throw ApiException.invalide("Exclusion temporaire : indiquez le nombre de jours.");
    if (type != Type.SURSIS && AppelService.weekEnd(date)) throw ApiException.invalide("L'exclusion commence un jour de classe (du lundi au vendredi).");
    String motif = Textes.optionnel(d.motif());
    Utilisateur directeur = utilisateurs.getReferenceById(moi.id());

    DecisionExclusion x = new DecisionExclusion();
    x.setInscriptionId(ins.getId());
    x.setType(type);
    x.setJours(type == Type.TEMPORAIRE ? d.jours() : null);
    x.setDate(date);
    // Comme une feuille d'appel : la décision va sur les bulletins de la dernière séquence ouverte.
    cm.cetic.mezesse.entity.Sequence seq = AppelService.sequenceProposee(sequences.findByAnnee_IdOrderByNumeroAsc(ins.getClasse().getAnnee().getId()));
    if (seq == null) throw ApiException.conflit("L'année n'a pas de séquence.");
    x.setSequence(seq.getNumero());
    x.setMotif(motif);
    x.setMinutes(nj);
    x.setParId(moi.id());
    String resume;
    switch (type) {
      case TEMPORAIRE -> {
        // Un renvoi dont la fin est déjà connue : l'élève revient de lui-même le jour de la reprise.
        LocalDate retour = reprise(date, d.jours());
        Renvoi r = new Renvoi();
        r.setInscription(ins);
        r.setDate(date);
        r.setMotif("Exclusion temporaire de " + d.jours() + " jour" + (d.jours() > 1 ? "s" : "") + " pour absences non justifiées" + (motif == null ? "" : " : " + motif));
        r.setPar(directeur);
        r.setReintegreLe(retour.atStartOfDay(EnteteOfficiel.CAMEROUN).toInstant());
        r.setReintegrePar(directeur);
        r.setMotifReintegration("Fin de l'exclusion temporaire");
        x.setRenvoiId(renvois.save(r).getId());
        resume = "exclu(e) " + d.jours() + " jour" + (d.jours() > 1 ? "s" : "") + " à partir du " + JJ_MM.format(date) + ", retour le " + JJ_MM.format(retour);
      }
      case DEFINITIVE -> {
        ins.setStatut(StatutInscription.RENVOYE);
        Renvoi r = new Renvoi();
        r.setInscription(ins);
        r.setDate(date);
        r.setMotif("Exclusion définitive pour absences non justifiées" + (motif == null ? "" : " : " + motif));
        r.setPar(directeur);
        x.setRenvoiId(renvois.save(r).getId());
        resume = "exclu(e) définitivement de l'établissement à partir du " + JJ_MM.format(date);
      }
      default -> resume = "maintenu(e) en classe avec sursis (blâme sur le bulletin)";
    }
    decisions.save(x);
    List<String> sg = utilisateurs.findByRoleOrderByNomAscPrenomAsc(Role.SURVEILLANT_GENERAL).stream().filter(Utilisateur::isActif).map(Utilisateur::getId).toList();
    notifications.notifier(
        sg, "Décision d'exclusion",
        ins.getEleve().nomComplet() + " (" + ins.getClasse().getNom() + ") : " + resume + ". Décision de la direction.",
        "/absences/bilan?classe=" + ins.getClasse().getId());
    return new Resultat(ins.getEleve().nomComplet() + " : " + resume + ".", 1);
  }

  /** Annule une décision prise par erreur : le renvoi qu'elle avait créé est retiré, l'élève revient en classe. */
  public Resultat annuler(UtilisateurConnecte moi, String id) {
    DecisionExclusion x = decisions.findById(id).orElseThrow(() -> ApiException.introuvable("Décision"));
    Inscription ins = inscriptions.findById(x.getInscriptionId()).orElseThrow(() -> ApiException.introuvable("Inscription"));
    if (x.getRenvoiId() != null) renvois.findById(x.getRenvoiId()).ifPresent(renvois::delete);
    if (x.getType() == Type.DEFINITIVE && ins.getStatut() == StatutInscription.RENVOYE) ins.setStatut(StatutInscription.ACTIF);
    decisions.delete(x);
    return new Resultat("Décision annulée : " + ins.getEleve().nomComplet() + " n'est plus exclu(e).", 1);
  }
}
