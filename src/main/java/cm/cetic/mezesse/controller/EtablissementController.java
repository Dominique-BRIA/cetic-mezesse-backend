package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.EtablissementDto.BaremeVue;
import cm.cetic.mezesse.dto.EtablissementDto.Modification;
import cm.cetic.mezesse.dto.EtablissementDto.Seuil;
import cm.cetic.mezesse.dto.EtablissementDto.Vue;
import cm.cetic.mezesse.dto.Message;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.EtablissementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EtablissementController {

  private final EtablissementService service;

  @GetMapping("/etablissement")
  public Vue lire() {
    return service.lire();
  }

  @PutMapping("/etablissement")
  @SurveillantGeneral
  public Vue modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Modification d) {
    return service.modifier(moi, d);
  }

  @GetMapping("/baremes")
  public List<BaremeVue> baremes() {
    return service.baremes();
  }

  @PutMapping("/baremes/{type}")
  @SurveillantGeneral
  public Message remplacerBareme(
      @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable TypeBareme type, @RequestBody List<@Valid Seuil> seuils) {
    service.remplacerBareme(moi, type, seuils);
    return new Message("Barème enregistré.");
  }
}
