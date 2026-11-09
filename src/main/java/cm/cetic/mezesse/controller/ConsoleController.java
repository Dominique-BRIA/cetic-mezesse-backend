package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.ConsoleDto.Analytique;
import cm.cetic.mezesse.dto.ConsoleDto.Apercu;
import cm.cetic.mezesse.dto.ConsoleDto.Connexion;
import cm.cetic.mezesse.dto.ConsoleDto.DetailPlantage;
import cm.cetic.mezesse.dto.ConsoleDto.GroupePlantage;
import cm.cetic.mezesse.dto.ConsoleDto.LigneJournal;
import cm.cetic.mezesse.dto.ConsoleDto.Moi;
import cm.cetic.mezesse.dto.ConsoleDto.Parc;
import cm.cetic.mezesse.dto.ConsoleDto.ReglagePolitique;
import cm.cetic.mezesse.dto.ConsoleDto.ReponseConnexion;
import cm.cetic.mezesse.dto.ConsoleDto.Resolution;
import cm.cetic.mezesse.dto.ConsoleDto.VuePolitique;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.ConsoleAuthService;
import cm.cetic.mezesse.service.ConsoleService;
import cm.cetic.mezesse.service.PolitiqueMiseAJourService;
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

/** Console technique du développeur. Hors connexion, tout est réservé au rôle SUPER_ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class ConsoleController {

  private final ConsoleAuthService auth;
  private final ConsoleService service;
  private final PolitiqueMiseAJourService politique;

  @PostMapping("/auth/connexion")
  public ReponseConnexion connexion(@Valid @RequestBody Connexion c) {
    return auth.connexion(c);
  }

  @GetMapping("/auth/moi")
  public Moi moi(@AuthenticationPrincipal UtilisateurConnecte moi) {
    return auth.moi(moi);
  }

  @GetMapping("/apercu")
  public Apercu apercu() {
    return service.apercu();
  }

  @GetMapping("/analytique")
  public Analytique analytique(@RequestParam(defaultValue = "30") int jours) {
    return service.analytique(jours);
  }

  @GetMapping("/appareils")
  public Parc appareils() {
    return service.parc();
  }

  @GetMapping("/plantages")
  public List<GroupePlantage> plantages(@RequestParam(defaultValue = "ouverts") String etat) {
    return service.plantages(etat);
  }

  @GetMapping("/plantages/{empreinte}")
  public DetailPlantage plantage(@PathVariable String empreinte) {
    return service.plantage(empreinte);
  }

  @PostMapping("/plantages/{empreinte}/resolution")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resolution(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String empreinte, @Valid @RequestBody Resolution d) {
    service.marquer(moi, empreinte, d.resolu());
  }

  @GetMapping("/mises-a-jour/politique")
  public VuePolitique politique() {
    return politique.detail();
  }

  @PutMapping("/mises-a-jour/politique")
  public VuePolitique modifierPolitique(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody ReglagePolitique d) {
    return politique.modifier(moi, d);
  }

  @GetMapping("/journal")
  public List<LigneJournal> journal() {
    return service.journal();
  }
}
