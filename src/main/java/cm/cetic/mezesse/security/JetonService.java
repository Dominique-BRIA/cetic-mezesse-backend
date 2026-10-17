package cm.cetic.mezesse.security;

import cm.cetic.mezesse.config.CeticProperties;
import cm.cetic.mezesse.entity.Utilisateur;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Émet les jetons de connexion (JWT HS256, valables 12 h). Ils portent les mêmes champs que ceux de
 * l'ancienne API (id, role, nom, prenom) : un jeton émis par l'une est accepté par l'autre.
 */
@Service
@RequiredArgsConstructor
public class JetonService {

  private final JwtEncoder encodeur;
  private final CeticProperties proprietes;

  public String emettre(Utilisateur u) {
    Instant maintenant = Instant.now();
    JwtClaimsSet revendications =
        JwtClaimsSet.builder()
            .subject(u.getId())
            .claim("id", u.getId())
            .claim("role", u.getRole().name())
            .claim("nom", u.getNom())
            .claim("prenom", u.getPrenom())
            .issuedAt(maintenant)
            .expiresAt(maintenant.plus(proprietes.dureeJeton()))
            .build();
    JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    return encodeur.encode(JwtEncoderParameters.from(entete, revendications)).getTokenValue();
  }
}
