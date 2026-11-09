package cm.cetic.mezesse.entity;

/**
 * Type énuméré PostgreSQL « Role ». SUPER_ADMIN n'existe que dans les jetons de la console technique :
 * ces comptes sont dans la table AdminConsole, jamais dans Utilisateur.
 */
public enum Role {
  PROVISEUR, SURVEILLANT_GENERAL, PROFESSEUR, SUPER_ADMIN
}
