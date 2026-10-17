package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.ClasseDto.SpecialiteNombres;
import cm.cetic.mezesse.dto.ClasseDto.SpecialiteRequete;
import cm.cetic.mezesse.dto.ClasseDto.SpecialiteVue;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Specialite;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.SpecialiteRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.NomsClasses.Renommage;
import cm.cetic.mezesse.util.Donnees;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spécialités des classes, créées par le SG et communes à toutes les années (RG-29). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialiteService {

  private final SpecialiteRepository specialites;
  private final ClasseRepository classes;
  private final NommageClassesService nommage;
  private final JournalDecisions journal;

  public List<SpecialiteVue> liste() {
    Map<String, Long> nombres = Compte.enMap(classes.compterParSpecialite());
    return specialites.findAllByOrderByNomAsc().stream().map(s -> vue(s, nombres.getOrDefault(s.getId(), 0L))).toList();
  }

  private static SpecialiteVue vue(Specialite s, long nbClasses) {
    return new SpecialiteVue(s.getId(), s.getNom(), s.getNomEn(), s.getCreeLe(), new SpecialiteNombres(nbClasses));
  }

  @Transactional
  public SpecialiteVue creer(UtilisateurConnecte moi, SpecialiteRequete d) {
    Specialite s = new Specialite();
    s.setNom(d.nom());
    s.setNomEn(d.nomEn());
    specialites.saveAndFlush(s);
    journal.consigner(moi, DomaineDecision.ORGANISATION, "specialite.creer", "Création de la spécialité " + s.getNom(), "/classes", Donnees.objet("id", s.getId()));
    return vue(s, 0);
  }

  /** Renommer une spécialité renomme les classes qui la portent, toutes années confondues. */
  @Transactional
  public SpecialiteVue modifier(UtilisateurConnecte moi, String id, SpecialiteRequete d) {
    Specialite s = specialites.findById(id).orElseThrow(() -> ApiException.introuvable("Spécialité"));
    String nomAvant = s.getNom();
    String nomEnAvant = s.getNomEn();
    s.setNom(d.nom());
    s.setNomEn(d.nomEn());
    specialites.saveAndFlush(s);
    List<Renommage> renommees = renommerClasses(id);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "specialite.modifier",
        nomAvant.equals(s.getNom())
            ? "Modification de la spécialité " + s.getNom()
            : "Spécialité " + nomAvant + " renommée en " + s.getNom() + NomsClasses.decrire(renommees),
        "/classes",
        Donnees.objet(
            "id", id, "avant", Donnees.objet("nom", nomAvant, "nomEn", nomEnAvant), "apres", Donnees.objet("nom", s.getNom(), "nomEn", s.getNomEn())));
    return vue(s, classes.countBySpecialite_Id(id));
  }

  /** Recalcule le nom de toutes les classes de la spécialité. */
  List<Renommage> renommerClasses(String specialiteId) {
    List<Renommage> renommees = new ArrayList<>();
    for (Object[] g : classes.groupesDeLaSpecialite(specialiteId)) {
      renommees.addAll(nommage.renommerGroupe((String) g[0], (Integer) g[1], specialiteId));
    }
    return renommees;
  }

  @Transactional
  public void supprimer(UtilisateurConnecte moi, String id) {
    Specialite s = specialites.findById(id).orElseThrow(() -> ApiException.introuvable("Spécialité"));
    long n = classes.countBySpecialite_Id(id);
    if (n > 0) throw ApiException.conflit("La spécialité " + s.getNom() + " est portée par " + n + " classe(s) : elle ne peut pas être supprimée.");
    specialites.delete(s);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "specialite.supprimer", "Suppression de la spécialité " + s.getNom(), "/classes",
        Donnees.objet("nom", s.getNom(), "nomEn", s.getNomEn()));
  }
}
