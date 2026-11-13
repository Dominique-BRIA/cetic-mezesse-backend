package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.EmploiDuTempsDto.CreneauRequete;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.NouvelleVersion;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Vue;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.EmploiDuTempsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Emploi du temps des classes : le SG le tient, le Proviseur le consulte. */
@RestController
@RequestMapping("/api/emplois-du-temps")
@RequiredArgsConstructor
public class EmploiDuTempsController {

  private final EmploiDuTempsService service;

  @GetMapping
  @Direction
  public Vue vue(@RequestParam String classeId, @RequestParam(required = false) String versionId) {
    return service.vue(classeId, versionId);
  }

  @PostMapping("/versions")
  @SurveillantGeneral
  public Vue creerVersion(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody NouvelleVersion d) {
    return service.creerVersion(moi, d);
  }

  @DeleteMapping("/versions/{id}")
  @SurveillantGeneral
  public Vue supprimerVersion(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    return service.supprimerVersion(moi, id);
  }

  @PostMapping("/creneaux")
  @SurveillantGeneral
  public Vue ajouterCreneau(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody CreneauRequete d) {
    return service.ajouterCreneau(moi, d);
  }

  @PutMapping("/creneaux/{id}")
  @SurveillantGeneral
  public Vue modifierCreneau(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody CreneauRequete d) {
    return service.modifierCreneau(moi, id, d);
  }

  @DeleteMapping("/creneaux/{id}")
  @SurveillantGeneral
  public Vue supprimerCreneau(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    return service.supprimerCreneau(moi, id);
  }
}
