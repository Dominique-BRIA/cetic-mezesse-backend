package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.AppareilDto.Detachement;
import cm.cetic.mezesse.dto.AppareilDto.Politique;
import cm.cetic.mezesse.dto.AppareilDto.Rapport;
import cm.cetic.mezesse.dto.AppareilDto.Signal;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AppareilService;
import cm.cetic.mezesse.service.PolitiqueMiseAJourService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Routes appelées par l'application mobile pour la console technique. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppareilController {

  private final AppareilService service;
  private final PolitiqueMiseAJourService politique;

  /** À l'ouverture de l'application, par un compte connecté. */
  @PostMapping("/appareils/signal")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void signal(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Signal d) {
    service.signal(moi, d);
  }

  /** À la déconnexion, par le compte qui se déconnecte. */
  @PostMapping("/appareils/detacher")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void detacher(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Detachement d) {
    service.detacher(moi, d.installationId());
  }

  /** Publique et limitée par adresse : l'application peut se fermer avant toute connexion. */
  @PostMapping("/appareils/plantages")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void plantage(@Valid @RequestBody Rapport d) {
    service.plantage(d);
  }

  /** Publique : lue avant de télécharger une nouvelle version, connecté ou non, et par la tâche de fond. */
  @GetMapping("/mises-a-jour/politique")
  public Politique politique() {
    return politique.lire();
  }
}
