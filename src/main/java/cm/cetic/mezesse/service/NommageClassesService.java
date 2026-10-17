package cm.cetic.mezesse.service;

import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Specialite;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.SpecialiteRepository;
import cm.cetic.mezesse.service.NomsClasses.Nom;
import cm.cetic.mezesse.service.NomsClasses.Renommage;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Recalcule les noms des classes d'un même niveau et d'une même spécialité (RG-29). */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NommageClassesService {

  private final ClasseRepository classes;
  private final SpecialiteRepository specialites;
  private final EntityManager em;

  /**
   * Deux passes pour ne pas heurter l'unicité du nom dans l'année quand les lettres se décalent (B
   * devient A) : chaque classe reçoit d'abord un nom provisoire unique, enregistré, puis son nom final.
   */
  public List<Renommage> renommerGroupe(String anneeId, int niveau, String specialiteId) {
    em.flush();
    List<Classe> groupe = classes.duGroupe(anneeId, niveau, specialiteId);
    if (groupe.isEmpty()) return List.of();
    Specialite s = specialiteId == null ? null : specialites.findById(specialiteId).orElseThrow();
    List<Nom> noms = NomsClasses.noms(niveau, s == null ? null : s.getNom(), s == null ? null : s.getNomEn(), groupe.size());
    List<String> avant = groupe.stream().map(Classe::getNom).toList();
    for (Classe c : groupe) c.setNom("…" + c.getId());
    em.flush();
    List<Renommage> renommages = new ArrayList<>();
    for (int i = 0; i < groupe.size(); i++) {
      Classe c = groupe.get(i);
      c.setNom(noms.get(i).nom());
      c.setNomEn(noms.get(i).nomEn());
      c.setNumero(i + 1);
      String ancien = avant.get(i);
      if (!ancien.equals(c.getNom()) && !ancien.startsWith("…")) renommages.add(new Renommage(ancien, c.getNom()));
    }
    em.flush();
    return renommages;
  }

  /** Numéro de la classe ajoutée à un niveau et une spécialité : elle prend la dernière lettre. */
  public int numeroSuivant(String anneeId, int niveau, String specialiteId) {
    em.flush();
    return classes.numeroMax(anneeId, niveau, specialiteId) + 1;
  }
}
