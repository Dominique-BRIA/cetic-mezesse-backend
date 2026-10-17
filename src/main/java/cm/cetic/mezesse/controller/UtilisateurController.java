package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.Message;
import cm.cetic.mezesse.dto.UtilisateurDto.Enseignant;
import cm.cetic.mezesse.dto.UtilisateurDto.Ligne;
import cm.cetic.mezesse.dto.UtilisateurDto.Profil;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.UtilisateurService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

  private final UtilisateurService service;
  private final AnneeCouranteService annee;

  @GetMapping
  @Direction
  public List<Ligne> liste(@RequestParam(required = false) Role role, @RequestParam(required = false) String anneeId) {
    return service.liste(role, annee.anneeDe(anneeId));
  }

  @PostMapping
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public Profil creer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Enseignant d) {
    return service.creer(moi, d);
  }

  @PutMapping("/{id}")
  @SurveillantGeneral
  public Profil modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Enseignant d) {
    return service.modifier(moi, id, d);
  }

  @PostMapping("/{id}/reinitialiser")
  @SurveillantGeneral
  public Message reinitialiser(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    return new Message(service.reinitialiser(moi, id));
  }
}
