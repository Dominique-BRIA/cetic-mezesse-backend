package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.MatiereDto.ClasseRef;
import cm.cetic.mezesse.dto.MatiereDto.GroupeRef;
import cm.cetic.mezesse.dto.MatiereDto.Nombres;
import cm.cetic.mezesse.dto.MatiereDto.Personne;
import cm.cetic.mezesse.dto.MatiereDto.Requete;
import cm.cetic.mezesse.dto.MatiereDto.Vue;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.GroupeMatiereRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.NoteRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matières des classes : coefficient, groupe, professeur. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatiereService {

  private final MatiereRepository matieres;
  private final ClasseRepository classes;
  private final GroupeMatiereRepository groupes;
  private final UtilisateurRepository utilisateurs;
  private final NoteRepository notes;
  private final JournalDecisions journal;

  /** Champs qu'une désapprobation remet en l'état (AnnulationService). */
  static Map<String, Object> champs(Matiere m) {
    return Donnees.objet(
        "nom", m.getNom(), "nomEn", m.getNomEn(), "coefficient", m.getCoefficient(), "ordre", m.getOrdre(),
        "groupeId", m.getGroupeId(), "classeId", m.getClasseId(), "professeurId", m.getProfesseurId());
  }

  /** Un professeur ne voit que ses matières. */
  public List<Vue> liste(UtilisateurConnecte moi, String anneeId, String classeId) {
    List<Matiere> liste = matieres.deLAnnee(anneeId, classeId, moi.estProfesseur() ? moi.id() : null);
    Map<String, Long> nbNotes = liste.isEmpty() ? Map.of() : Compte.enMap(notes.compterParMatiere(liste.stream().map(Matiere::getId).toList()));
    return liste.stream().map(m -> vue(m, nbNotes.getOrDefault(m.getId(), 0L))).toList();
  }

  private static Vue vue(Matiere m, long nbNotes) {
    Classe c = m.getClasse();
    Utilisateur p = m.getProfesseur();
    return new Vue(
        m.getId(), m.getClasseId(), m.getGroupeId(), m.getNom(), m.getNomEn(), m.getCoefficient(), m.getOrdre(), m.getProfesseurId(),
        new ClasseRef(c.getId(), c.getNom(), c.getNiveau()),
        new GroupeRef(m.getGroupe().getId(), m.getGroupe().getNom(), m.getGroupe().getOrdre()),
        p == null ? null : new Personne(p.getId(), p.getNom(), p.getPrenom()),
        new Nombres(nbNotes));
  }

  @Transactional
  public Vue creer(UtilisateurConnecte moi, String anneeId, Requete d) {
    verifierGroupe(d.groupeId(), d.classeId());
    Utilisateur prof = professeur(d.professeurId());
    if (classes.countByIdAndAnnee_Id(d.classeId(), anneeId) == 0) throw ApiException.interdit();
    Matiere m = new Matiere();
    appliquer(m, d, prof);
    matieres.saveAndFlush(m);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "matiere.creer",
        "Création de la matière " + m.getNom() + " en " + m.getClasse().getNom() + " (coefficient " + m.getCoefficient()
            + (prof != null ? ", " + prof.nomComplet() : "") + ")",
        "/matieres?classe=" + m.getClasseId(), Donnees.objet("id", m.getId()));
    return vue(m, 0);
  }

  @Transactional
  public Vue modifier(UtilisateurConnecte moi, String id, Requete d) {
    Matiere m = matieres.findById(id).orElseThrow(() -> ApiException.introuvable("Matière"));
    long nbNotes = notes.countByMatiere_Id(id);
    if (!m.getClasseId().equals(d.classeId()) && nbNotes > 0) {
      throw ApiException.conflit("Cette matière a déjà des notes : on ne peut plus la changer de classe.");
    }
    verifierGroupe(d.groupeId(), d.classeId());
    Utilisateur prof = professeur(d.professeurId());
    Map<String, Object> avant = champs(m);
    String nomAvant = m.getNom();
    String classeAvant = m.getClasse().getNom();
    int coefAvant = m.getCoefficient();
    appliquer(m, d, prof);
    matieres.saveAndFlush(m);
    List<String> changements = new ArrayList<>();
    if (!nomAvant.equals(m.getNom())) changements.add("renommée " + m.getNom());
    if (!Objects.equals(avant.get("classeId"), m.getClasseId())) changements.add("déplacée en " + m.getClasse().getNom());
    if (coefAvant != m.getCoefficient()) changements.add("coefficient " + coefAvant + " → " + m.getCoefficient());
    if (!Objects.equals(avant.get("groupeId"), m.getGroupeId())) changements.add("groupe " + m.getGroupe().getNom());
    if (!Objects.equals(avant.get("professeurId"), m.getProfesseurId())) {
      changements.add("professeur : " + (prof == null ? "aucun" : prof.nomComplet()));
    }
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "matiere.modifier",
        "Modification de la matière " + nomAvant + " en " + classeAvant + (changements.isEmpty() ? "" : " : " + String.join(", ", changements)),
        "/matieres?classe=" + m.getClasseId(), Donnees.objet("id", id, "avant", avant, "apres", champs(m)));
    return vue(m, nbNotes);
  }

  /** Supprimer une matière supprime ses notes : l'interface demande une confirmation explicite. */
  @Transactional
  public void supprimer(UtilisateurConnecte moi, String id, boolean confirme) {
    Matiere m = matieres.findById(id).orElseThrow(() -> ApiException.introuvable("Matière"));
    long nbNotes = notes.countByMatiere_Id(id);
    if (nbNotes > 0 && !confirme) {
      throw ApiException.conflit("Cette matière a " + nbNotes + " note(s). Confirmez pour la supprimer avec ses notes.");
    }
    Map<String, Object> annulation = champs(m);
    annulation.put("notes", nbNotes);
    String classe = m.getClasse().getNom();
    String classeId = m.getClasseId();
    matieres.delete(m);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "matiere.supprimer",
        "Suppression de la matière " + m.getNom() + " en " + classe + (nbNotes > 0 ? " et de ses " + nbNotes + " note(s)" : ""),
        "/matieres?classe=" + classeId, annulation);
  }

  /** Le groupe doit être rattaché à la classe de la matière. */
  private void verifierGroupe(String groupeId, String classeId) {
    if (!groupes.existsByIdAndClasses_Id(groupeId, classeId)) throw ApiException.invalide("Ce groupe n'est pas rattaché à cette classe.");
  }

  private Utilisateur professeur(String id) {
    if (id == null) return null;
    Utilisateur p = utilisateurs.findById(id).orElse(null);
    if (p == null || p.getRole() != Role.PROFESSEUR) throw ApiException.invalide("L'enseignant choisi n'est pas un professeur.");
    return p;
  }

  private void appliquer(Matiere m, Requete d, Utilisateur prof) {
    m.setClasse(classes.getReferenceById(d.classeId()));
    m.setGroupe(groupes.getReferenceById(d.groupeId()));
    m.setNom(d.nom());
    m.setNomEn(d.nomEn());
    m.setCoefficient(d.coefficient());
    m.setOrdre(d.ordre());
    m.setProfesseur(prof);
  }
}
