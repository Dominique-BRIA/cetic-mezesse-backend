package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.AttestationDto.Demande;
import cm.cetic.mezesse.dto.AttestationDto.Vue;
import cm.cetic.mezesse.dto.DocumentDto.Fichier;
import cm.cetic.mezesse.security.Proviseur;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AttestationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Attestations de présence effective au poste : registre, délivrance et impression (Proviseur). */
@RestController
@RequestMapping("/api/attestations")
@RequiredArgsConstructor
@Proviseur
public class AttestationController {

  private final AttestationService service;

  @GetMapping
  public List<Vue> registre() {
    return service.registre();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Vue delivrer(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Demande d) {
    return service.delivrer(moi, d.utilisateurId());
  }

  @GetMapping("/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable String id) {
    Fichier f = service.pdf(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.nom() + ".pdf\"")
        .body(f.contenu());
  }
}
