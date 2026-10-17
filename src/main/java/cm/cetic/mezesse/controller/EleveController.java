package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.EleveDto.Creation;
import cm.cetic.mezesse.dto.EleveDto.Fiche;
import cm.cetic.mezesse.dto.EleveDto.Ligne;
import cm.cetic.mezesse.dto.EleveDto.Modification;
import cm.cetic.mezesse.dto.EleveDto.Nombre;
import cm.cetic.mezesse.dto.EleveDto.PhotoCle;
import cm.cetic.mezesse.dto.EleveDto.Reinscription;
import cm.cetic.mezesse.dto.EleveDto.Reintegrer;
import cm.cetic.mezesse.dto.EleveDto.Renvoyer;
import cm.cetic.mezesse.dto.EleveDto.ResultatLot;
import cm.cetic.mezesse.dto.EleveDto.Vue;
import cm.cetic.mezesse.dto.Message;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.Proviseur;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AnneeCouranteService;
import cm.cetic.mezesse.service.EleveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/eleves")
@RequiredArgsConstructor
public class EleveController {

  private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final EleveService service;
  private final AnneeCouranteService annee;
  private final ObjectMapper json;

  /** Élèves d'une année : ?statut=ACTIF (par défaut), RENVOYE ou TOUS ; ?classeId= ; ?q= (nom, matricule). */
  @GetMapping
  @Direction
  public List<Ligne> liste(
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String statut,
      @RequestParam(required = false) String classeId,
      @RequestParam(required = false) String q) {
    return service.liste(annee.anneeDe(anneeId), statut, vide(classeId), vide(q));
  }

  @GetMapping("/modele-import")
  @SurveillantGeneral
  public ResponseEntity<byte[]> modeleImport() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(XLSX))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-import-eleves.xlsx\"")
        .body(service.modeleImport());
  }

  @GetMapping("/{id}")
  @Direction
  public Fiche fiche(@PathVariable String id) {
    return service.fiche(id);
  }

  @PostMapping
  @Direction
  @ResponseStatus(HttpStatus.CREATED)
  public Vue creer(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId, @Valid @RequestBody Creation d) {
    return service.creer(moi, annee.anneeDe(anneeId), d);
  }

  /** Saisie groupée en JSON : { classeId, eleves: [...] }. */
  @PostMapping(path = "/lot", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Direction
  @ResponseStatus(HttpStatus.CREATED)
  public ResultatLot lot(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId, @RequestBody JsonNode corps) {
    return service.ajouterLot(moi, anneeId, corps, null);
  }

  /** Saisie groupée avec photos : champ « donnees » (le JSON) et fichiers « photo_&lt;n° de ligne - 1&gt; ». */
  @PostMapping(path = "/lot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Direction
  @ResponseStatus(HttpStatus.CREATED)
  public ResultatLot lotAvecPhotos(
      @AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String anneeId, MultipartHttpServletRequest req) {
    String donnees = req.getParameter("donnees");
    JsonNode corps;
    try {
      corps = donnees == null ? null : json.readTree(donnees);
    } catch (Exception e) {
      throw ApiException.invalide("Données invalides.");
    }
    return service.ajouterLot(moi, anneeId, corps, req.getFileMap());
  }

  /** Photos de plusieurs élèves : fichiers « photo_&lt;id de l'élève&gt; ». */
  @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Direction
  public Nombre photos(@AuthenticationPrincipal UtilisateurConnecte moi, MultipartHttpServletRequest req) {
    return service.photos(moi, req.getFileMap());
  }

  @PutMapping("/{id}")
  @SurveillantGeneral
  public Vue modifier(
      @AuthenticationPrincipal UtilisateurConnecte moi,
      @PathVariable String id,
      @RequestParam(required = false) String anneeId,
      @Valid @RequestBody Modification d) {
    return service.modifier(moi, id, annee.anneeDe(anneeId), d);
  }

  @PostMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Direction
  public PhotoCle changerPhoto(
      @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @RequestPart(name = "photo", required = false) MultipartFile photo) {
    return service.changerPhoto(moi, id, photo);
  }

  @GetMapping("/{id}/photo")
  @Direction
  public ResponseEntity<byte[]> photo(@PathVariable String id) {
    EleveService.Photo p = service.photo(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(p.type()))
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
        .body(p.contenu());
  }

  /** Import d'un fichier Excel ou CSV (modèle fourni) dans une classe : tout ou rien. */
  @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SurveillantGeneral
  @ResponseStatus(HttpStatus.CREATED)
  public Nombre importer(
      @AuthenticationPrincipal UtilisateurConnecte moi,
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String classeId,
      @RequestPart(name = "fichier", required = false) MultipartFile fichier) {
    return service.importer(moi, anneeId, classeId, fichier);
  }

  @PostMapping("/inscriptions/{id}/renvoyer")
  @Proviseur
  public Message renvoyer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Renvoyer d) {
    service.renvoyer(moi, id, d.motif(), d.date());
    return new Message("Élève renvoyé.");
  }

  @PostMapping("/inscriptions/{id}/reintegrer")
  @Proviseur
  public Message reintegrer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, @Valid @RequestBody Reintegrer d) {
    service.reintegrer(moi, id, d.motif());
    return new Message("Élève réintégré.");
  }

  @PostMapping("/reinscrire")
  @SurveillantGeneral
  public Message reinscrire(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody Reinscription d) {
    return new Message(service.reinscrire(moi, d));
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
