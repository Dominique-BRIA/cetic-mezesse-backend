package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.StatistiqueDto;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.StatistiqueService;
import cm.cetic.mezesse.service.calcul.Periode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistiques")
@RequiredArgsConstructor
public class StatistiqueController {

  private final StatistiqueService service;
  private final AnneeCouranteService annee;

  /** Professeur de la matière ou direction. */
  @GetMapping("/matiere")
  public StatistiqueDto.Matiere matiere(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam String matiereId, @RequestParam(required = false) String periode) {
    return service.matiere(moi, matiereId, Periode.lire(periode));
  }

  @GetMapping("/classe")
  @Direction
  public StatistiqueDto.Classe classe(@RequestParam String classeId, @RequestParam(required = false) String periode) {
    return service.classe(classeId, Periode.lire(periode));
  }

  @GetMapping("/etablissement")
  @Direction
  public StatistiqueDto.Etablissement etablissement(@RequestParam(required = false) String anneeId, @RequestParam(required = false) String periode) {
    String id = annee.anneeDe(anneeId);
    return service.etablissement(id, Periode.lire(periode));
  }
}
