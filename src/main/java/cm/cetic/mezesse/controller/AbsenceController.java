package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.AbsencesDto.Detail;
import cm.cetic.mezesse.dto.AbsencesDto.JustificationVue;
import cm.cetic.mezesse.dto.AbsencesDto.Justifier;
import cm.cetic.mezesse.dto.AbsencesDto.ModifierJustification;
import cm.cetic.mezesse.dto.AbsencesDto.Motif;
import cm.cetic.mezesse.dto.AbsencesDto.MotifRequete;
import cm.cetic.mezesse.dto.AbsencesDto.Page;
import cm.cetic.mezesse.dto.AbsencesDto.Resultat;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.security.Direction;
import cm.cetic.mezesse.security.SurveillantGeneral;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AbsenceService;
import cm.cetic.mezesse.service.AbsenceService.Contenu;
import cm.cetic.mezesse.service.AbsenceService.Filtres;
import cm.cetic.mezesse.service.AnneeCouranteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Consultation des absences et justifications : le SG justifie, le Proviseur consulte. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AbsenceController {

  private final AbsenceService service;
  private final cm.cetic.mezesse.service.EtablissementService etablissement;
  private final cm.cetic.mezesse.service.BilanAbsences bilan;
  private final AnneeCouranteService annee;
  private final ObjectMapper json;
  private final Validator validateur;

  @GetMapping("/absences")
  @Direction
  public Page liste(
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String classeId,
      @RequestParam(required = false) String eleveId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
      @RequestParam(required = false) String matiereId,
      @RequestParam(required = false) String matiere,
      @RequestParam(required = false) String etat,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String tri,
      @RequestParam(required = false) String sens,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int taille) {
    return service.liste(
        annee.anneeDe(anneeId), new Filtres(vide(classeId), vide(eleveId), du, au, vide(matiereId), vide(matiere), vide(etat), q, vide(tri), vide(sens)), page, taille);
  }

  /** Export des absences filtrées : « format » = xlsx (classeur) ou pdf (à imprimer). */
  @GetMapping("/absences/export")
  @Direction
  public ResponseEntity<byte[]> exporter(
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) String classeId,
      @RequestParam(required = false) String eleveId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
      @RequestParam(required = false) String matiere,
      @RequestParam(required = false) String etat,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String tri,
      @RequestParam(required = false) String sens,
      @RequestParam(defaultValue = "xlsx") String format) {
    String a = annee.anneeDe(anneeId);
    Filtres f = new Filtres(vide(classeId), vide(eleveId), du, au, null, vide(matiere), vide(etat), q, vide(tri), vide(sens));
    var lignes = service.toutes(a, f);
    var totaux = service.liste(a, f, 0, 1).totaux();
    String titre = "Absences" + (du != null || au != null ? " du " + (du == null ? "début" : du) + " au " + (au == null ? "ce jour" : au) : "");
    String nom = "absences-" + java.time.LocalDate.now();
    if ("pdf".equals(format)) {
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + ".pdf\"")
          .body(cm.cetic.mezesse.document.AbsencesExport.pdf(etablissement.requis(), titre, lignes, totaux));
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + ".xlsx\"")
        .body(cm.cetic.mezesse.document.AbsencesExport.excel(titre, lignes, totaux));
  }

  /** Heures de chaque élève d'une classe pour une période (S1 à S6, T1 à T3, A), et sanction retenue. */
  @GetMapping("/absences/bilan")
  @Direction
  public cm.cetic.mezesse.dto.AbsencesDto.Bilan bilan(@RequestParam String classeId, @RequestParam(defaultValue = "A") String periode) {
    return bilan.bilan(classeId, cm.cetic.mezesse.service.calcul.Periode.lire(periode));
  }

  /** Séances non assurées par les enseignants (absents), totaux par enseignant : direction. */
  @GetMapping("/absences/enseignants")
  @Direction
  public cm.cetic.mezesse.dto.AbsencesDto.EnseignantsAbsents enseignants(
      @RequestParam(required = false) String anneeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
      @RequestParam(required = false) String enseignantId) {
    return service.enseignantsAbsents(annee.anneeDe(anneeId), du, au, vide(enseignantId));
  }

  @GetMapping("/absences/{id}")
  @Direction
  public Detail detail(@RequestParam(required = false) String anneeId, @PathVariable String id) {
    return service.detail(annee.anneeDe(anneeId), id);
  }

  /** Multipart : champ « donnees » (JSON) et pièces « piece_0 », « piece_1 »… (PDF, JPEG, PNG). */
  @PostMapping(path = "/absences/justifier", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SurveillantGeneral
  public Resultat justifier(@AuthenticationPrincipal UtilisateurConnecte moi, MultipartHttpServletRequest req) {
    return service.justifier(moi, lire(req, Justifier.class), pieces(req));
  }

  /** Correction d'une justification : mêmes champs ; « piecesRetirees » dans les données. */
  @PostMapping(path = "/justifications/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SurveillantGeneral
  public JustificationVue modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id, MultipartHttpServletRequest req) {
    return service.modifier(moi, id, lire(req, ModifierJustification.class), pieces(req));
  }

  @DeleteMapping("/justifications/{id}")
  @SurveillantGeneral
  public Resultat retirer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String id) {
    return service.retirer(moi, id);
  }

  @GetMapping("/justifications/pieces/{id}")
  @Direction
  public ResponseEntity<byte[]> piece(@PathVariable String id) {
    Contenu c = service.piece(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(c.type()))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(c.nom(), StandardCharsets.UTF_8).build().toString())
        .body(c.octets());
  }

  @GetMapping("/motifs-justification")
  @Direction
  public List<Motif> motifs() {
    return service.motifs();
  }

  @PostMapping("/motifs-justification")
  @SurveillantGeneral
  public List<Motif> creerMotif(@Valid @RequestBody MotifRequete d) {
    return service.creerMotif(d);
  }

  @PutMapping("/motifs-justification/{id}")
  @SurveillantGeneral
  public List<Motif> modifierMotif(@PathVariable String id, @Valid @RequestBody MotifRequete d) {
    return service.modifierMotif(id, d);
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private <T> T lire(MultipartHttpServletRequest req, Class<T> type) {
    String donnees = req.getParameter("donnees");
    if (donnees == null) throw ApiException.invalide("Données manquantes.");
    T d;
    try {
      d = json.readValue(donnees, type);
    } catch (Exception e) {
      throw ApiException.invalide("Données illisibles.");
    }
    Set<ConstraintViolation<T>> erreurs = validateur.validate(d);
    if (!erreurs.isEmpty()) {
      ConstraintViolation<T> e = erreurs.iterator().next();
      throw ApiException.invalide(e.getPropertyPath() + " : " + e.getMessage());
    }
    return d;
  }

  private static List<MultipartFile> pieces(MultipartHttpServletRequest req) {
    return req.getMultiFileMap().entrySet().stream()
        .filter(e -> e.getKey().startsWith("piece"))
        .sorted(Map.Entry.comparingByKey())
        .flatMap(e -> e.getValue().stream())
        .toList();
  }
}
