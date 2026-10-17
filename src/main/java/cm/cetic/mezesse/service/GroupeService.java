package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.MatiereDto.GroupeNombres;
import cm.cetic.mezesse.dto.MatiereDto.GroupeRequete;
import cm.cetic.mezesse.dto.MatiereDto.GroupeVue;
import cm.cetic.mezesse.dto.MatiereDto.Ref;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.GroupeMatiere;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.GroupeMatiereRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Groupes de matières ; un groupe peut servir à plusieurs classes de la même année. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupeService {

  private final GroupeMatiereRepository groupes;
  private final ClasseRepository classes;
  private final AnneeScolaireRepository annees;
  private final MatiereRepository matieres;
  private final JournalDecisions journal;

  public List<GroupeVue> liste(String anneeId) {
    List<GroupeMatiere> liste = groupes.findByAnnee_IdOrderByOrdreAsc(anneeId);
    Map<String, Long> nombres = liste.isEmpty() ? Map.of() : Compte.enMap(matieres.compterParGroupe(liste.stream().map(GroupeMatiere::getId).toList()));
    return liste.stream().map(g -> vue(g, nombres.getOrDefault(g.getId(), 0L))).toList();
  }

  private static GroupeVue vue(GroupeMatiere g, long nbMatieres) {
    return new GroupeVue(
        g.getId(), g.getAnneeId(), g.getNom(), g.getNomEn(), g.getOrdre(),
        g.getClasses().stream().sorted(Comparator.comparingInt(Classe::getNiveau)).map(c -> new Ref(c.getId(), c.getNom())).toList(),
        new GroupeNombres(nbMatieres));
  }

  private static String noms(GroupeMatiere g) {
    return String.join(", ", g.getClasses().stream().sorted(Comparator.comparingInt(Classe::getNiveau)).map(Classe::getNom).toList());
  }

  @Transactional
  public GroupeVue creer(UtilisateurConnecte moi, String anneeId, GroupeRequete d) {
    GroupeMatiere g = new GroupeMatiere();
    g.setAnnee(annees.findById(anneeId).orElseThrow(() -> ApiException.introuvable("Année scolaire")));
    g.setNom(d.nom());
    g.setNomEn(d.nomEn());
    g.setOrdre(d.ordre());
    g.setClasses(chargerClasses(d.classeIds()));
    groupes.saveAndFlush(g);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "groupe.creer", "Création du groupe de matières " + g.getNom() + " pour " + noms(g), "/groupes",
        Donnees.objet("id", g.getId()));
    return vue(g, 0);
  }

  @Transactional
  public GroupeVue modifier(UtilisateurConnecte moi, String id, GroupeRequete d) {
    // On ne retire pas une classe dont des matières sont encore rangées dans ce groupe.
    List<String> bloquantes = matieres.classesHorsListe(id, d.classeIds());
    if (!bloquantes.isEmpty()) {
      throw ApiException.conflit(
          "Des matières de " + String.join(", ", bloquantes) + " sont dans ce groupe : changez-les de groupe d'abord.");
    }
    GroupeMatiere g = groupes.findById(id).orElseThrow(() -> ApiException.introuvable("Groupe de matières"));
    Map<String, Object> avant =
        Donnees.objet("nom", g.getNom(), "nomEn", g.getNomEn(), "ordre", g.getOrdre(), "classeIds", g.getClasses().stream().map(Classe::getId).toList());
    String nomAvant = g.getNom();
    g.setNom(d.nom());
    g.setNomEn(d.nomEn());
    g.setOrdre(d.ordre());
    g.getClasses().clear();
    g.getClasses().addAll(chargerClasses(d.classeIds()));
    groupes.saveAndFlush(g);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "groupe.modifier",
        "Modification du groupe de matières " + nomAvant + (nomAvant.equals(g.getNom()) ? "" : " (renommé " + g.getNom() + ")") + " : " + noms(g),
        "/groupes",
        Donnees.objet(
            "id", id, "avant", avant,
            "apres", Donnees.objet("nom", g.getNom(), "nomEn", g.getNomEn(), "ordre", g.getOrdre(), "classeIds", g.getClasses().stream().map(Classe::getId).toList())));
    return vue(g, matieres.countByGroupe_Id(id));
  }

  @Transactional
  public void supprimer(UtilisateurConnecte moi, String id) {
    if (matieres.countByGroupe_Id(id) > 0) {
      throw ApiException.conflit("Ce groupe contient des matières : déplacez-les ou supprimez-les d'abord.");
    }
    GroupeMatiere g = groupes.findById(id).orElseThrow(() -> ApiException.introuvable("Groupe de matières"));
    Map<String, Object> annulation =
        Donnees.objet(
            "anneeId", g.getAnneeId(), "nom", g.getNom(), "nomEn", g.getNomEn(), "ordre", g.getOrdre(),
            "classeIds", g.getClasses().stream().map(Classe::getId).toList());
    groupes.delete(g);
    journal.consigner(moi, DomaineDecision.ORGANISATION, "groupe.supprimer", "Suppression du groupe de matières " + g.getNom(), "/groupes", annulation);
  }

  private LinkedHashSet<Classe> chargerClasses(List<String> ids) {
    List<Classe> trouvees = classes.findAllById(ids);
    if (trouvees.size() != new LinkedHashSet<>(ids).size()) throw ApiException.invalide("Une des classes choisies est introuvable.");
    return new LinkedHashSet<>(trouvees);
  }
}
