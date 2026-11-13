package cm.cetic.mezesse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Double envoi d'un même formulaire (double clic, réseau lent qui fait renvoyer) : le client marque
 * chaque formulaire d'un identifiant (en-tête X-Envoi). Le second envoi n'est pas exécuté : il reçoit
 * la réponse du premier, en l'attendant s'il est encore en cours. Seules les réponses réussies sont
 * gardées, dix minutes : un envoi refusé peut être renvoyé, par exemple après confirmation.
 */
public class EnvoiUniqueFilter extends OncePerRequestFilter {

  public static final String ENTETE = "X-Envoi";
  public static final String REJOUE = "X-Envoi-Rejoue";
  private static final long DUREE_MS = Duration.ofMinutes(10).toMillis();
  private static final int MAX = 2_000;

  private record Reponse(int statut, String type, byte[] corps) {}

  private record Envoi(long debut, CompletableFuture<Reponse> resultat) {}

  private final Map<String, Envoi> envois = new ConcurrentHashMap<>();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    String id = req.getHeader(ENTETE);
    return switch (req.getMethod()) {
          case "POST", "PUT", "PATCH", "DELETE" -> false;
          default -> true;
        }
        || id == null
        || id.isBlank()
        || id.length() > 100;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chaine) throws ServletException, IOException {
    long maintenant = System.currentTimeMillis();
    if (envois.size() > MAX) envois.values().removeIf(e -> maintenant - e.debut() > DUREE_MS);
    String cle = cle(req);
    Envoi nouveau = new Envoi(maintenant, new CompletableFuture<>());
    Envoi existant = envois.putIfAbsent(cle, nouveau);
    if (existant != null && maintenant - existant.debut() <= DUREE_MS) {
      Reponse premiere = attendre(existant);
      if (premiere != null) {
        res.setStatus(premiere.statut());
        if (premiere.type() != null) res.setContentType(premiere.type());
        res.setHeader(REJOUE, "oui");
        res.getOutputStream().write(premiere.corps());
        return;
      }
      // Le premier envoi a échoué : celui-ci est traité comme un nouvel essai.
      envois.put(cle, nouveau);
    } else if (existant != null) {
      envois.put(cle, nouveau);
    }

    ContentCachingResponseWrapper enveloppe = new ContentCachingResponseWrapper(res);
    Reponse gardee = null;
    try {
      chaine.doFilter(req, enveloppe);
      int s = enveloppe.getStatus();
      if (s >= 200 && s < 300) gardee = new Reponse(s, enveloppe.getContentType(), enveloppe.getContentAsByteArray());
    } finally {
      if (gardee == null) envois.remove(cle, nouveau);
      nouveau.resultat().complete(gardee);
      enveloppe.copyBodyToResponse();
    }
  }

  private static Reponse attendre(Envoi e) {
    try {
      return e.resultat().get(60, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception ex) {
      return null;
    }
  }

  /** Même formulaire : même identifiant, même route, même compte. */
  private static String cle(HttpServletRequest req) {
    String compte = Objects.toString(req.getHeader("Authorization"), "");
    return req.getMethod() + " " + req.getRequestURI() + "?" + Objects.toString(req.getQueryString(), "") + "|" + req.getHeader(ENTETE) + "|" + empreinte(compte);
  }

  private static String empreinte(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)), 0, 12);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
