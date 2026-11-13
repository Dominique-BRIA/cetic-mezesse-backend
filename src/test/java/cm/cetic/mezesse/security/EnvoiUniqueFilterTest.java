package cm.cetic.mezesse.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Un formulaire envoyé deux fois n'est enregistré qu'une fois. */
class EnvoiUniqueFilterTest {

  final EnvoiUniqueFilter filtre = new EnvoiUniqueFilter();
  final AtomicInteger executions = new AtomicInteger();

  FilterChain chaine(int statut) {
    return (req, res) -> {
      int n = executions.incrementAndGet();
      ((HttpServletResponse) res).setStatus(statut);
      res.setContentType("application/json");
      res.getOutputStream().write(("{\"n\":" + n + "}").getBytes(StandardCharsets.UTF_8));
    };
  }

  static MockHttpServletRequest requete(String envoi) {
    MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/utilisateurs");
    r.addHeader("Authorization", "Bearer a");
    if (envoi != null) r.addHeader(EnvoiUniqueFilter.ENTETE, envoi);
    return r;
  }

  @Test
  void le_second_envoi_recoit_la_reponse_du_premier_sans_etre_execute() throws Exception {
    MockHttpServletResponse premiere = new MockHttpServletResponse();
    filtre.doFilter(requete("f1"), premiere, chaine(201));
    MockHttpServletResponse seconde = new MockHttpServletResponse();
    filtre.doFilter(requete("f1"), seconde, chaine(201));
    assertThat(executions).hasValue(1);
    assertThat(seconde.getStatus()).isEqualTo(201);
    assertThat(seconde.getContentAsString()).isEqualTo("{\"n\":1}");
    assertThat(seconde.getHeader(EnvoiUniqueFilter.REJOUE)).isEqualTo("oui");
    assertThat(premiere.getContentAsString()).isEqualTo("{\"n\":1}");
  }

  @Test
  void un_envoi_refuse_peut_etre_renvoye() throws Exception {
    filtre.doFilter(requete("f2"), new MockHttpServletResponse(), chaine(409));
    MockHttpServletResponse seconde = new MockHttpServletResponse();
    filtre.doFilter(requete("f2"), seconde, chaine(201));
    assertThat(executions).hasValue(2);
    assertThat(seconde.getStatus()).isEqualTo(201);
  }

  @Test
  void formulaires_differents_ou_sans_identifiant_toujours_executes() throws Exception {
    filtre.doFilter(requete("a"), new MockHttpServletResponse(), chaine(201));
    filtre.doFilter(requete("b"), new MockHttpServletResponse(), chaine(201));
    filtre.doFilter(requete(null), new MockHttpServletResponse(), chaine(201));
    filtre.doFilter(requete(null), new MockHttpServletResponse(), chaine(201));
    assertThat(executions).hasValue(4);
  }

  @Test
  void un_autre_compte_avec_le_meme_identifiant_n_est_pas_confondu() throws Exception {
    filtre.doFilter(requete("f3"), new MockHttpServletResponse(), chaine(201));
    MockHttpServletRequest autre = requete("f3");
    autre.removeHeader("Authorization");
    autre.addHeader("Authorization", "Bearer b");
    filtre.doFilter(autre, new MockHttpServletResponse(), chaine(201));
    assertThat(executions).hasValue(2);
  }
}
