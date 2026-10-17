package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.ClasseDto.Comptes;
import cm.cetic.mezesse.dto.ClasseDto.Personne;
import cm.cetic.mezesse.dto.ClasseDto.Ref;
import cm.cetic.mezesse.dto.ClasseDto.Requete;
import cm.cetic.mezesse.dto.ClasseDto.SpecialiteRef;
import cm.cetic.mezesse.dto.ClasseDto.Vue;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.GroupeMatiere;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Specialite;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.SpecialiteRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.NomsClasses.Renommage;
import cm.cetic.mezesse.util.Donnees;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Classes d'une année. Leur nom est calculé (RG-29) et se recalcule quand le groupe change. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClasseService {

  private final ClasseRepository classes;
  private final AnneeScolaireRepository annees;
  private final SpecialiteRepository specialites;
  private final UtilisateurRepository utilisateurs;
  private final MatiereRepository matieres;
  private final InscriptionRepository inscriptions;
  private final NommageClassesService nommage;
  private final JournalDecisions journal;

  /** Un professeur ne voit que les classes où il enseigne. */
  public List<Vue> liste(UtilisateurConnecte moi, String anneeId) {
    List<Classe> liste = moi.estProfesseur() ? classes.deLAnneePourProfesseur(anneeId, moi.id()) : classes.deLAnnee(anneeId);
    return vues(liste);
  }

  public Vue une(String id) {
    return vue(classes.findById(id).orElseThrow(() -> ApiException.introuvable("Classe")));
  }

  Vue vue(Classe c) {
    return vues(List.of(c)).get(0);
  }

  private List<Vue> vues(List<Classe> liste) {
    if (liste.isEmpty()) return List.of();
    List<String> ids = liste.stream().map(Classe::getId).toList();
    Map<String, Long> nbMatieres = Compte.enMap(matieres.compterParClasse(ids));
    Map<String, Long> nbEleves = Compte.enMap(inscriptions.activesParClasse(ids));
    return liste.stream()
        .map(
            c -> {
              Utilisateur pp = c.getProfPrincipal();
              Specialite s = c.getSpecialite();
              return new Vue(
                  c.getId(), c.getAnneeId(), c.getNom(), c.getNomEn(), c.getNiveau(), c.getSpecialiteId(), c.getNumero(),
                  c.getProfPrincipalId(),
                  pp == null ? null : new Personne(pp.getId(), pp.getNom(), pp.getPrenom()),
                  s == null ? null : new SpecialiteRef(s.getId(), s.getNom(), s.getNomEn()),
                  c.getGroupes().stream()
                      .sorted(Comparator.comparingInt(GroupeMatiere::getOrdre))
                      .map(g -> new Ref(g.getId(), g.getNom()))
                      .toList(),
                  new Comptes(nbMatieres.getOrDefault(c.getId(), 0L), nbEleves.getOrDefault(c.getId(), 0L)));
            })
        .toList();
  }

  private static String lien(String id) {
    return "/eleves?classe=" + id;
  }

  @Transactional
  public Vue creer(UtilisateurConnecte moi, String anneeId, Requete d) {
    AnneeScolaire annee = annees.findById(anneeId).orElseThrow(() -> ApiException.introuvable("Année scolaire"));
    Classe c = new Classe();
    c.setAnnee(annee);
    c.setNiveau(d.niveau());
    c.setSpecialite(specialite(d.specialiteId()));
    c.setProfPrincipal(professeurPrincipal(d.profPrincipalId()));
    c.setNumero(nommage.numeroSuivant(anneeId, d.niveau(), d.specialiteId()));
    c.setNom("…" + UUID.randomUUID());
    classes.saveAndFlush(c);
    List<Renommage> renommees = nommage.renommerGroupe(anneeId, d.niveau(), d.specialiteId());
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "classe.creer", "Création de la classe " + c.getNom() + NomsClasses.decrire(renommees),
        lien(c.getId()), Donnees.objet("classeId", c.getId()));
    return vue(c);
  }

  @Transactional
  public Vue modifier(UtilisateurConnecte moi, String id, Requete d) {
    Classe c = classes.findById(id).orElseThrow(() -> ApiException.introuvable("Classe"));
    String nomAvant = c.getNom();
    int niveauAvant = c.getNiveau();
    String specialiteAvant = c.getSpecialiteId();
    String ppAvant = c.getProfPrincipalId();
    boolean deplacee = niveauAvant != d.niveau() || !Objects.equals(specialiteAvant, d.specialiteId());
    int numero = deplacee ? nommage.numeroSuivant(c.getAnneeId(), d.niveau(), d.specialiteId()) : c.getNumero();
    c.setNiveau(d.niveau());
    c.setSpecialite(specialite(d.specialiteId()));
    c.setProfPrincipal(professeurPrincipal(d.profPrincipalId()));
    c.setNumero(numero);
    List<Renommage> renommees = new ArrayList<>();
    if (deplacee) {
      renommees.addAll(nommage.renommerGroupe(c.getAnneeId(), niveauAvant, specialiteAvant));
      renommees.addAll(nommage.renommerGroupe(c.getAnneeId(), d.niveau(), d.specialiteId()));
    }
    List<String> changements = new ArrayList<>(renommees.stream().map(r -> r.avant() + " devient " + r.apres()).toList());
    if (!Objects.equals(ppAvant, c.getProfPrincipalId())) {
      changements.add("professeur principal : " + (c.getProfPrincipal() == null ? "aucun" : c.getProfPrincipal().nomComplet()));
    }
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "classe.modifier",
        "Modification de la classe " + nomAvant + (changements.isEmpty() ? "" : " : " + String.join(", ", changements)),
        lien(id),
        Donnees.objet(
            "classeId", id,
            "avant", Donnees.objet("niveau", niveauAvant, "specialiteId", specialiteAvant, "profPrincipalId", ppAvant),
            "apres", Donnees.objet("niveau", c.getNiveau(), "specialiteId", c.getSpecialiteId(), "profPrincipalId", c.getProfPrincipalId())));
    return vue(c);
  }

  /** Une classe sans élève peut être supprimée (avec ses matières) ; les lettres des autres se resserrent. */
  @Transactional
  public void supprimer(UtilisateurConnecte moi, String id) {
    Classe c = classes.findById(id).orElseThrow(() -> ApiException.introuvable("Classe"));
    long eleves = inscriptions.countByClasse_Id(id);
    if (eleves > 0) throw ApiException.conflit(c.getNom() + " a " + eleves + " élève(s) inscrit(s) : elle ne peut pas être supprimée.");
    List<Map<String, Object>> sesMatieres = new ArrayList<>();
    List<Matiere> aSupprimer = matieres.findByClasse_Id(id);
    for (Matiere m : aSupprimer) {
      sesMatieres.add(
          Donnees.objet(
              "nom", m.getNom(), "nomEn", m.getNomEn(), "coefficient", m.getCoefficient(), "ordre", m.getOrdre(),
              "groupeId", m.getGroupeId(), "professeurId", m.getProfesseurId()));
    }
    Map<String, Object> annulation =
        Donnees.objet(
            "anneeId", c.getAnneeId(), "niveau", c.getNiveau(), "specialiteId", c.getSpecialiteId(), "numero", c.getNumero(),
            "profPrincipalId", c.getProfPrincipalId(), "groupeIds", c.getGroupes().stream().map(GroupeMatiere::getId).toList(),
            "matieres", sesMatieres);
    String anneeId = c.getAnneeId();
    int niveau = c.getNiveau();
    String specialiteId = c.getSpecialiteId();
    String nom = c.getNom();
    for (GroupeMatiere g : c.getGroupes()) g.getClasses().remove(c);
    // Les matières partent avec la classe ; leurs notes suivent (ON DELETE CASCADE).
    matieres.deleteAll(aSupprimer);
    classes.delete(c);
    List<Renommage> renommees = nommage.renommerGroupe(anneeId, niveau, specialiteId);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "classe.supprimer", "Suppression de la classe " + nom + NomsClasses.decrire(renommees),
        "/classes", annulation);
  }

  private Specialite specialite(String id) {
    return id == null ? null : specialites.findById(id).orElseThrow(() -> ApiException.invalide("Spécialité introuvable."));
  }

  private Utilisateur professeurPrincipal(String id) {
    return id == null ? null : utilisateurs.findById(id).orElseThrow(() -> ApiException.invalide("Professeur principal introuvable."));
  }
}
