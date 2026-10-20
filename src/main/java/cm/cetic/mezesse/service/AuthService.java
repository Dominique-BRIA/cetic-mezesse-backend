package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.UtilisateurDto.ChangementMotDePasse;
import cm.cetic.mezesse.dto.UtilisateurDto.Connexion;
import cm.cetic.mezesse.dto.UtilisateurDto.ModificationProfil;
import cm.cetic.mezesse.dto.UtilisateurDto.Profil;
import cm.cetic.mezesse.dto.UtilisateurDto.ProfilComplet;
import cm.cetic.mezesse.dto.UtilisateurDto.ReponseConnexion;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.JetonService;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Connexion, profil de l'utilisateur connecté et changement de son mot de passe. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UtilisateurRepository utilisateurs;
  private final PasswordEncoder motsDePasse;
  private final JetonService jetons;

  /** L'identifiant est un e-mail ou un numéro de téléphone. */
  public ReponseConnexion connexion(Connexion c) {
    String id = c.identifiant().contains("@") ? c.identifiant().toLowerCase(Locale.ROOT) : c.identifiant().replaceAll("[\\s.-]", "");
    Utilisateur u = utilisateurs.parIdentifiant(id).stream().findFirst().orElse(null);
    if (u == null || !motsDePasse.matches(c.motDePasse(), u.getMotDePasse())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect.");
    }
    if (!u.isActif()) throw ApiException.interdit("Ce compte est désactivé.");
    return new ReponseConnexion(jetons.emettre(u, c.prolongee()), ProfilComplet.de(u));
  }

  public Profil moi(UtilisateurConnecte moi) {
    return Profil.de(charger(moi.id()));
  }

  /**
   * Espace profil : chacun modifie ses propres informations. Changer le téléphone ou l'e-mail, qui
   * servent d'identifiant de connexion, demande le mot de passe actuel.
   */
  @Transactional
  public Profil modifierProfil(UtilisateurConnecte moi, ModificationProfil d) {
    if (d.telephone() == null && d.email() == null) throw ApiException.invalide("telephone : renseignez un téléphone ou un e-mail");
    Utilisateur u = charger(moi.id());
    boolean identifiantChange = !Objects.equals(d.telephone(), u.getTelephone()) || !Objects.equals(d.email(), u.getEmail());
    if (identifiantChange && (d.motDePasse() == null || !motsDePasse.matches(d.motDePasse(), u.getMotDePasse()))) {
      throw ApiException.invalide("Pour changer de téléphone ou d’e-mail, saisissez votre mot de passe actuel.");
    }
    verifierDisponibles(u.getId(), d.telephone(), d.email());
    u.setNom(d.nom());
    u.setPrenom(d.prenom());
    u.setTelephone(d.telephone());
    u.setEmail(d.email());
    // Civilité : imprimée sur les documents (« La Directrice », « Mme »…). Absente : inchangée.
    if (d.sexe() != null) u.setSexe(cm.cetic.mezesse.entity.Sexe.valueOf(d.sexe()));
    // La discipline n'a de sens que pour un professeur.
    if (u.getRole() == Role.PROFESSEUR) u.setDiscipline(d.discipline());
    return Profil.de(utilisateurs.saveAndFlush(u));
  }

  /**
   * Ferme les sessions ouvertes sur les autres appareils (RG-04) et renvoie le jeton qui garde
   * celle-ci ouverte, avec la même durée.
   */
  @Transactional
  public String changerMotDePasse(UtilisateurConnecte moi, boolean prolongee, ChangementMotDePasse d) {
    if (d.nouveau().equals("password")) throw ApiException.invalide("Choisissez un mot de passe différent de « password ».");
    Utilisateur u = charger(moi.id());
    if (!motsDePasse.matches(d.ancien(), u.getMotDePasse())) throw ApiException.invalide("Mot de passe actuel incorrect.");
    u.setMotDePasse(motsDePasse.encode(d.nouveau()));
    u.setDoitChangerMotDePasse(false);
    u.fermerSessions();
    return jetons.emettre(u, prolongee);
  }

  /** Le téléphone et l'e-mail identifient un compte : ils ne peuvent pas servir deux fois. */
  void verifierDisponibles(String id, String telephone, String email) {
    if (telephone != null && utilisateurs.findFirstByIdNotAndTelephone(id, telephone).isPresent()) {
      throw ApiException.conflit("Ce numéro est déjà utilisé par un autre compte.");
    }
    if (email != null && utilisateurs.findFirstByIdNotAndEmail(id, email).isPresent()) {
      throw ApiException.conflit("Cette adresse e-mail est déjà utilisée par un autre compte.");
    }
  }

  private Utilisateur charger(String id) {
    return utilisateurs.findById(id).orElseThrow(() -> ApiException.introuvable("Compte"));
  }
}
