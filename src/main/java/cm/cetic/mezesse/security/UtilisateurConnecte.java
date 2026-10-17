package cm.cetic.mezesse.security;

import cm.cetic.mezesse.entity.Role;

/** Utilisateur de la requête en cours (rôle relu en base à chaque requête). */
public record UtilisateurConnecte(String id, Role role, String nom, String prenom) {

  public boolean estProfesseur() {
    return role == Role.PROFESSEUR;
  }

  public boolean estSurveillantGeneral() {
    return role == Role.SURVEILLANT_GENERAL;
  }
}
