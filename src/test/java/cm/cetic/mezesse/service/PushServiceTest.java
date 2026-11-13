package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.service.PushService.Compte;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Notifications push : compte de service, assertion signée, message FCM, jetons périmés. */
class PushServiceTest {

  final ObjectMapper json = new ObjectMapper();

  static KeyPair cles() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  String compteJson(KeyPair k) throws Exception {
    String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(k.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
    return json.writeValueAsString(
        Map.of(
            "type", "service_account", "project_id", "cetic-mezesse", "private_key_id", "cle1", "private_key", pem,
            "client_email", "push@cetic-mezesse.iam.gserviceaccount.com", "token_uri", "https://oauth2.googleapis.com/token"));
  }

  @Test
  void lit_le_compte_tel_quel_ou_en_base_64() throws Exception {
    String brut = compteJson(cles());
    Compte a = PushService.lireCompte(json, brut);
    Compte b = PushService.lireCompte(json, Base64.getEncoder().encodeToString(brut.getBytes(StandardCharsets.UTF_8)));
    assertThat(a.projet()).isEqualTo("cetic-mezesse");
    assertThat(b.email()).isEqualTo("push@cetic-mezesse.iam.gserviceaccount.com");
  }

  @Test
  void sans_compte_ou_compte_illisible_rien_n_est_envoye() {
    assertThat(PushService.lireCompte(json, "")).isNull();
    assertThat(PushService.lireCompte(json, "{\"project_id\":\"x\"}")).isNull();
    assertThat(PushService.lireCompte(json, "pas du json")).isNull();
  }

  @Test
  void l_assertion_est_signee_par_la_cle_du_compte_et_porte_les_bonnes_informations() throws Exception {
    KeyPair k = cles();
    Compte c = PushService.lireCompte(json, compteJson(k));
    SignedJWT jwt = SignedJWT.parse(PushService.assertion(c, Instant.ofEpochSecond(1_800_000_000)));
    assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) k.getPublic()))).isTrue();
    assertThat(jwt.getHeader().getKeyID()).isEqualTo("cle1");
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("push@cetic-mezesse.iam.gserviceaccount.com");
    assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://oauth2.googleapis.com/token");
    assertThat(jwt.getJWTClaimsSet().getStringClaim("scope")).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
    assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(Instant.ofEpochSecond(1_800_003_600));
  }

  @Test
  void le_message_porte_le_texte_l_ecran_a_ouvrir_et_la_priorite() throws Exception {
    String m = json.writeValueAsString(PushService.message("jeton1", "Séquence 2 ouverte", "La saisie est ouverte.", "/saisie"));
    assertThat(m)
        .contains("\"token\":\"jeton1\"")
        .contains("\"title\":\"Séquence 2 ouverte\"")
        .contains("\"lien\":\"/saisie\"")
        .contains("\"priority\":\"HIGH\"")
        .contains("\"channel_id\":\"" + PushService.CANAL + "\"");
  }

  @Test
  void reconnait_les_jetons_perimes() {
    assertThat(PushService.jetonPerime(404, "{\"error\":{\"status\":\"NOT_FOUND\"}}")).isTrue();
    assertThat(PushService.jetonPerime(400, "{\"error\":{\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}")).isTrue();
    assertThat(PushService.jetonPerime(400, "The registration token is not a valid FCM registration token")).isTrue();
    assertThat(PushService.jetonPerime(500, "erreur interne")).isFalse();
    assertThat(PushService.jetonPerime(401, "jeton d'accès expiré")).isFalse();
  }
}
