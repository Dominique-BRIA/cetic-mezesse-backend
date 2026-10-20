package cm.cetic.mezesse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Prolonge les sessions qui servent (RG-04) : quand le jeton reçu a plus d'une heure, la réponse
 * porte un jeton neuf dans l'en-tête {@code X-Jeton}, que le site et l'application gardent à la place
 * de l'ancien. Une session ne prend ainsi fin qu'après 12 h, ou 90 jours, sans aucune utilisation.
 *
 * <p>Seuls les jetons envoyés dans l'en-tête Authorization sont renouvelés : ceux passés dans
 * l'adresse d'une photo ou d'un PDF ne reviennent pas au site.
 */
public class RenouvellementJetonFilter extends OncePerRequestFilter {

  public static final String ENTETE = "X-Jeton";

  private final JetonService jetons;

  public RenouvellementJetonFilter(JetonService jetons) {
    this.jetons = jetons;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chaine)
      throws ServletException, IOException {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a instanceof AuthentificationJeton j && req.getHeader(HttpHeaders.AUTHORIZATION) != null && jetons.aRenouveler(j.getCredentials())) {
      res.setHeader(ENTETE, jetons.renouveler(j.getPrincipal(), j.getCredentials()));
    }
    chaine.doFilter(req, res);
  }
}
