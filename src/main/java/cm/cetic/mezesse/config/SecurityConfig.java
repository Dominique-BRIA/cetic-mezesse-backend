package cm.cetic.mezesse.config;

import cm.cetic.mezesse.security.ConvertisseurJeton;
import cm.cetic.mezesse.security.LecteurJeton;
import cm.cetic.mezesse.security.LimiteConnexionFilter;
import cm.cetic.mezesse.security.ReponsesSecurite;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Sécurité de l'API : sans session (jeton JWT à chaque requête), CORS limité aux origines déclarées,
 * en-têtes de protection, et rôles vérifiés sur chaque route par @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String CSP =
      "default-src 'self';base-uri 'self';font-src 'self' https: data:;form-action 'self';frame-ancestors 'self';"
          + "img-src 'self' data:;object-src 'none';script-src 'self';script-src-attr 'none';"
          + "style-src 'self' https: 'unsafe-inline';upgrade-insecure-requests";

  @Bean
  SecurityFilterChain filtres(
      HttpSecurity http, ConvertisseurJeton convertisseur, ReponsesSecurite reponses, CorsConfigurationSource cors)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(c -> c.configurationSource(cors))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/sante").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/connexion").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated())
        .oauth2ResourceServer(
            o ->
                o.bearerTokenResolver(new LecteurJeton())
                    .authenticationEntryPoint(reponses)
                    .accessDeniedHandler(reponses)
                    .jwt(j -> j.jwtAuthenticationConverter(convertisseur)))
        .exceptionHandling(e -> e.authenticationEntryPoint(reponses).accessDeniedHandler(reponses))
        .headers(
            h ->
                h.contentSecurityPolicy(c -> c.policyDirectives(CSP))
                    .frameOptions(f -> f.sameOrigin())
                    .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                    .crossOriginOpenerPolicy(c -> c.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                    .crossOriginResourcePolicy(c -> c.policy(CrossOriginResourcePolicy.CROSS_ORIGIN))
                    .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
        .addFilterBefore(new LimiteConnexionFilter(reponses), BearerTokenAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource cors(CeticProperties proprietes) {
    CorsConfiguration c = new CorsConfiguration();
    List<String> origines = proprietes.originesAutorisees();
    if (origines.isEmpty()) c.addAllowedOriginPattern("*");
    else c.setAllowedOrigins(origines);
    c.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"));
    c.addAllowedHeader("*");
    c.setExposedHeaders(List.of("Content-Disposition", "X-Cartes-Ecartees"));
    c.setMaxAge(600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }

  @Bean
  SecretKey cleJeton(CeticProperties proprietes) {
    if (proprietes.jwtSecret() == null || proprietes.jwtSecret().isBlank()) {
      throw new IllegalStateException("Variable d'environnement manquante : JWT_SECRET");
    }
    return new SecretKeySpec(proprietes.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtDecoder decodeurJeton(SecretKey cle) {
    return NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  JwtEncoder encodeurJeton(SecretKey cle) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(cle));
  }

  /** bcrypt, coût 10 : les empreintes créées par l'ancienne API ($2b$) restent valides. */
  @Bean
  PasswordEncoder encodeurMotDePasse() {
    return new BCryptPasswordEncoder(10);
  }
}
