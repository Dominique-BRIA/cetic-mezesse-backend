package cm.cetic.mezesse.service;

import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AppareilRepository;
import cm.cetic.mezesse.repository.JetonWebRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notifications push (Firebase Cloud Messaging, API HTTP v1). Chaque notification enregistrée est aussi
 * envoyée aux téléphones du destinataire. L'envoi part après la validation de la transaction et en
 * arrière-plan : un Firebase lent ou injoignable ne retarde ni n'annule aucune action, et la cloche de
 * l'application reste la référence. Sans compte de service (variable FIREBASE_CREDENTIALS), rien n'est
 * envoyé.
 */
@Slf4j
@Service
public class PushService {

  private static final String PORTEE = "https://www.googleapis.com/auth/firebase.messaging";
  /** Un téléphone resté hors réseau reçoit encore la notification pendant trois jours. */
  private static final String DUREE_DE_VIE = "259200s";
  public static final String CANAL = "cetic_notifications";

  /** Compte de service Firebase (fichier JSON téléchargé dans la console Firebase). */
  record Compte(String projet, String email, String cleId, PrivateKey cle, String urlJeton) {}

  private record Acces(String jeton, Instant expire) {}

  private final AppareilRepository appareils;
  private final JetonWebRepository jetonsWeb;
  private final ObjectMapper json;
  private final Compte compte;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ExecutorService envois = Executors.newVirtualThreadPerTaskExecutor();
  private Acces acces;

  public PushService(AppareilRepository appareils, JetonWebRepository jetonsWeb, ObjectMapper json, @Value("${FIREBASE_CREDENTIALS:}") String identifiants) {
    this.appareils = appareils;
    this.jetonsWeb = jetonsWeb;
    this.json = json;
    this.compte = lireCompte(json, identifiants);
    if (compte == null) log.info("Notifications push désactivées : FIREBASE_CREDENTIALS absent ou illisible.");
    else log.info("Notifications push actives (projet Firebase {}).", compte.projet());
  }

  public boolean actif() {
    return compte != null;
  }

  /** Envoie aux téléphones des comptes donnés, une fois la transaction en cours validée. */
  public void envoyerApresValidation(Collection<String> utilisateurIds, String titre, String message, String lien) {
    if (compte == null || utilisateurIds.isEmpty()) return;
    List<String> ids = List.copyOf(utilisateurIds);
    Runnable envoi = () -> envois.submit(() -> envoyer(ids, titre, message, lien));
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              envoi.run();
            }
          });
    } else {
      envoi.run();
    }
  }

  void envoyer(List<String> utilisateurIds, String titre, String message, String lien) {
    try {
      // Téléphones (« A ») puis navigateurs (« W ») : un jeton périmé est oublié là où il est rangé.
      List<Object[]> jetons = new ArrayList<>();
      for (Object[] j : appareils.jetonsDe(utilisateurIds)) jetons.add(new Object[] {"A", j[0], j[1]});
      for (Object[] j : jetonsWeb.jetonsDe(utilisateurIds)) jetons.add(new Object[] {"W", j[0], j[1]});
      if (jetons.isEmpty()) return;
      String acces = jetonAcces();
      int envoyes = 0;
      for (Object[] j : jetons) {
        String id = (String) j[1];
        String jeton = (String) j[2];
        HttpResponse<String> r = envoyerA(acces, jeton, titre, message, lien);
        if (r.statusCode() / 100 == 2) envoyes++;
        else if (jetonPerime(r.statusCode(), r.body())) {
          if ("A".equals(j[0])) appareils.oublierJeton(id, jeton);
          else jetonsWeb.oublier(id);
        }
        else log.warn("Notification push refusée ({}) : {}", r.statusCode(), r.body().length() > 300 ? r.body().substring(0, 300) : r.body());
      }
      log.debug("Notification push « {} » : {}/{} téléphone(s).", titre, envoyes, jetons.size());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.warn("Notification push non envoyée : {}", e.toString());
    }
  }

  private HttpResponse<String> envoyerA(String acces, String jeton, String titre, String corps, String lien) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/" + compte.projet() + "/messages:send"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + acces)
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("message", message(jeton, titre, corps, lien)))))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** Envoi immédiat à un seul téléphone, depuis la console technique : la réponse de Firebase, en clair. */
  public String essai(String appareilId, String jeton, String titre, String corps) {
    if (compte == null) throw ApiException.conflit("Notifications push désactivées : FIREBASE_CREDENTIALS est absent ou illisible dans Azure.");
    HttpResponse<String> r;
    try {
      r = envoyerA(jetonAcces(), jeton, titre, corps, null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ApiException.conflit("Envoi interrompu.");
    } catch (Exception e) {
      throw ApiException.conflit("Envoi impossible : " + e.getMessage());
    }
    if (r.statusCode() / 100 == 2) return "Notification envoyée à ce téléphone.";
    if (jetonPerime(r.statusCode(), r.body())) {
      appareils.oublierJeton(appareilId, jeton);
      throw ApiException.conflit("Firebase ne connaît plus ce téléphone (application désinstallée ou réinstallée) : son jeton est oublié.");
    }
    String detail = r.body().length() > 300 ? r.body().substring(0, 300) : r.body();
    throw ApiException.conflit("Firebase refuse l'envoi (" + r.statusCode() + ") : " + detail);
  }

  /** Message FCM : notification affichée par Android même application fermée, et l'écran à ouvrir. */
  static Map<String, Object> message(String jeton, String titre, String corps, String lien) {
    Map<String, Object> donnees = new LinkedHashMap<>();
    if (lien != null) donnees.put("lien", lien);
    return Map.of(
        "token", jeton,
        "notification", Map.of("title", titre, "body", corps),
        "data", donnees,
        "android", Map.of("priority", "HIGH", "ttl", DUREE_DE_VIE, "notification", Map.of("channel_id", CANAL)));
  }

  /** Téléphone désinstallé ou jeton invalide : inutile de réessayer. */
  static boolean jetonPerime(int statut, String reponse) {
    String r = reponse == null ? "" : reponse;
    return statut == 404 || r.contains("UNREGISTERED") || (statut == 400 && r.contains("registration token"));
  }

  // ---------- Authentification auprès de Google (compte de service, OAuth 2 par assertion signée) ----------

  private synchronized String jetonAcces() throws Exception {
    if (acces != null && Instant.now().isBefore(acces.expire().minusSeconds(120))) return acces.jeton();
    String corps =
        "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8) + "&assertion="
            + assertion(compte, Instant.now());
    HttpResponse<String> r =
        http.send(
            HttpRequest.newBuilder(URI.create(compte.urlJeton()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corps))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (r.statusCode() != 200) throw new IllegalStateException("Google refuse le compte de service (" + r.statusCode() + ") : " + r.body());
    JsonNode j = json.readTree(r.body());
    acces = new Acces(j.path("access_token").asText(), Instant.now().plusSeconds(j.path("expires_in").asLong(3600)));
    return acces.jeton();
  }

  static String assertion(Compte c, Instant maintenant) throws JOSEException {
    JWTClaimsSet revendications =
        new JWTClaimsSet.Builder()
            .issuer(c.email())
            .audience(c.urlJeton())
            .claim("scope", PORTEE)
            .issueTime(Date.from(maintenant))
            .expirationTime(Date.from(maintenant.plusSeconds(3600)))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(c.cleId()).build(), revendications);
    jwt.sign(new RSASSASigner(c.cle()));
    return jwt.serialize();
  }

  /** Le fichier JSON du compte de service, tel quel ou encodé en base 64 (plus simple à coller dans Azure). */
  static Compte lireCompte(ObjectMapper json, String identifiants) {
    if (identifiants == null || identifiants.isBlank()) return null;
    try {
      String texte = identifiants.strip();
      if (!texte.startsWith("{")) texte = new String(Base64.getMimeDecoder().decode(texte), StandardCharsets.UTF_8);
      JsonNode j = json.readTree(texte);
      String pem = j.path("private_key").asText().replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
      PrivateKey cle = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
      String urlJeton = j.path("token_uri").asText("https://oauth2.googleapis.com/token");
      List<String> manquants = new ArrayList<>();
      if (j.path("project_id").asText().isBlank()) manquants.add("project_id");
      if (j.path("client_email").asText().isBlank()) manquants.add("client_email");
      if (!manquants.isEmpty()) throw new IllegalArgumentException("champs manquants : " + manquants);
      return new Compte(j.path("project_id").asText(), j.path("client_email").asText(), j.path("private_key_id").asText(null), cle, urlJeton);
    } catch (Exception e) {
      log.warn("FIREBASE_CREDENTIALS illisible : {}", e.getMessage());
      return null;
    }
  }
}
