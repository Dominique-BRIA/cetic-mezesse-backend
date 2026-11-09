package cm.cetic.mezesse.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Codes à usage unique de la double authentification (RFC 6238 : HMAC-SHA1, 6 chiffres, pas de 30 s),
 * compatibles avec les applications d'authentification courantes.
 */
public final class Totp {

  private Totp() {}

  private static final int PAS = 30;
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final SecureRandom HASARD = new SecureRandom();

  /** Secret de 160 bits, en base 32. */
  public static String nouveauSecret() {
    byte[] octets = new byte[20];
    HASARD.nextBytes(octets);
    return base32(octets);
  }

  public static long pas(Instant instant) {
    return instant.getEpochSecond() / PAS;
  }

  /** Code attendu pour un pas donné, sur 6 chiffres. */
  public static String code(String secret, long pas) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(decoder(secret), "HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(pas).array());
      int decalage = h[h.length - 1] & 0x0f;
      int valeur = ByteBuffer.wrap(h, decalage, 4).getInt() & 0x7fffffff;
      return String.format("%06d", valeur % 1_000_000);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Vérifie un code avec une tolérance d'un pas de part et d'autre (horloge du téléphone).
   *
   * @return le pas reconnu, à retenir pour refuser un second usage ; -1 si le code est faux ou déjà utilisé
   */
  public static long verifier(String secret, String code, Instant maintenant, long dernierPas) {
    if (code == null || !code.matches("\\d{6}")) return -1;
    long courant = pas(maintenant);
    long trouve = -1;
    // Le parcours va à son terme : la durée ne dit rien de la position du bon pas.
    for (long p = courant - 1; p <= courant + 1; p++) {
      if (MessageDigest.isEqual(code(secret, p).getBytes(), code.getBytes()) && p > dernierPas) trouve = p;
    }
    return trouve;
  }

  static String base32(byte[] octets) {
    StringBuilder s = new StringBuilder();
    int tampon = 0;
    int bits = 0;
    for (byte b : octets) {
      tampon = (tampon << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        s.append(ALPHABET.charAt((tampon >> (bits - 5)) & 31));
        bits -= 5;
      }
    }
    if (bits > 0) s.append(ALPHABET.charAt((tampon << (5 - bits)) & 31));
    return s.toString();
  }

  static byte[] decoder(String texte) {
    ByteBuffer sortie = ByteBuffer.allocate(texte.length() * 5 / 8);
    int tampon = 0;
    int bits = 0;
    for (char c : texte.toCharArray()) {
      int v = ALPHABET.indexOf(c);
      if (v < 0) throw new IllegalArgumentException("Secret invalide");
      tampon = (tampon << 5) | v;
      bits += 5;
      if (bits >= 8) {
        sortie.put((byte) (tampon >> (bits - 8)));
        bits -= 8;
      }
    }
    return sortie.array();
  }
}
