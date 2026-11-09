package cm.cetic.mezesse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite les tentatives de connexion : 20 par adresse IP et par fenêtre de 15 minutes, contre les
 * essais de mots de passe à la chaîne. Les rapports de plantage, publics, ont leur propre compteur.
 */
public class LimiteConnexionFilter extends OncePerRequestFilter {

  private static final int LIMITE = 20;
  private static final int LIMITE_PLANTAGES = 60;
  private static final long FENETRE_MS = Duration.ofMinutes(15).toMillis();

  private final ReponsesSecurite reponses;
  private final Map<String, Fenetre> compteurs = new ConcurrentHashMap<>();

  public LimiteConnexionFilter(ReponsesSecurite reponses) {
    this.reponses = reponses;
  }

  private record Fenetre(long debut, int nombre) {}

  /** Nombre de requêtes permis par fenêtre ; 0 pour une route sans limite. */
  private static int limite(HttpServletRequest req) {
    if (!"POST".equals(req.getMethod())) return 0;
    return switch (req.getRequestURI()) {
      case "/api/auth/connexion", "/api/console/auth/connexion" -> LIMITE;
      case "/api/appareils/plantages" -> LIMITE_PLANTAGES;
      default -> 0;
    };
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    return limite(req) == 0;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chaine)
      throws ServletException, IOException {
    int limite = limite(req);
    long maintenant = System.currentTimeMillis();
    if (compteurs.size() > 10_000) compteurs.values().removeIf(f -> maintenant - f.debut() >= FENETRE_MS);
    Fenetre f =
        compteurs.compute(
            req.getRequestURI() + "|" + adresse(req),
            (cle, actuelle) ->
                actuelle == null || maintenant - actuelle.debut() >= FENETRE_MS
                    ? new Fenetre(maintenant, 1)
                    : new Fenetre(actuelle.debut(), actuelle.nombre() + 1));
    long reset = Math.max(0, (f.debut() + FENETRE_MS - maintenant) / 1000);
    res.setHeader("RateLimit-Limit", String.valueOf(limite));
    res.setHeader("RateLimit-Remaining", String.valueOf(Math.max(0, limite - f.nombre())));
    res.setHeader("RateLimit-Reset", String.valueOf(reset));
    if (f.nombre() > limite) {
      res.setHeader("Retry-After", String.valueOf(reset));
      reponses.ecrire(res, 429, "Trop de tentatives de connexion. Réessayez dans quelques minutes.");
      return;
    }
    chaine.doFilter(req, res);
  }

  /**
   * Adresse du client. Derrière le proxy d'Azure, c'est la dernière entrée de X-Forwarded-For (celle
   * ajoutée par le proxy) : les précédentes peuvent être inventées par le client.
   */
  public static String adresse(HttpServletRequest req) {
    String transmis = req.getHeader("X-Forwarded-For");
    if (transmis != null && !transmis.isBlank()) {
      String[] adresses = transmis.split(",");
      return adresses[adresses.length - 1].trim();
    }
    return req.getRemoteAddr();
  }
}
