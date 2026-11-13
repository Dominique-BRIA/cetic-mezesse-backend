package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.AppelDto.Absences;
import cm.cetic.mezesse.dto.AppelDto.ChoixSequence;
import cm.cetic.mezesse.dto.AppelDto.Enregistrer;
import cm.cetic.mezesse.dto.AppelDto.Historique;
import cm.cetic.mezesse.dto.AppelDto.SeanceAjout;
import cm.cetic.mezesse.dto.AppelDto.SeanceModif;
import cm.cetic.mezesse.dto.AppelDto.Vue;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AppelService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Feuille d'appel du jour : le SG la tient, le Proviseur la consulte. */
@RestController
@RequestMapping("/api/appel")
@RequiredArgsConstructor
public class AppelController {

  private final AppelService service;
  private final cm.cetic.mezesse.service.AnneeCouranteService annee;

  @GetMapping
  @Direction
  public Vue vue(@RequestParam String classeId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.vue(classeId, date);
  }

  /** État de l'appel du jour pour toutes les classes de l'année. */
  @GetMapping("/etat")
  @Direction
  public List<cm.cetic.mezesse.dto.AppelDto.EtatClasse> etat(
      @RequestParam(required = false) String anneeId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.etatDuJour(annee.anneeDe(anneeId), date);
  }

  @GetMapping("/{id}/historique")
  @Direction
  public List<Historique> historique(@PathVariable String id) {
    return service.historique(id);
  }

  /** Premier enregistrement de l'appel du jour. */
  @PostMapping
  @SurveillantGeneral
  public Vue enregistrer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Enregistrer d) {
    return service.enregistrerAppel(moi, d);
  }

  /** Correction des absences d'une feuille enregistrée. */
  @PutMapping("/{id}/absences")
  @SurveillantGeneral
  public Vue corriger(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Absences d) {
    return service.corriger(moi, id, d);
  }

  @PutMapping("/{id}/sequence")
  @SurveillantGeneral
  public Vue sequence(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody ChoixSequence d) {
    return service.changerSequence(moi, id, d.sequenceId());
  }

  @PostMapping("/{id}/seances")
  @SurveillantGeneral
  public Vue ajouterSeance(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody SeanceAjout d) {
    return service.ajouterSeance(moi, id, d);
  }

  @PutMapping("/seances/{id}")
  @SurveillantGeneral
  public Vue modifierSeance(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody SeanceModif d) {
    return service.modifierSeance(moi, id, d);
  }

  @DeleteMapping("/seances/{id}")
  @SurveillantGeneral
  public Vue retirerSeance(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    return service.retirerSeance(moi, id);
  }
}
