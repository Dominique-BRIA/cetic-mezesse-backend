package cm.cetic.mezesse.service;

import cm.cetic.mezesse.config.CeticProperties;
import cm.cetic.mezesse.dto.UtilisateurDto.Carriere;
import cm.cetic.mezesse.dto.UtilisateurDto.Enseignant;
import cm.cetic.mezesse.dto.UtilisateurDto.Ligne;
import cm.cetic.mezesse.dto.UtilisateurDto.MatiereEnseignee;
import cm.cetic.mezesse.dto.UtilisateurDto.Profil;
import cm.cetic.mezesse.dto.UtilisateurDto.Reference;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Sexe;
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
        "discipline", u.getDiscipline(), "actif", u.isActif(), "sexe", u.getSexe() == null ? null : u.getSexe().name(),
        "matricule", u.getMatricule(), "grade", u.getGrade(), "fonction", u.getFonction(),
        "datePriseService", u.getDatePriseService() == null ? null : u.getDatePriseService().toString(),
        "dateEntreeFonctionPublique", u.getDateEntreeFonctionPublique() == null ? null : u.getDateEntreeFonctionPublique().toString(),
        "acteNomination", u.getActeNomination());
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
                    u.isDoitChangerMotDePasse(), u.getDiscipline(), u.getSexe(), u.getMatricule(), u.getGrade(), u.getFonction(),
                    u.getDatePriseService(), u.getDateEntreeFonctionPublique(), u.getActeNomination(), u.getCreeLe(),
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
    if (d.carriere() != null) verifierMatricule("", d.carriere().matricule());
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
    if (d.carriere() != null) verifierMatricule(id, d.carriere().matricule());
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
    if (d.carriere() != null) appliquer(u, d.carriere());
  }

  static void appliquer(Utilisateur u, Carriere c) {
    u.setSexe(c.sexe() == null ? null : Sexe.valueOf(c.sexe()));
    u.setMatricule(c.matricule());
    u.setGrade(c.grade());
    u.setFonction(c.fonction());
    u.setDatePriseService(c.datePriseService());
    u.setDateEntreeFonctionPublique(c.dateEntreeFonctionPublique());
    u.setActeNomination(c.acteNomination());
  }

  /**
   * Carrière d'un personnel (matricule, grade, fonction, prise de service…), complétée par le SG ou par
   * le Proviseur au moment de délivrer une attestation. Le compte du Proviseur ne se modifie pas ici.
   */
  @Transactional
  public Profil carriere(UtilisateurConnecte moi, String id, Carriere c) {
    Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> ApiException.introuvable("Compte"));
    if (u.getRole() == Role.PROVISEUR && !id.equals(moi.id())) throw ApiException.interdit("Le compte du Proviseur ne se modifie pas ici.");
    verifierMatricule(id, c.matricule());
    Map<String, Object> avant = champs(u);
    appliquer(u, c);
    utilisateurs.saveAndFlush(u);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "enseignant.modifier", "Carrière de " + u.nomComplet() + " mise à jour", "/enseignants",
        Donnees.objet("id", id, "avant", avant, "apres", champs(u)));
    return Profil.de(u);
  }

  /** Le matricule de la fonction publique identifie une personne : il ne sert pas deux fois. */
  private void verifierMatricule(String id, String matricule) {
    if (matricule != null && utilisateurs.findFirstByIdNotAndMatricule(id, matricule).isPresent()) {
      throw ApiException.conflit("Ce matricule est déjà celui d'un autre compte.");
    }
  }
}
