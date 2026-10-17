package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.MatiereDto.GroupeRequete;
import cm.cetic.mezesse.dto.MatiereDto.GroupeVue;
import cm.cetic.mezesse.dto.MatiereDto.Requete;
import cm.cetic.mezesse.dto.MatiereDto.Vue;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.GroupeService;
import cm.cetic.mezesse.service.MatiereService;
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
public class MatiereController {

  private final GroupeService groupes;
  private final MatiereService matieres;
  private final AnneeCouranteService annee;

  @GetMapping("/groupes")
  @Direction
  public List<GroupeVue> groupes(@RequestParam(required = false) String anneeId) {
    return groupes.liste(annee.anneeDe(anneeId));
  }

  @PostMapping("/groupes")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public GroupeVue creerGroupe(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId, @Valid @RequestBody GroupeRequete d) {
    return groupes.creer(moi, annee.anneeDe(anneeId), d);
  }

  @PutMapping("/groupes/{id}")
  @SurveillantGeneral
  public GroupeVue modifierGroupe(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody GroupeRequete d) {
    return groupes.modifier(moi, id, d);
  }

  @DeleteMapping("/groupes/{id}")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void supprimerGroupe(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    groupes.supprimer(moi, id);
  }

  @GetMapping("/matieres")
  public List<Vue> matieres(
      @AuthenticationPrincipal UtilisateurConnecte moi,
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String classeId) {
    return matieres.liste(moi, annee.anneeDe(anneeId), classeId == null || classeId.isBlank() ? null : classeId);
  }

  @PostMapping("/matieres")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public Vue creerMatiere(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId, @Valid @RequestBody Requete d) {
    return matieres.creer(moi, annee.anneeDe(anneeId), d);
  }

  @PutMapping("/matieres/{id}")
  @SurveillantGeneral
  public Vue modifierMatiere(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Requete d) {
    return matieres.modifier(moi, id, d);
  }

  /** ?confirmer=oui pour supprimer une matière qui a déjà des notes (elles partent avec elle). */
  @DeleteMapping("/matieres/{id}")
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void supprimerMatiere(
      @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @RequestParam(required = false) String confirmer) {
    matieres.supprimer(moi, id, "oui".equals(confirmer));
  }
}
