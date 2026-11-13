package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.VerificationDto.Ignorer;
import cm.cetic.mezesse.dto.VerificationDto.Rapport;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Page « À vérifier » de la direction. */
@RestController
@RequestMapping("/api/verifications")
@RequiredArgsConstructor
public class VerificationController {

  private final VerificationService service;
  private final AnneeCouranteService annee;

  /** @param ignores true : inclure les points marqués « c'est normal » */
  @GetMapping
  @Direction
  public Rapport rapport(@RequestParam(required = false) String anneeId, @RequestParam(defaultValue = "false") boolean ignores) {
    return service.rapport(annee.anneeDe(anneeId), ignores);
  }

  @PostMapping("/ignorees")
  @Direction
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ignorer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Ignorer d) {
    service.ignorer(moi, d);
  }

  @DeleteMapping("/ignorees")
  @Direction
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retablir(@RequestParam String cle) {
    service.retablir(cle);
  }
}
