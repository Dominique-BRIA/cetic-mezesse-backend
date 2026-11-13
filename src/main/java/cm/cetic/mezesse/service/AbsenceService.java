package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.AbsencesDto.Detail;
import cm.cetic.mezesse.dto.AbsencesDto.JustificationVue;
import cm.cetic.mezesse.dto.AbsencesDto.Justifier;
import cm.cetic.mezesse.dto.AbsencesDto.Ligne;
import cm.cetic.mezesse.dto.AbsencesDto.ModifierJustification;
import cm.cetic.mezesse.dto.AbsencesDto.Motif;
import cm.cetic.mezesse.dto.AbsencesDto.MotifRequete;
import cm.cetic.mezesse.dto.AbsencesDto.Page;
import cm.cetic.mezesse.dto.AbsencesDto.Piece;
import cm.cetic.mezesse.dto.AbsencesDto.Resultat;
import cm.cetic.mezesse.dto.AbsencesDto.Totaux;
import cm.cetic.mezesse.dto.AppelDto.Historique;
import cm.cetic.mezesse.entity.Absence;
import cm.cetic.mezesse.entity.HistoriqueAbsence;
import cm.cetic.mezesse.entity.Justification;
import cm.cetic.mezesse.entity.MotifJustification;
import cm.cetic.mezesse.entity.PieceJustificative;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AbsenceRepository;
import cm.cetic.mezesse.repository.HistoriqueAbsenceRepository;
import cm.cetic.mezesse.repository.JustificationRepository;
import cm.cetic.mezesse.repository.MotifJustificationRepository;
import cm.cetic.mezesse.repository.PieceJustificativeRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import cm.cetic.mezesse.util.Textes;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Consultation des absences et justifications. Une justification est rattachée directement à une absence
 * (aucun dossier) ; plusieurs absences d'un même élève peuvent être justifiées ensemble, chacune reçoit la
 * sienne. Le refus d'un justificatif n'est pas enregistré : l'absence reste non justifiée.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbsenceService {

  static final Set<String> TYPES_PIECE = Set.of("image/jpeg", "image/png", "application/pdf");
  static final int PIECES_MAX = 5;
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final NamedParameterJdbcTemplate sql;
  private final AbsenceRepository absences;
  private final JustificationRepository justifications;
  private final PieceJustificativeRepository pieces;
  private final MotifJustificationRepository motifs;
  private final HistoriqueAbsenceRepository historique;
  private final UtilisateurRepository utilisateurs;
  private final StockageService stockage;
  private final PlatformTransactionManager transactions;
  private final BilanAbsences bilan;

  // ---------- Consultation ----------

  /** Filtres de la consultation ; tous facultatifs, combinables. */
  /**
   * Filtres de la consultation ; tous facultatifs, combinables. « matiere » : un nom de matière, toutes classes
   * confondues. « tri » : date, eleve, duree ou etat ; « sens » : asc ou desc.
   */
  public record Filtres(
      String classeId, String eleveId, LocalDate du, LocalDate au, String matiereId, String matiere, String etat, String recherche, String tri, String sens) {

    public Filtres(String classeId, String eleveId, LocalDate du, LocalDate au, String matiereId, String etat, String recherche) {
      this(classeId, eleveId, du, au, matiereId, null, etat, recherche, null, null);
    }
  }

  /** Ordre des lignes : colonne choisie, puis la date (plus récente d'abord) pour départager. */
  static String ordre(Filtres f) {
    String sens = "asc".equalsIgnoreCase(f.sens()) ? "ASC" : "DESC";
    String colonne =
        switch (f.tri() == null ? "date" : f.tri()) {
          case "eleve" -> "e.nom " + sens + ", e.prenoms " + sens;
          case "duree" -> DUREE + " " + sens;
          case "etat" -> "(j.id IS NOT NULL) " + sens;
          default -> "f.date " + sens + ", s.debut";
        };
    return " ORDER BY " + colonne + ", f.date DESC, s.debut, e.nom, e.prenoms";
  }

  private static final String DUREE = "(EXTRACT(EPOCH FROM (s.fin - s.debut)) / 60)::int";

  private static final String DEPUIS =
      """
      FROM "Absence" a
      JOIN "Seance" s ON s.id = a."seanceId"
      JOIN "FicheAppel" f ON f.id = s."ficheId"
      JOIN "Classe" c ON c.id = f."classeId"
      JOIN "Inscription" i ON i.id = a."inscriptionId"
      JOIN "Eleve" e ON e.id = i."eleveId"
      LEFT JOIN "Justification" j ON j."absenceId" = a.id
      """;

  private static String conditions(String anneeId, Filtres f, MapSqlParameterSource p) {
    StringBuilder w = new StringBuilder(" WHERE c.\"anneeId\" = :annee");
    p.addValue("annee", anneeId);
    if (f.classeId() != null) {
      w.append(" AND c.id = :classe");
      p.addValue("classe", f.classeId());
    }
    if (f.eleveId() != null) {
      w.append(" AND e.id = :eleve");
      p.addValue("eleve", f.eleveId());
    }
    if (f.du() != null) {
      w.append(" AND f.date >= :du");
      p.addValue("du", f.du());
    }
    if (f.au() != null) {
      w.append(" AND f.date <= :au");
      p.addValue("au", f.au());
    }
    if (f.matiere() != null) {
      w.append(" AND s.matiere = :matiereNom");
      p.addValue("matiereNom", f.matiere());
    }
    if (f.matiereId() != null) {
      w.append(" AND s.\"matiereId\" = :matiere");
      p.addValue("matiere", f.matiereId());
    }
    if ("JUSTIFIEE".equals(f.etat())) w.append(" AND j.id IS NOT NULL");
    if ("NON_JUSTIFIEE".equals(f.etat())) w.append(" AND j.id IS NULL");
    if (f.recherche() != null && !f.recherche().isBlank()) {
      w.append(" AND (translate(e.nom || ' ' || e.prenoms, " + ACCENTS + ") ILIKE :q OR e.matricule ILIKE :q)");
      p.addValue("q", "%" + Textes.sansAccents(f.recherche().trim()) + "%");
    }
    return w.toString();
  }

  public Page liste(String anneeId, Filtres f, int page, int taille) {
    int t = Math.max(1, Math.min(taille, 200));
    int n = Math.max(0, page);
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = conditions(anneeId, f, p);
    Map<String, Object> tot =
        sql.queryForMap(
            "SELECT COUNT(*) AS n, COALESCE(SUM(" + DUREE + "), 0) AS minutes, COALESCE(SUM(CASE WHEN j.id IS NOT NULL THEN " + DUREE + " END), 0) AS justifiees "
                + DEPUIS + where,
            p);
    long minutes = ((Number) tot.get("minutes")).longValue();
    long justifiees = ((Number) tot.get("justifiees")).longValue();
    Totaux totaux = new Totaux(((Number) tot.get("n")).longValue(), minutes, justifiees, minutes - justifiees);
    p.addValue("limite", t).addValue("decalage", (long) n * t);
    List<Map<String, Object>> rangs =
        sql.queryForList(
            """
            SELECT a.id, a."inscriptionId" AS inscription, e.id AS eleve, e.nom, e.prenoms, e.matricule, c.id AS classe_id, c.nom AS classe, f.date,
              s.id AS seance, s.matiere, to_char(s.debut, 'HH24:MI') AS debut, to_char(s.fin, 'HH24:MI') AS fin, """
                + DUREE + " AS duree, s.\"enseignantAbsent\" AS ens_absent "
                + DEPUIS + where + ordre(f) + " LIMIT :limite OFFSET :decalage",
            p);
    return new Page(lignes(rangs), n, t, totaux.absences(), totaux);
  }

  /** Toutes les lignes du filtre (5 000 au plus), dans l'ordre choisi : l'export. */
  public List<Ligne> toutes(String anneeId, Filtres f) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = conditions(anneeId, f, p);
    p.addValue("limite", 5000).addValue("decalage", 0L);
    return lignes(
        sql.queryForList(
            """
            SELECT a.id, a."inscriptionId" AS inscription, e.id AS eleve, e.nom, e.prenoms, e.matricule, c.id AS classe_id, c.nom AS classe, f.date,
              s.id AS seance, s.matiere, to_char(s.debut, 'HH24:MI') AS debut, to_char(s.fin, 'HH24:MI') AS fin, """
                + DUREE + " AS duree, s.\"enseignantAbsent\" AS ens_absent " + DEPUIS + where + ordre(f) + " LIMIT :limite OFFSET :decalage",
            p));
  }

  /** Accents retirés côté base, comme Textes.sansAccents côté Java. */
  private static final String ACCENTS = "'ÀÂÄÉÈÊËÎÏÔÖÙÛÜÇàâäéèêëîïôöùûüç', 'AAAEEEEIIOOUUUCaaaeeeeiioouuuc'";

  private List<Ligne> lignes(List<Map<String, Object>> rangs) {
    if (rangs.isEmpty()) return List.of();
    Map<String, JustificationVue> parAbsence = new LinkedHashMap<>();
    for (Justification j : justifications.desAbsences(rangs.stream().map(r -> (String) r.get("id")).toList())) {
      parAbsence.put(j.getAbsence().getId(), vue(j));
    }
    List<Ligne> l = new ArrayList<>();
    for (Map<String, Object> r : rangs) {
      Object d = r.get("date");
      LocalDate date = d instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) d;
      l.add(new Ligne(
          (String) r.get("id"), (String) r.get("inscription"), (String) r.get("eleve"), (String) r.get("nom"), (String) r.get("prenoms"),
          (String) r.get("matricule"), (String) r.get("classe_id"), (String) r.get("classe"), date, (String) r.get("seance"), (String) r.get("matiere"),
          (String) r.get("debut"), (String) r.get("fin"), ((Number) r.get("duree")).intValue(), Boolean.TRUE.equals(r.get("ens_absent")),
          parAbsence.get((String) r.get("id"))));
    }
    return l;
  }

  private static JustificationVue vue(Justification j) {
    return new JustificationVue(
        j.getId(), j.getMotif().getId(), j.getMotif().getLibelle(), j.getDescription(), j.getDateReception(), j.getPar() == null ? null : j.getPar().nomComplet(),
        j.getCreeLe(), j.getModifieLe(), j.getPieces().stream().map(p -> new Piece(p.getId(), p.getNomFichier(), p.getTypeMime(), p.getTaille())).toList());
  }

  public Detail detail(String anneeId, String absenceId) {
    Absence a = absences.findById(absenceId).orElseThrow(() -> ApiException.introuvable("Absence"));
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", absenceId);
    List<Map<String, Object>> rangs =
        sql.queryForList(
            """
            SELECT a.id, a."inscriptionId" AS inscription, e.id AS eleve, e.nom, e.prenoms, e.matricule, c.id AS classe_id, c.nom AS classe, f.date,
              s.id AS seance, s.matiere, to_char(s.debut, 'HH24:MI') AS debut, to_char(s.fin, 'HH24:MI') AS fin, """
                + DUREE + " AS duree, s.\"enseignantAbsent\" AS ens_absent " + DEPUIS + " WHERE a.id = :id",
            p);
    List<Historique> h =
        historique.deLAbsence(a.getId()).stream()
            .map(x -> new Historique(x.getId(), x.getAction(), x.getLibelle(), x.getAvant(), x.getApres(), x.getPar() == null ? null : x.getPar().nomComplet(), x.getCreeLe()))
            .toList();
    return new Detail(lignes(rangs).get(0), h);
  }

  // ---------- Enseignants ----------

  /** Séances non assurées de l'année (enseignant absent), par enseignant, sur une période facultative. */
  public cm.cetic.mezesse.dto.AbsencesDto.EnseignantsAbsents enseignantsAbsents(String anneeId, LocalDate du, LocalDate au, String enseignantId) {
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("annee", anneeId);
    StringBuilder w = new StringBuilder(" WHERE c.\"anneeId\" = :annee AND s.\"enseignantAbsent\" AND s.statut = 'PREVUE'");
    if (du != null) {
      w.append(" AND f.date >= :du");
      p.addValue("du", du);
    }
    if (au != null) {
      w.append(" AND f.date <= :au");
      p.addValue("au", au);
    }
    List<Map<String, Object>> rangs =
        sql.queryForList(
            """
            SELECT s.id, f.date, c.id AS classe_id, c.nom AS classe, s.matiere, to_char(s.debut, 'HH24:MI') AS debut, to_char(s.fin, 'HH24:MI') AS fin, """
                + DUREE
                + """
                 AS duree, s.motif, u.id AS ens_id, u.nom AS ens_nom, u.prenom AS ens_prenom
                FROM "Seance" s
                JOIN "FicheAppel" f ON f.id = s."ficheId"
                JOIN "Classe" c ON c.id = f."classeId"
                LEFT JOIN "Utilisateur" u ON u.id = s."enseignantId"
                """
                + w
                + " ORDER BY f.date DESC, s.debut",
            p);
    Map<String, cm.cetic.mezesse.dto.AbsencesDto.TotalEnseignant> totaux = new LinkedHashMap<>();
    List<cm.cetic.mezesse.dto.AbsencesDto.SeanceNonAssuree> seances = new ArrayList<>();
    for (Map<String, Object> r : rangs) {
      String id = (String) r.get("ens_id");
      String cle = id == null ? "" : id;
      int duree = ((Number) r.get("duree")).intValue();
      cm.cetic.mezesse.dto.AbsencesDto.TotalEnseignant t = totaux.get(cle);
      totaux.put(
          cle,
          new cm.cetic.mezesse.dto.AbsencesDto.TotalEnseignant(
              id, id == null ? "Sans enseignant" : (String) r.get("ens_nom"), id == null ? "" : (String) r.get("ens_prenom"), (t == null ? 0 : t.seances()) + 1,
              (t == null ? 0 : t.minutes()) + duree));
      if (enseignantId != null && !enseignantId.equals(id)) continue;
      Object d = r.get("date");
      seances.add(new cm.cetic.mezesse.dto.AbsencesDto.SeanceNonAssuree(
          (String) r.get("id"), d instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) d, (String) r.get("classe_id"), (String) r.get("classe"),
          (String) r.get("matiere"), (String) r.get("debut"), (String) r.get("fin"), duree, (String) r.get("motif"), id,
          id == null ? null : r.get("ens_prenom") + " " + r.get("ens_nom")));
    }
    List<cm.cetic.mezesse.dto.AbsencesDto.TotalEnseignant> liste = new ArrayList<>(totaux.values());
    liste.sort((x, y) -> y.minutes() != x.minutes() ? Integer.compare(y.minutes(), x.minutes()) : x.nom().compareTo(y.nom()));
    return new cm.cetic.mezesse.dto.AbsencesDto.EnseignantsAbsents(liste, seances);
  }

  // ---------- Justifications ----------

  /** Fichier reçu et vérifié, prêt à être déposé. */
  private record Fichier(String nom, String type, byte[] contenu) {}

  private static List<Fichier> verifier(List<MultipartFile> recus, int dejaPresentes) {
    List<MultipartFile> l = recus == null ? List.of() : recus.stream().filter(f -> f != null && !f.isEmpty()).toList();
    if (l.size() + dejaPresentes > PIECES_MAX) throw ApiException.invalide("Au plus " + PIECES_MAX + " pièces justificatives par absence.");
    List<Fichier> fichiers = new ArrayList<>();
    for (MultipartFile f : l) {
      if (!TYPES_PIECE.contains(f.getContentType())) throw ApiException.invalide("Pièces en PDF, JPEG ou PNG uniquement (" + f.getOriginalFilename() + ").");
      try {
        fichiers.add(new Fichier(Textes.couper(f.getOriginalFilename() == null ? "piece" : f.getOriginalFilename(), 200), f.getContentType(), f.getBytes()));
      } catch (IOException e) {
        throw new IllegalStateException("Lecture de la pièce impossible", e);
      }
    }
    return fichiers;
  }

  /** Dépose les fichiers ; renvoie leurs clés, dans l'ordre. */
  private List<String> deposer(List<Fichier> fichiers) {
    List<String> cles = new ArrayList<>();
    try {
      for (Fichier f : fichiers) {
        String ext = "application/pdf".equals(f.type()) ? ".pdf" : "image/png".equals(f.type()) ? ".png" : ".jpg";
        String cle = "justificatifs/" + UUID.randomUUID() + ext;
        stockage.deposer(cle, f.contenu(), f.type());
        cles.add(cle);
      }
    } catch (RuntimeException e) {
      cles.forEach(stockage::supprimer);
      throw e;
    }
    return cles;
  }

  private static void ajouterPieces(Justification j, List<Fichier> fichiers, List<String> cles) {
    for (int k = 0; k < fichiers.size(); k++) {
      PieceJustificative p = new PieceJustificative();
      p.setJustification(j);
      p.setNomFichier(fichiers.get(k).nom());
      p.setCle(cles.get(k));
      p.setTypeMime(fichiers.get(k).type());
      p.setTaille(fichiers.get(k).contenu().length);
      j.getPieces().add(p);
    }
  }

  /** Retire du stockage les fichiers qui ne servent plus à aucune justification (après validation de la transaction). */
  private void nettoyer(Set<String> cles) {
    for (String cle : cles) {
      if (pieces.countByCle(cle) == 0) stockage.supprimer(cle);
    }
  }

  /** Justifie une ou plusieurs absences d'un même élève : tout ou rien. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Resultat justifier(UtilisateurConnecte moi, Justifier d, List<MultipartFile> recues) {
    List<Fichier> fichiers = verifier(recues, 0);
    List<String> ids = d.absenceIds().stream().distinct().toList();
    // Vérifications avant tout dépôt de fichier.
    new TransactionTemplate(transactions).executeWithoutResult(t -> controler(ids, d.motifId()));
    List<String> cles = deposer(fichiers);
    try {
      return new TransactionTemplate(transactions)
          .execute(
              t -> {
                List<Absence> liste = controler(ids, d.motifId());
                MotifJustification motif = motifs.getReferenceById(d.motifId());
                for (Absence a : liste) {
                  Justification j = new Justification();
                  j.setAbsence(a);
                  j.setMotif(motif);
                  j.setDescription(Textes.optionnel(d.description()));
                  j.setDateReception(d.dateReception());
                  j.setPar(utilisateurs.getReferenceById(moi.id()));
                  ajouterPieces(j, fichiers, cles);
                  justifications.save(j);
                  tracer(moi, a, "justification.creer", libelle(a) + " justifiée : " + motifs.findById(d.motifId()).map(MotifJustification::getLibelle).orElse("")
                      + (cles.isEmpty() ? "" : " (" + cles.size() + " pièce(s))"), null, champs(j));
                }
                justifications.flush();
                String eleve = liste.get(0).getInscription().getEleve().nomComplet();
                return new Resultat(liste.size() + " absence(s) de " + eleve + " justifiée(s).", liste.size());
              });
    } catch (RuntimeException e) {
      cles.forEach(stockage::supprimer);
      throw e;
    }
  }

  /** Absences à justifier : existantes, d'un même élève, pas encore justifiées ; motif actif. */
  private List<Absence> controler(List<String> ids, String motifId) {
    MotifJustification motif = motifs.findById(motifId).orElseThrow(() -> ApiException.invalide("Motif inconnu."));
    if (!motif.isActif()) throw ApiException.invalide("Ce motif n'est plus proposé.");
    List<Absence> liste = absences.avecEleve(ids);
    if (liste.size() != ids.size()) throw ApiException.introuvable("Absence");
    Set<String> eleves = liste.stream().map(a -> a.getInscription().getId()).collect(Collectors.toSet());
    if (eleves.size() > 1) throw ApiException.invalide("Une justification concerne les absences d'un seul élève.");
    List<Justification> deja = justifications.desAbsences(ids);
    if (!deja.isEmpty()) {
      Map<String, Absence> parId = liste.stream().collect(Collectors.toMap(Absence::getId, Function.identity()));
      String quelles = String.join(", ", deja.stream().map(j -> libelleCourt(parId.get(j.getAbsence().getId()))).toList());
      throw ApiException.conflit("Déjà justifiée(s) : " + quelles + ". Modifiez la justification existante plutôt que d'en ajouter une seconde.");
    }
    return liste;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public JustificationVue modifier(UtilisateurConnecte moi, String id, ModifierJustification d, List<MultipartFile> recues) {
    Set<String> retirees = new HashSet<>(d.piecesRetirees() == null ? List.of() : d.piecesRetirees());
    int restantes =
        new TransactionTemplate(transactions)
            .execute(t -> {
              Justification j = justifications.findById(id).orElseThrow(() -> ApiException.introuvable("Justification"));
              return (int) j.getPieces().stream().filter(p -> !retirees.contains(p.getId())).count();
            });
    List<Fichier> fichiers = verifier(recues, restantes);
    List<String> cles = deposer(fichiers);
    Set<String> aNettoyer = new HashSet<>();
    try {
      JustificationVue v =
          new TransactionTemplate(transactions)
              .execute(
                  t -> {
                    Justification j = justifications.findById(id).orElseThrow(() -> ApiException.introuvable("Justification"));
                    MotifJustification motif = motifs.findById(d.motifId()).orElseThrow(() -> ApiException.invalide("Motif inconnu."));
                    if (!motif.isActif() && !motif.getId().equals(j.getMotif().getId())) throw ApiException.invalide("Ce motif n'est plus proposé.");
                    Map<String, Object> avant = champs(j);
                    j.setMotif(motif);
                    j.setDescription(Textes.optionnel(d.description()));
                    j.setDateReception(d.dateReception());
                    for (PieceJustificative p : List.copyOf(j.getPieces())) {
                      if (retirees.contains(p.getId())) {
                        aNettoyer.add(p.getCle());
                        j.getPieces().remove(p);
                      }
                    }
                    ajouterPieces(j, fichiers, cles);
                    Map<String, Object> apres = champs(j);
                    if (!avant.equals(apres)) {
                      j.setModifieLe(Horloge.maintenant());
                      justifications.saveAndFlush(j);
                      tracer(moi, j.getAbsence(), "justification.modifier", "Justification modifiée : " + libelle(j.getAbsence()) + ecart(avant, apres), avant, apres);
                    }
                    return vue(justifications.desAbsences(List.of(j.getAbsence().getId())).get(0));
                  });
      nettoyer(aNettoyer);
      return v;
    } catch (RuntimeException e) {
      cles.forEach(stockage::supprimer);
      throw e;
    }
  }

  /** Retire une justification enregistrée par erreur : l'absence redevient non justifiée. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Resultat retirer(UtilisateurConnecte moi, String id) {
    Set<String> aNettoyer = new HashSet<>();
    Resultat r =
        new TransactionTemplate(transactions)
            .execute(
                t -> {
                  Justification j = justifications.findById(id).orElseThrow(() -> ApiException.introuvable("Justification"));
                  Absence a = j.getAbsence();
                  Map<String, Object> avant = champs(j);
                  j.getPieces().forEach(p -> aNettoyer.add(p.getCle()));
                  justifications.delete(j);
                  justifications.flush();
                  tracer(moi, a, "justification.retirer", "Justification retirée : " + libelle(a) + " redevient non justifiée", avant, null);
                  bilan.verifierPalier(List.of(a.getInscription().getId()));
                  return new Resultat("Justification retirée : l'absence est de nouveau non justifiée.", 1);
                });
    nettoyer(aNettoyer);
    return r;
  }

  public record Contenu(byte[] octets, String type, String nom) {}

  public Contenu piece(String id) {
    PieceJustificative p = pieces.findById(id).orElseThrow(() -> ApiException.introuvable("Pièce justificative"));
    byte[] o = stockage.lire(p.getCle());
    if (o == null) throw ApiException.introuvable("Fichier");
    return new Contenu(o, p.getTypeMime(), p.getNomFichier());
  }

  // ---------- Motifs ----------

  public List<Motif> motifs() {
    return motifs.findAllByOrderByOrdreAscLibelleAsc().stream().map(m -> new Motif(m.getId(), m.getLibelle(), m.getOrdre(), m.isActif())).toList();
  }

  @Transactional
  public List<Motif> creerMotif(MotifRequete d) {
    String libelle = d.libelle().strip();
    if (motifs.existsByLibelleIgnoreCaseAndIdNot(libelle, "")) throw ApiException.conflit("Ce motif existe déjà.");
    MotifJustification m = new MotifJustification();
    m.setLibelle(libelle);
    m.setOrdre(motifs.findAll().stream().mapToInt(MotifJustification::getOrdre).max().orElse(0) + 1);
    motifs.saveAndFlush(m);
    return motifs();
  }

  /** Renommer ou retirer de la liste (un motif déjà utilisé n'est jamais supprimé). */
  @Transactional
  public List<Motif> modifierMotif(String id, MotifRequete d) {
    MotifJustification m = motifs.findById(id).orElseThrow(() -> ApiException.introuvable("Motif"));
    String libelle = d.libelle().strip();
    if (motifs.existsByLibelleIgnoreCaseAndIdNot(libelle, id)) throw ApiException.conflit("Ce motif existe déjà.");
    m.setLibelle(libelle);
    if (d.actif() != null) m.setActif(d.actif());
    motifs.saveAndFlush(m);
    return motifs();
  }

  // ---------- Historique ----------

  private static Map<String, Object> champs(Justification j) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("motif", j.getMotif().getLibelle());
    m.put("description", j.getDescription());
    m.put("dateReception", j.getDateReception() == null ? null : j.getDateReception().toString());
    m.put("pieces", j.getPieces().stream().map(PieceJustificative::getNomFichier).toList());
    return m;
  }

  private static String ecart(Map<String, Object> avant, Map<String, Object> apres) {
    List<String> l = new ArrayList<>();
    if (!Objects.equals(avant.get("motif"), apres.get("motif"))) l.add("motif " + avant.get("motif") + " → " + apres.get("motif"));
    if (!Objects.equals(avant.get("dateReception"), apres.get("dateReception"))) l.add("reçue le " + date(apres.get("dateReception")));
    if (!Objects.equals(avant.get("description"), apres.get("description"))) l.add("description modifiée");
    if (!Objects.equals(avant.get("pieces"), apres.get("pieces"))) l.add("pièces : " + ((List<?>) apres.get("pieces")).size());
    return l.isEmpty() ? "" : " (" + String.join(", ", l) + ")";
  }

  private static String date(Object iso) {
    return iso == null ? "?" : DATE.format(LocalDate.parse(iso.toString()));
  }

  static String libelleCourt(Absence a) {
    return a.getSeance().getNomMatiere() + " du " + DATE.format(a.getSeance().getFiche().getDate()) + " à " + a.getSeance().getDebut();
  }

  private static String libelle(Absence a) {
    return "absence de " + a.getInscription().getEleve().nomComplet() + " en " + libelleCourt(a);
  }

  private void tracer(UtilisateurConnecte moi, Absence a, String action, String libelle, Map<String, Object> avant, Map<String, Object> apres) {
    HistoriqueAbsence h = new HistoriqueAbsence();
    h.setFicheId(a.getSeance().getFiche().getId());
    h.setClasseId(a.getSeance().getFiche().getClasse().getId());
    h.setDate(a.getSeance().getFiche().getDate());
    h.setAbsenceId(a.getId());
    h.setAction(action);
    h.setLibelle(Textes.couper(Character.toUpperCase(libelle.charAt(0)) + libelle.substring(1), 1000));
    h.setAvant(avant);
    h.setApres(apres);
    h.setPar(utilisateurs.getReferenceById(moi.id()));
    historique.save(h);
  }
}
