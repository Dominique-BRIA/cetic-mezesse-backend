package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.DecisionDto.Desapprobation;
import cm.cetic.mezesse.dto.DecisionDto.Page;
import cm.cetic.mezesse.dto.DecisionDto.Vue;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.security.Proviseur;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.DecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Journal des décisions du SG : réservé au Proviseur. */
@RestController
@RequestMapping("/api/decisions")
@RequiredArgsConstructor
@Proviseur
public class DecisionController {

  private final DecisionService service;

  @GetMapping
  public Page liste(
      @RequestParam(required = false) DomaineDecision domaine,
      @RequestParam(required = false) String statut,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String apres) {
    String recherche = q == null || q.isBlank() ? null : q.strip();
    if (recherche != null && recherche.length() > 100) throw ApiException.invalide("q : 100 caractères au plus");
    return service.liste(domaine, vide(statut), recherche, vide(apres));
  }

  @PostMapping("/{id}/desapprouver")
  public Vue desapprouver(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Desapprobation d) {
    return service.desapprouver(moi, id, d.motif());
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
