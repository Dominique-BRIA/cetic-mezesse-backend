package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.DocumentDto.Fichier;
import cm.cetic.mezesse.dto.TableauHonneurDto.Tableau;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.TableauHonneurService;
import cm.cetic.mezesse.service.calcul.Periode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tableau d'honneur d'une période (Proviseur et SG) : consultation, liste PDF, certificats. */
@RestController
@RequestMapping("/api/tableau-honneur")
@RequiredArgsConstructor
@Direction
public class TableauHonneurController {

  private final TableauHonneurService service;
  private final AnneeCouranteService annee;

  @GetMapping
  public Tableau tableau(
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String periode,
      @RequestParam(required = false) String classeId) {
    return service.tableau(annee.anneeDe(anneeId), Periode.lire(periode), vide(classeId));
  }

  /** ?format=liste (par défaut) ou certificats. */
  @GetMapping("/pdf")
  public ResponseEntity<byte[]> pdf(
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String periode,
      @RequestParam(required = false) String classeId,
      @RequestParam(defaultValue = "liste") String format) {
    if (!format.equals("liste") && !format.equals("certificats")) throw ApiException.invalide("format : liste ou certificats");
    Fichier f = service.document(annee.anneeDe(anneeId), Periode.lire(periode), vide(classeId), format.equals("certificats"));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.nom() + ".pdf\"")
        .body(f.contenu());
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
