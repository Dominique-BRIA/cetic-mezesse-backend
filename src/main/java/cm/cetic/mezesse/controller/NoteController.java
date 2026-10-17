package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.Message;
import cm.cetic.mezesse.dto.NoteDto.Avancement;
import cm.cetic.mezesse.dto.NoteDto.Feuille;
import cm.cetic.mezesse.dto.NoteDto.Manquantes;
import cm.cetic.mezesse.dto.NoteDto.ReleveClasse;
import cm.cetic.mezesse.dto.NoteDto.SaisieFeuille;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.Professeur;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.NoteService;
import cm.cetic.mezesse.service.calcul.Periode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

  private final NoteService service;
  private final AnneeCouranteService annee;

  @GetMapping("/feuille")
  public Feuille feuille(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam String matiereId, @RequestParam String sequenceId) {
    return service.feuille(moi, matiereId, sequenceId);
  }

  /** Seul le professeur de la matière saisit ses notes. */
  @PutMapping("/feuille")
  @Professeur
  public Message enregistrer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody SaisieFeuille d) {
    return new Message(service.enregistrer(moi, d));
  }

  @GetMapping("/avancement")
  public Avancement avancement(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId) {
    return service.avancement(moi, annee.anneeDe(anneeId));
  }

  @GetMapping("/manquantes")
  public Manquantes manquantes(
      @AuthenticationPrincipal UtilisateurConnecte moi,
      @RequestParam String sequenceId,
      @RequestParam(required = false) String matiereId,
      @RequestParam(required = false) String classeId) {
    return service.manquantes(moi, sequenceId, vide(matiereId), vide(classeId));
  }

  @GetMapping("/classe")
  @Direction
  public ReleveClasse releve(@RequestParam String classeId, @RequestParam(required = false) String periode) {
    return service.releve(classeId, Periode.lire(periode));
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
