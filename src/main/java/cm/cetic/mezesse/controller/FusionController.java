package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.FusionDto.ApercuEleves;
import cm.cetic.mezesse.dto.FusionDto.ApercuEnseignants;
import cm.cetic.mezesse.dto.FusionDto.DemandeEleves;
import cm.cetic.mezesse.dto.FusionDto.DemandeEnseignants;
import cm.cetic.mezesse.dto.FusionDto.Resultat;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.FusionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fusion de fiches en double : aperçu (GET) puis exécution (POST). Les comptes relèvent du SG, les élèves de la direction. */
@RestController
@RequestMapping("/api/fusions")
@RequiredArgsConstructor
public class FusionController {

  private final FusionService service;

  @GetMapping("/enseignants")
  @SurveillantGeneral
  public ApercuEnseignants apercuEnseignants(@RequestParam String garde, @RequestParam String autre) {
    return service.apercuEnseignants(garde, autre);
  }

  @PostMapping("/enseignants")
  @SurveillantGeneral
  public Resultat fusionnerEnseignants(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody DemandeEnseignants d) {
    return service.fusionnerEnseignants(moi, d);
  }

  @GetMapping("/eleves")
  @Direction
  public ApercuEleves apercuEleves(@RequestParam String garde, @RequestParam String autre) {
    return service.apercuEleves(garde, autre);
  }

  @PostMapping("/eleves")
  @Direction
  public Resultat fusionnerEleves(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody DemandeEleves d) {
    return service.fusionnerEleves(moi, d);
  }
}
