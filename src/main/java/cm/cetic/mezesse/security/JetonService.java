package cm.cetic.mezesse.security;

import cm.cetic.mezesse.config.CeticProperties;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Émet et renouvelle les jetons de connexion (JWT HS256). Ils portent les champs de l'ancienne API
 * (id, role, nom, prenom), qui les accepte aussi, et deux de plus (RG-04) :
 *
 * <ul>
 *   <li>{@code prolongee} : session « Rester connecté », valable 90 jours au lieu de 12 h ;
 *   <li>{@code version} : version de session du compte ; un jeton d'une version antérieure est refusé.
 * </ul>
 *
 * Une session se prolonge tant qu'elle sert : {@link RenouvellementJetonFilter} renvoie un jeton neuf
 * dès que le jeton reçu a plus d'une heure.
 */
@Service
@RequiredArgsConstructor
public class JetonService {

  static final String PROLONGEE = "prolongee";
  static final String VERSION = "version";

  private final JwtEncoder encodeur;
  private final CeticProperties proprietes;

  public String emettre(Utilisateur u, boolean prolongee) {
    return emettre(u.getId(), u.getRole(), u.getNom(), u.getPrenom(), u.getVersionSession(), prolongee);
  }

  /** Jeton neuf pour la même session : même compte, même version, même durée. */
  public String renouveler(UtilisateurConnecte u, Jwt ancien) {
    return emettre(u.id(), u.role(), u.nom(), u.prenom(), version(ancien), prolongee(ancien));
  }

  public boolean aRenouveler(Jwt jeton) {
    Instant emis = jeton.getIssuedAt();
    return emis == null || emis.plus(proprietes.session().renouvellement()).isBefore(Instant.now());
  }

  /** Les jetons émis avant les sessions prolongées n'ont pas ce champ : ils le deviennent. */
  public static boolean prolongee(Jwt jeton) {
    Boolean b = jeton.getClaimAsBoolean(PROLONGEE);
    return b == null || b;
  }

  /** Version de session du jeton ; 0 pour les jetons émis avant son introduction. */
  public static int version(Jwt jeton) {
    return jeton.getClaims().get(VERSION) instanceof Number n ? n.intValue() : 0;
  }

  private String emettre(String id, Role role, String nom, String prenom, int version, boolean prolongee) {
    Instant maintenant = Instant.now();
    CeticProperties.Session s = proprietes.session();
    JwtClaimsSet revendications =
        JwtClaimsSet.builder()
            .subject(id)
            .claim("id", id)
            .claim("role", role.name())
            .claim("nom", nom)
            .claim("prenom", prenom)
            .claim(PROLONGEE, prolongee)
            .claim(VERSION, version)
            .issuedAt(maintenant)
            .expiresAt(maintenant.plus(prolongee ? s.prolongee() : s.courte()))
            .build();
    JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    return encodeur.encode(JwtEncoderParameters.from(entete, revendications)).getTokenValue();
  }
}
