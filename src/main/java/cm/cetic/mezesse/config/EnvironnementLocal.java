package cm.cetic.mezesse.config;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Prépare l'environnement avant le démarrage de Spring :
 *
 * <ul>
 *   <li>lit le fichier .env (développement local), sans écraser les vraies variables d'environnement ;
 *   <li>convertit DATABASE_URL et DIRECT_URL (format postgresql:// de Prisma, partagé avec l'API Node)
 *       en paramètres JDBC pour la source de données et pour Flyway.
 * </ul>
 */
public class EnvironnementLocal implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    Map<String, Object> dotenv = lireDotenv(Path.of(".env"));
    if (!dotenv.isEmpty()) env.getPropertySources().addLast(new MapPropertySource("dotenv", dotenv));

    Map<String, Object> jdbc = new HashMap<>();
    String base = env.getProperty("DATABASE_URL");
    if (base != null && base.startsWith("postgres")) {
      Connexion c = Connexion.depuis(base);
      jdbc.put("spring.datasource.url", c.url());
      jdbc.put("spring.datasource.username", c.utilisateur());
      jdbc.put("spring.datasource.password", c.motDePasse());
    }
    // Flyway passe par la connexion directe (sans le pooler de Neon) quand elle existe.
    String directe = env.getProperty("DIRECT_URL");
    String migrations = directe != null && directe.startsWith("postgres") ? directe : base;
    if (migrations != null && migrations.startsWith("postgres")) {
      Connexion c = Connexion.depuis(migrations);
      jdbc.put("spring.flyway.url", c.url());
      jdbc.put("spring.flyway.user", c.utilisateur());
      jdbc.put("spring.flyway.password", c.motDePasse());
    }
    if (!jdbc.isEmpty()) env.getPropertySources().addFirst(new MapPropertySource("jdbc-depuis-prisma", jdbc));
  }

  /** Fichier KEY=valeur ; les guillemets autour de la valeur sont retirés, les commentaires ignorés. */
  static Map<String, Object> lireDotenv(Path fichier) {
    Map<String, Object> valeurs = new LinkedHashMap<>();
    if (!Files.isRegularFile(fichier)) return valeurs;
    try {
      for (String ligne : Files.readAllLines(fichier, StandardCharsets.UTF_8)) {
        String l = ligne.strip();
        if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
        String cle = l.substring(0, l.indexOf('=')).strip();
        String valeur = l.substring(l.indexOf('=') + 1).strip();
        if (valeur.length() >= 2
            && (valeur.startsWith("\"") && valeur.endsWith("\"") || valeur.startsWith("'") && valeur.endsWith("'"))) {
          valeur = valeur.substring(1, valeur.length() - 1);
        }
        if (System.getenv(cle) == null) valeurs.put(cle, valeur);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Lecture du fichier .env impossible", e);
    }
    return valeurs;
  }

  /** postgresql://utilisateur:motdepasse@hote:port/base?options → URL JDBC et identifiants. */
  record Connexion(String url, String utilisateur, String motDePasse) {

    // Options propres à Prisma, sans équivalent JDBC.
    private static final List<String> IGNOREES = List.of("schema", "pgbouncer", "connection_limit", "pool_timeout", "socket_timeout");

    static Connexion depuis(String brute) {
      URI uri = URI.create(brute.replaceFirst("^postgres(ql)?://", "http://"));
      String utilisateur = null;
      String motDePasse = null;
      String info = uri.getRawUserInfo();
      if (info != null) {
        int i = info.indexOf(':');
        utilisateur = decoder(i < 0 ? info : info.substring(0, i));
        motDePasse = i < 0 ? null : decoder(info.substring(i + 1));
      }
      StringBuilder url = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
      if (uri.getPort() > 0) url.append(':').append(uri.getPort());
      url.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());

      Map<String, String> options = new LinkedHashMap<>();
      boolean pooler = uri.getHost().contains("-pooler");
      if (uri.getRawQuery() != null) {
        for (String paire : uri.getRawQuery().split("&")) {
          if (paire.isEmpty()) continue;
          String[] kv = paire.split("=", 2);
          String cle = kv[0];
          String valeur = kv.length > 1 ? kv[1] : "";
          if (cle.equals("pgbouncer") && valeur.equals("true")) pooler = true;
          if (cle.equals("schema") && !valeur.equals("public")) options.put("currentSchema", valeur);
          if (cle.equals("connect_timeout")) options.put("connectTimeout", valeur);
          else if (!IGNOREES.contains(cle)) options.put(cle.equals("channel_binding") ? "channelBinding" : cle, valeur);
        }
      }
      // Le pooler de Neon (PgBouncer en mode transaction) ne garde pas les requêtes préparées.
      if (pooler) options.put("prepareThreshold", "0");
      if (!options.isEmpty()) {
        url.append('?');
        options.forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
        url.setLength(url.length() - 1);
      }
      return new Connexion(url.toString(), utilisateur, motDePasse);
    }

    private static String decoder(String s) {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
  }
}
