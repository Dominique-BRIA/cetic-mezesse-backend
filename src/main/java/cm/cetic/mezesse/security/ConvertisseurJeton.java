package cm.cetic.mezesse.security;

import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Transforme un jeton valide en utilisateur connecté. Le compte est relu en base à chaque requête :
 * un compte désactivé perd l'accès immédiatement, un changement de rôle s'applique tout de suite, et
 * un jeton émis avant un changement de mot de passe (version de session antérieure) est refusé.
 */
@Component
@RequiredArgsConstructor
public class ConvertisseurJeton implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UtilisateurRepository utilisateurs;

  @Override
  public AbstractAuthenticationToken convert(Jwt jeton) {
    String id = jeton.getClaimAsString("id");
    if (id == null) throw new BadCredentialsException("Jeton sans identifiant");
    Utilisateur u = utilisateurs.findById(id).orElse(null);
    if (u == null || !u.isActif()) throw new DisabledException("Compte désactivé.");
    if (JetonService.version(jeton) != u.getVersionSession()) throw new BadCredentialsException("Session fermée");
    UtilisateurConnecte connecte =
        new UtilisateurConnecte(u.getId(), u.getRole(), jeton.getClaimAsString("nom"), jeton.getClaimAsString("prenom"));
    return new AuthentificationJeton(connecte, jeton);
  }
}
