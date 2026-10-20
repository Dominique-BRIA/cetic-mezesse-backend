package cm.cetic.mezesse.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cm.cetic.mezesse.config.CeticProperties;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Sessions (RG-04) : 12 h ou 90 jours sans utilisation, renouvelées, fermées au changement de mot de passe. */
class JetonServiceTest {

  static final SecretKey CLE = new SecretKeySpec("une-cle-de-test-de-trente-deux-octets!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  static final CeticProperties PROPRIETES =
      new CeticProperties(
          "secret", new CeticProperties.Session(Duration.ofHours(12), Duration.ofDays(90), Duration.ofHours(1)), "", "password", null, null, null);

  final JetonService jetons = new JetonService(new NimbusJwtEncoder(new ImmutableSecret<>(CLE)), PROPRIETES);
  final JwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(CLE).macAlgorithm(MacAlgorithm.HS256).build();

  static Utilisateur directeur(int version) {
    Utilisateur u = new Utilisateur();
    u.setId("u1");
    u.setRole(Role.PROVISEUR);
    u.setNom("Mbarga");
    u.setPrenom("Paul");
    u.setVersionSession(version);
    return u;
  }

  static Jwt jetonEmisIlYa(Duration age, Object version, Object prolongee) {
    Instant emis = Instant.now().minus(age);
    Jwt.Builder b = Jwt.withTokenValue("x").header("alg", "HS256").claim("id", "u1").issuedAt(emis).expiresAt(emis.plus(Duration.ofDays(90)));
    if (version != null) b.claim(JetonService.VERSION, version);
    if (prolongee != null) b.claim(JetonService.PROLONGEE, prolongee);
    return b.build();
  }

  @Test
  void rester_connecte_donne_une_session_de_quatre_vingt_dix_jours() {
    Jwt j = decodeur.decode(jetons.emettre(directeur(3), true));
    assertThat(Duration.between(j.getIssuedAt(), j.getExpiresAt())).isEqualTo(Duration.ofDays(90));
    assertThat(JetonService.prolongee(j)).isTrue();
    assertThat(JetonService.version(j)).isEqualTo(3);
    assertThat(j.getClaimAsString("role")).isEqualTo("PROVISEUR");
  }

  @Test
  void sans_rester_connecte_la_session_dure_douze_heures() {
    Jwt j = decodeur.decode(jetons.emettre(directeur(0), false));
    assertThat(Duration.between(j.getIssuedAt(), j.getExpiresAt())).isEqualTo(Duration.ofHours(12));
    assertThat(JetonService.prolongee(j)).isFalse();
  }

  @Test
  void un_jeton_de_plus_d_une_heure_est_renouvele() {
    assertThat(jetons.aRenouveler(jetonEmisIlYa(Duration.ofMinutes(10), 0, true))).isFalse();
    assertThat(jetons.aRenouveler(jetonEmisIlYa(Duration.ofMinutes(61), 0, true))).isTrue();
    assertThat(jetons.aRenouveler(jetonEmisIlYa(Duration.ofDays(40), 0, true))).isTrue();
  }

  @Test
  void le_renouvellement_garde_la_duree_et_la_version_et_repart_de_maintenant() {
    UtilisateurConnecte moi = new UtilisateurConnecte("u1", Role.PROVISEUR, "Mbarga", "Paul");
    for (boolean prolongee : new boolean[] {true, false}) {
      Jwt neuf = decodeur.decode(jetons.renouveler(moi, jetonEmisIlYa(Duration.ofDays(40), 2, prolongee)));
      assertThat(JetonService.prolongee(neuf)).isEqualTo(prolongee);
      assertThat(JetonService.version(neuf)).isEqualTo(2);
      assertThat(neuf.getIssuedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
      assertThat(neuf.getExpiresAt()).isCloseTo(Instant.now().plus(prolongee ? Duration.ofDays(90) : Duration.ofHours(12)), within(5, ChronoUnit.SECONDS));
    }
  }

  @Test
  void les_jetons_emis_avant_le_changement_sont_prolonges_en_version_zero() {
    Jwt ancien = jetonEmisIlYa(Duration.ofHours(2), null, null);
    assertThat(JetonService.prolongee(ancien)).isTrue();
    assertThat(JetonService.version(ancien)).isZero();
  }

  @Test
  void un_jeton_d_une_version_anterieure_est_refuse() {
    UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    when(utilisateurs.findById("u1")).thenReturn(Optional.of(directeur(1)));
    ConvertisseurJeton convertisseur = new ConvertisseurJeton(utilisateurs);

    assertThat(convertisseur.convert(jetonEmisIlYa(Duration.ZERO, 1, true)).getName()).isEqualTo("u1");
    assertThatThrownBy(() -> convertisseur.convert(jetonEmisIlYa(Duration.ZERO, 0, true))).isInstanceOf(BadCredentialsException.class);
    assertThatThrownBy(() -> convertisseur.convert(jetonEmisIlYa(Duration.ZERO, null, null))).isInstanceOf(BadCredentialsException.class);
  }
}
