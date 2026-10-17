package cm.cetic.mezesse.service;

import cm.cetic.mezesse.config.CeticProperties;
import cm.cetic.mezesse.dto.UtilisateurDto.Enseignant;
import cm.cetic.mezesse.dto.UtilisateurDto.Ligne;
import cm.cetic.mezesse.dto.UtilisateurDto.MatiereEnseignee;
import cm.cetic.mezesse.dto.UtilisateurDto.Profil;
import cm.cetic.mezesse.dto.UtilisateurDto.Reference;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comptes des enseignants, gérés par le SG ; la liste est consultable par la direction. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UtilisateurService {

  private final UtilisateurRepository utilisateurs;
  private final MatiereRepository matieres;
  private final ClasseRepository classes;
  private final PasswordEncoder motsDePasse;
  private final CeticProperties proprietes;
  private final AuthService auth;
  private final JournalDecisions journal;

  /** Champs qu'une désapprobation remet en l'état (AnnulationService). */
  static Map<String, Object> champs(Utilisateur u) {
    return Donnees.objet(
        "nom", u.getNom(), "prenom", u.getPrenom(), "telephone", u.getTelephone(), "email", u.getEmail(),
        "discipline", u.getDiscipline(), "actif", u.isActif());
  }

  public List<Ligne> liste(Role role, String anneeId) {
    List<Utilisateur> comptes = role == null ? utilisateurs.findAllByOrderByRoleAscNomAscPrenomAsc() : utilisateurs.findByRoleOrderByNomAscPrenomAsc(role);
    List<String> ids = comptes.stream().map(Utilisateur::getId).toList();
    Map<String, List<Matiere>> parProf =
        ids.isEmpty() ? Map.of() : matieres.enseigneesPar(anneeId, ids).stream().collect(Collectors.groupingBy(Matiere::getProfesseurId));
    Map<String, List<Classe>> principales =
        ids.isEmpty() ? Map.of() : classes.principalesDe(anneeId, ids).stream().collect(Collectors.groupingBy(Classe::getProfPrincipalId));
    return comptes.stream()
        .map(
            u ->
                new Ligne(
                    u.getId(), u.getNom(), u.getPrenom(), u.getTelephone(), u.getEmail(), u.getRole(), u.isActif(),
                    u.isDoitChangerMotDePasse(), u.getDiscipline(), u.getCreeLe(),
                    parProf.getOrDefault(u.getId(), List.of()).stream()
                        .map(m -> new MatiereEnseignee(m.getId(), m.getNom(), m.getCoefficient(), new Reference(m.getClasse().getId(), m.getClasse().getNom())))
                        .toList(),
                    principales.getOrDefault(u.getId(), List.of()).stream().map(c -> new Reference(c.getId(), c.getNom())).toList()))
        .toList();
  }

  /**
   * Le SG crée les comptes des professeurs. Le compte reçoit le mot de passe par défaut et doit le
   * changer à la première connexion.
   */
  @Transactional
  public Profil creer(UtilisateurConnecte moi, Enseignant d) {
    verifierContact(d);
    auth.verifierDisponibles("", d.telephone(), d.email());
    Utilisateur u = new Utilisateur();
    appliquer(u, d);
    u.setRole(Role.PROFESSEUR);
    u.setMotDePasse(motsDePasse.encode(proprietes.motDePasseReinitialise()));
    u.setDoitChangerMotDePasse(true);
    utilisateurs.saveAndFlush(u);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "enseignant.creer",
        "Création du compte de l'enseignant " + u.nomComplet() + (u.getDiscipline() != null ? " (" + u.getDiscipline() + ")" : ""),
        "/enseignants", Donnees.objet("id", u.getId()));
    return Profil.de(u);
  }

  @Transactional
  public Profil modifier(UtilisateurConnecte moi, String id, Enseignant d) {
    verifierContact(d);
    Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> ApiException.introuvable("Compte"));
    if (u.getRole() == Role.PROVISEUR) throw ApiException.interdit("Le compte du Proviseur ne se modifie pas ici.");
    if (id.equals(moi.id()) && !d.actif()) throw ApiException.invalide("Vous ne pouvez pas désactiver votre propre compte.");
    auth.verifierDisponibles(id, d.telephone(), d.email());
    Map<String, Object> avant = champs(u);
    boolean etaitActif = u.isActif();
    appliquer(u, d);
    utilisateurs.saveAndFlush(u);
    String quoi = etaitActif && !u.isActif() ? "Désactivation" : !etaitActif && u.isActif() ? "Réactivation" : "Modification";
    String action = switch (quoi) {
      case "Désactivation" -> "enseignant.desactiver";
      case "Réactivation" -> "enseignant.reactiver";
      default -> "enseignant.modifier";
    };
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, action, quoi + " du compte de " + u.nomComplet(), "/enseignants",
        Donnees.objet("id", id, "avant", avant, "apres", champs(u)));
    return Profil.de(u);
  }

  @Transactional
  public String reinitialiser(UtilisateurConnecte moi, String id) {
    Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> ApiException.introuvable("Compte"));
    if (u.getRole() == Role.PROVISEUR) throw ApiException.interdit("Le mot de passe du Proviseur ne se réinitialise pas ici.");
    String avantHash = u.getMotDePasse();
    boolean avantDoitChanger = u.isDoitChangerMotDePasse();
    u.setMotDePasse(motsDePasse.encode(proprietes.motDePasseReinitialise()));
    u.setDoitChangerMotDePasse(true);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "enseignant.reinitialiser", "Réinitialisation du mot de passe de " + u.nomComplet(), "/enseignants",
        Donnees.objet("id", id, "avantHash", avantHash, "avantDoitChanger", avantDoitChanger, "apresHash", u.getMotDePasse()));
    return "Mot de passe réinitialisé à « " + proprietes.motDePasseReinitialise() + " ».";
  }

  private static void verifierContact(Enseignant d) {
    if (d.telephone() == null && d.email() == null) throw ApiException.invalide("telephone : renseignez un téléphone ou un e-mail");
  }

  private static void appliquer(Utilisateur u, Enseignant d) {
    u.setNom(d.nom());
    u.setPrenom(d.prenom());
    u.setTelephone(d.telephone());
    u.setEmail(d.email());
    u.setDiscipline(d.discipline());
    u.setActif(d.actif());
  }
}
