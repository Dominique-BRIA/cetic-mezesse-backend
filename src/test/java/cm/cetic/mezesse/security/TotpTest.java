package cm.cetic.mezesse.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Double authentification de la console technique. */
class TotpTest {

  // Secret ASCII « 12345678901234567890 » de la RFC 6238, en base 32.
  static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  @Test
  void vecteurs_de_la_rfc_6238() {
    assertThat(Totp.code(SECRET, Totp.pas(Instant.ofEpochSecond(59)))).isEqualTo("287082");
    assertThat(Totp.code(SECRET, Totp.pas(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
    assertThat(Totp.code(SECRET, Totp.pas(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
  }

  @Test
  void base32_aller_retour() {
    String secret = Totp.nouveauSecret();
    assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    assertThat(Totp.base32(Totp.decoder(secret))).isEqualTo(secret);
  }

  @Test
  void accepte_le_pas_courant_et_ses_voisins_seulement() {
    Instant t = Instant.ofEpochSecond(1_800_000_000);
    long p = Totp.pas(t);
    assertThat(Totp.verifier(SECRET, Totp.code(SECRET, p), t, 0)).isEqualTo(p);
    assertThat(Totp.verifier(SECRET, Totp.code(SECRET, p - 1), t, 0)).isEqualTo(p - 1);
    assertThat(Totp.verifier(SECRET, Totp.code(SECRET, p + 1), t, 0)).isEqualTo(p + 1);
    assertThat(Totp.verifier(SECRET, Totp.code(SECRET, p - 2), t, 0)).isEqualTo(-1);
  }

  @Test
  void un_code_deja_utilise_ne_resert_pas() {
    Instant t = Instant.ofEpochSecond(1_800_000_000);
    long p = Totp.pas(t);
    assertThat(Totp.verifier(SECRET, Totp.code(SECRET, p), t, p)).isEqualTo(-1);
  }

  @Test
  void refuse_un_code_mal_forme() {
    Instant t = Instant.ofEpochSecond(1_800_000_000);
    assertThat(Totp.verifier(SECRET, null, t, 0)).isEqualTo(-1);
    assertThat(Totp.verifier(SECRET, "12345", t, 0)).isEqualTo(-1);
    assertThat(Totp.verifier(SECRET, "abcdef", t, 0)).isEqualTo(-1);
  }
}
