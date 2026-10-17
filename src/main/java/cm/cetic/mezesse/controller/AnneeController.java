package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.AnneeDto.Creation;
import cm.cetic.mezesse.dto.AnneeDto.Ligne;
import cm.cetic.mezesse.dto.AnneeDto.Saisie;
import cm.cetic.mezesse.dto.AnneeDto.SequenceVue;
import cm.cetic.mezesse.dto.AnneeDto.Vue;
import cm.cetic.mezesse.dto.Message;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/annees")
@RequiredArgsConstructor
public class AnneeController {

  private final AnneeService service;

  @GetMapping
  public List<Ligne> liste() {
    return service.liste();
  }

  @PostMapping
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public Vue creer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Creation d) {
    return service.creer(moi, d);
  }

  @PostMapping("/{id}/activer")
  @SurveillantGeneral
  public Message activer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    service.activer(moi, id);
    return new Message("Année activée.");
  }

  @PatchMapping("/sequences/{id}")
  @SurveillantGeneral
  public SequenceVue saisie(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Saisie d) {
    return service.saisie(moi, id, d.saisieOuverte());
  }
}
