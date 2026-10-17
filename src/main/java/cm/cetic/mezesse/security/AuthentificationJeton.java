package cm.cetic.mezesse.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentification issue d'un jeton valide : le principal est l'utilisateur connecté. */
public class AuthentificationJeton extends AbstractAuthenticationToken {

  private final UtilisateurConnecte utilisateur;
  private final Jwt jeton;

  public AuthentificationJeton(UtilisateurConnecte utilisateur, Jwt jeton) {
    super(List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.role().name())));
    this.utilisateur = utilisateur;
    this.jeton = jeton;
    setAuthenticated(true);
  }

  @Override
  public UtilisateurConnecte getPrincipal() {
    return utilisateur;
  }

  @Override
  public Jwt getCredentials() {
    return jeton;
  }

  @Override
  public String getName() {
    return utilisateur.id();
  }
}
