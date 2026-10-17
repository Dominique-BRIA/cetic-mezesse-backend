package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.NotificationDto;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Notifications de l'utilisateur connecté (RG-32). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService service;

  @GetMapping
  public NotificationDto.Liste liste(@AuthenticationPrincipal UtilisateurConnecte moi) {
    return service.listeDe(moi.id());
  }

  @PostMapping("/lues")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void toutMarquerLu(@AuthenticationPrincipal UtilisateurConnecte moi) {
    service.toutMarquerLu(moi.id());
  }

  @PostMapping("/{id}/lue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void marquerLue(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    service.marquerLue(id, moi.id());
  }
}
