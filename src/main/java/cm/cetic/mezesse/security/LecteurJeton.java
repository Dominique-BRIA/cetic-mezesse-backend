package cm.cetic.mezesse.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Lit le jeton dans l'en-tête « Authorization: Bearer … », ou à défaut dans le paramètre ?jeton=
 * (photos et PDF ouverts directement par le navigateur, qui ne peut pas ajouter d'en-tête).
 */
public class LecteurJeton implements BearerTokenResolver {

  static final String ATTRIBUT_JETON = LecteurJeton.class.getName() + ".present";

  private final DefaultBearerTokenResolver entete = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest req) {
    String jeton = entete.resolve(req);
    if (jeton == null) {
      String parametre = req.getParameter("jeton");
      jeton = parametre == null || parametre.isBlank() ? null : parametre;
    }
    if (jeton != null) req.setAttribute(ATTRIBUT_JETON, Boolean.TRUE);
    return jeton;
  }
}
