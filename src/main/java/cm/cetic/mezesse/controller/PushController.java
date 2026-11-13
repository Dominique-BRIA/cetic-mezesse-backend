package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.entity.JetonWeb;
import cm.cetic.mezesse.repository.JetonWebRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import cm.cetic.mezesse.util.Textes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Notifications push du site : chaque navigateur qui les accepte enregistre son jeton pour le compte connecté. */
@RestController
@RequestMapping("/api/push/web")
@RequiredArgsConstructor
public class PushController {

  private final JetonWebRepository jetons;
  private final UtilisateurRepository utilisateurs;

  public record Abonnement(
      @NotBlank(message = "obligatoire") @Size(max = 4096, message = "4096 caractères au plus") String jeton,
      @Size(max = 120, message = "120 caractères au plus") String navigateur) {}

  /** À chaque ouverture du site, notifications acceptées : le jeton est (re)rattaché au compte connecté. */
  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void abonner(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Abonnement d) {
    JetonWeb j = jetons.findByJeton(d.jeton()).orElseGet(JetonWeb::new);
    j.setJeton(d.jeton());
    j.setUtilisateur(utilisateurs.getReferenceById(moi.id()));
    j.setNavigateur(Textes.optionnel(d.navigateur()));
    j.setVuLe(Horloge.maintenant());
    jetons.save(j);
  }

  /** À la déconnexion. */
  @PostMapping("/retirer")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void retirer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Abonnement d) {
    jetons.retirer(d.jeton(), moi.id());
  }
}
