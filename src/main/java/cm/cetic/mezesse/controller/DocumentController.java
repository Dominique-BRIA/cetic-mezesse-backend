package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.DocumentDto.Fichier;
import cm.cetic.mezesse.dto.DocumentDto.Selection;
import cm.cetic.mezesse.dto.DocumentDto.Verification;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.service.DocumentService;
import cm.cetic.mezesse.service.calcul.Periode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Documents imprimables, réservés à la direction. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Direction
public class DocumentController {

  private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final DocumentService service;

  /** PDF ouvert dans le navigateur. */
  private static ResponseEntity<byte[]> pdf(Fichier f) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.nom() + ".pdf\"")
        .body(f.contenu());
  }

  /** Classeur Excel téléchargé. */
  private static ResponseEntity<byte[]> excel(Fichier f) {
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.nom() + ".xlsx\"")
        .body(f.contenu());
  }

  /** Bulletins : { classeIds, inscriptionIds, periode }. */
  @PostMapping("/bulletins")
  public ResponseEntity<byte[]> bulletins(@RequestBody Selection s) {
    return pdf(service.bulletins(s));
  }

  @PostMapping("/cartes/verification")
  public Verification verification(@RequestBody Selection s) {
    return service.verification(s);
  }

  /** Cartes scolaires ; l'en-tête X-Cartes-Ecartees donne le nombre d'élèves sans photo écartés. */
  @PostMapping("/cartes")
  public ResponseEntity<byte[]> cartes(@RequestBody Selection s) {
    Fichier f = service.cartes(s);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.nom() + ".pdf\"")
        .header("X-Cartes-Ecartees", String.valueOf(f.ecartes()))
        .body(f.contenu());
  }

  /** Liste des élèves d'une classe : ?format=pdf (par défaut) ou xlsx. */
  @GetMapping("/liste")
  public ResponseEntity<byte[]> liste(@RequestParam String classeId, @RequestParam(defaultValue = "pdf") String format) {
    if (!format.equals("pdf") && !format.equals("xlsx")) throw ApiException.invalide("format : pdf ou xlsx");
    Fichier f = service.liste(classeId, format.equals("xlsx"));
    return format.equals("xlsx") ? excel(f) : pdf(f);
  }

  @GetMapping("/releve")
  public ResponseEntity<byte[]> releve(@RequestParam String classeId, @RequestParam(required = false) String periode) {
    return excel(service.releve(classeId, Periode.lire(periode)));
  }
}
