package cm.cetic.mezesse.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres propres à l'application (section « cetic » de application.yml). */
@ConfigurationProperties(prefix = "cetic")
public record CeticProperties(
    String jwtSecret,
    Duration dureeJeton,
    String corsOrigins,
    String motDePasseReinitialise,
    String dossierPhotos,
    B2 b2,
    Amorcage amorcage) {

  public record B2(String endpoint, String keyId, String applicationKey, String bucket) {
    public boolean configure() {
      return nonVide(endpoint) && nonVide(keyId) && nonVide(applicationKey) && nonVide(bucket);
    }
  }

  public record Amorcage(String proviseurTelephone, String sgTelephone, String motDePasse) {}

  public List<String> originesAutorisees() {
    if (corsOrigins == null) return List.of();
    return Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static boolean nonVide(String s) {
    return s != null && !s.isBlank();
  }
}
