package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.ConsoleDto.Connexion;
import cm.cetic.mezesse.dto.ConsoleDto.Enrolement;
import cm.cetic.mezesse.dto.ConsoleDto.Moi;
import cm.cetic.mezesse.dto.ConsoleDto.ReponseConnexion;
import cm.cetic.mezesse.entity.AdminConsole;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AdminConsoleRepository;
import cm.cetic.mezesse.security.JetonService;
import cm.cetic.mezesse.security.Totp;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connexion à la console technique : mot de passe puis code à usage unique, obligatoire. Cinq échecs
 * verrouillent le compte un quart d'heure. Le premier compte naît de deux variables d'environnement,
 * CONSOLE_ADMIN_EMAIL et CONSOLE_ADMIN_MOT_DE_PASSE, au démarrage de l'API si aucun n'existe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsoleAuthService implements ApplicationRunner {

  private static final int ECHECS_MAX = 5;
  private static final Duration VERROU = Duration.ofMinutes(15);
  /** Comparé quand le compte n'existe pas : la réponse prend le même temps. */
  private static final String EMPREINTE_FICTIVE = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa8e4B8m9Xw0m3yN4xQxk3yZ1m5mWb6e";

  private final AdminConsoleRepository admins;
  private final PasswordEncoder motsDePasse;
  private final JetonService jetons;
  private final JournalConsoleService journal;

  @Value("${CONSOLE_ADMIN_EMAIL:}")
  private String emailInitial;

  @Value("${CONSOLE_ADMIN_MOT_DE_PASSE:}")
  private String motDePasseInitial;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (emailInitial.isBlank() || motDePasseInitial.isBlank() || admins.count() > 0) return;
    AdminConsole a = new AdminConsole();
    a.setEmail(emailInitial.strip().toLowerCase(java.util.Locale.ROOT));
    a.setNom("Développeur");
    a.setMotDePasse(motsDePasse.encode(motDePasseInitial));
    admins.save(a);
    log.info("Compte de la console technique créé : {}", a.getEmail());
  }

  /** Les échecs comptés doivent survivre à l'exception qui les signale : pas d'annulation sur ApiException. */
  @Transactional(noRollbackFor = ApiException.class)
  public ReponseConnexion connexion(Connexion c) {
    AdminConsole a = admins.findByEmail(c.email()).orElse(null);
    if (a == null) {
      motsDePasse.matches(c.motDePasse(), EMPREINTE_FICTIVE);
      journal.noter(c.email(), "connexion.echec", null, "compte inconnu");
      throw refus();
    }
    Instant maintenant = Horloge.maintenant();
    if (a.getVerrouilleJusqua() != null && a.getVerrouilleJusqua().isAfter(maintenant)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Compte verrouillé après plusieurs échecs. Réessayez dans un quart d'heure.");
    }
    if (!motsDePasse.matches(c.motDePasse(), a.getMotDePasse())) {
      echec(a, maintenant, "mot de passe");
      throw refus();
    }
    if (!a.isActif()) throw ApiException.interdit("Ce compte est désactivé.");

    // Première connexion : le secret est donné une fois, tant que le premier code n'est pas confirmé.
    if (!a.isTotpActif() && c.code() == null) {
      if (a.getTotpSecret() == null) a.setTotpSecret(Totp.nouveauSecret());
      return new ReponseConnexion(null, null, new Enrolement(a.getTotpSecret(), uri(a)));
    }
    long pas = a.getTotpSecret() == null ? -1 : Totp.verifier(a.getTotpSecret(), c.code(), maintenant, a.getTotpDernierPas());
    if (pas < 0) {
      echec(a, maintenant, "code de vérification");
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Code de vérification incorrect.");
    }
    a.setTotpActif(true);
    a.setTotpDernierPas(pas);
    a.setEchecs(0);
    a.setVerrouilleJusqua(null);
    journal.noter(a.getEmail(), "connexion", null, null);
    return new ReponseConnexion(jetons.emettreConsole(a), moi(a), null);
  }

  @Transactional(readOnly = true)
  public Moi moi(UtilisateurConnecte moi) {
    return moi(admins.findById(moi.id()).orElseThrow(() -> ApiException.introuvable("Compte")));
  }

  private static Moi moi(AdminConsole a) {
    return new Moi(a.getId(), a.getEmail(), a.getNom());
  }

  private void echec(AdminConsole a, Instant maintenant, String quoi) {
    a.setEchecs(a.getEchecs() + 1);
    if (a.getEchecs() >= ECHECS_MAX) {
      a.setEchecs(0);
      a.setVerrouilleJusqua(maintenant.plus(VERROU));
      journal.noter(a.getEmail(), "connexion.verrouillage", null, quoi);
    } else {
      journal.noter(a.getEmail(), "connexion.echec", null, quoi);
    }
  }

  private static ApiException refus() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect.");
  }

  private static String uri(AdminConsole a) {
    String emetteur = URLEncoder.encode("CETIC Console", StandardCharsets.UTF_8).replace("+", "%20");
    String compte = URLEncoder.encode(a.getEmail(), StandardCharsets.UTF_8);
    return "otpauth://totp/" + emetteur + ":" + compte + "?secret=" + a.getTotpSecret() + "&issuer=" + emetteur + "&algorithm=SHA1&digits=6&period=30";
  }
}
