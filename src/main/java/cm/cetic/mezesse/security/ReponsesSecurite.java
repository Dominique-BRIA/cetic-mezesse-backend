package cm.cetic.mezesse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Réponses JSON de Spring Security : 401 (pas connecté, session expirée…) et 403 (rôle insuffisant). */
@Component
@RequiredArgsConstructor
public class ReponsesSecurite implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper json;

  @Override
  public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e) throws IOException {
    String message;
    if (e instanceof DisabledException) message = "Compte désactivé.";
    else if (e instanceof InvalidBearerTokenException || req.getAttribute(LecteurJeton.ATTRIBUT_JETON) != null) {
      message = "Session expirée, reconnectez-vous.";
    } else message = "Connexion requise.";
    ecrire(res, HttpServletResponse.SC_UNAUTHORIZED, message);
  }

  @Override
  public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException e) throws IOException {
    ecrire(res, HttpServletResponse.SC_FORBIDDEN, "Vous n'avez pas accès à cette ressource.");
  }

  public void ecrire(HttpServletResponse res, int statut, String message) throws IOException {
    res.setStatus(statut);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setCharacterEncoding("UTF-8");
    json.writeValue(res.getOutputStream(), Map.of("message", message));
  }
}
