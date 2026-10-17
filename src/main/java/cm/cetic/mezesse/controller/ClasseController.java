package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.ClasseDto.Requete;
import cm.cetic.mezesse.dto.ClasseDto.SpecialiteRequete;
import cm.cetic.mezesse.dto.ClasseDto.SpecialiteVue;
import cm.cetic.mezesse.dto.ClasseDto.Vue;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.ClasseService;
import cm.cetic.mezesse.service.SpecialiteService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClasseController {

  private final ClasseService classes;
  private final SpecialiteService specialites;
  private final AnneeCouranteService annee;

  @GetMapping("/classes")
  public List<Vue> liste(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId) {
    return classes.liste(moi, annee.anneeDe(anneeId));
  }

  @GetMapping("/classes/{id}")
  @Direction
  public Vue une(@PathVariable String id) {
    return classes.une(id);
  }

  @PostMapping("/classes")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public Vue creer(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId, @Valid @RequestBody Requete d) {
    return classes.creer(moi, annee.anneeDe(anneeId), d);
  }

  @PutMapping("/classes/{id}")
  @SurveillantGeneral
  public Vue modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Requete d) {
    return classes.modifier(moi, id, d);
  }

  @DeleteMapping("/classes/{id}")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void supprimer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    classes.supprimer(moi, id);
  }

  @GetMapping("/specialites")
  @Direction
  public List<SpecialiteVue> specialites() {
    return specialites.liste();
  }

  @PostMapping("/specialites")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public SpecialiteVue creerSpecialite(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody SpecialiteRequete d) {
    return specialites.creer(moi, d);
  }

  @PutMapping("/specialites/{id}")
  @SurveillantGeneral
  public SpecialiteVue modifierSpecialite(
      @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody SpecialiteRequete d) {
    return specialites.modifier(moi, id, d);
  }

  @DeleteMapping("/specialites/{id}")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void supprimerSpecialite(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    specialites.supprimer(moi, id);
  }
}
