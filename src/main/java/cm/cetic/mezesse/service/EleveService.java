package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.ImportEleves;
import cm.cetic.mezesse.document.ImportEleves.LigneLue;
import cm.cetic.mezesse.dto.EleveDto.AnneeRef;
import cm.cetic.mezesse.dto.EleveDto.ClasseRef;
import cm.cetic.mezesse.dto.EleveDto.Creation;
import cm.cetic.mezesse.dto.EleveDto.ErreurLigne;
import cm.cetic.mezesse.dto.EleveDto.Fiche;
import cm.cetic.mezesse.dto.EleveDto.Identite;
import cm.cetic.mezesse.dto.EleveDto.InscriptionVue;
import cm.cetic.mezesse.dto.EleveDto.Ligne;
import cm.cetic.mezesse.dto.EleveDto.Modification;
import cm.cetic.mezesse.dto.EleveDto.Nombre;
import cm.cetic.mezesse.dto.EleveDto.PhotoCle;
import cm.cetic.mezesse.dto.EleveDto.Reinscription;
import cm.cetic.mezesse.dto.EleveDto.RenvoiVue;
import cm.cetic.mezesse.dto.EleveDto.ResultatLot;
import cm.cetic.mezesse.dto.EleveDto.Vue;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.exception.GlobalExceptionHandler;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.EleveRepository;
import cm.cetic.mezesse.repository.EtablissementRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.NoteRepository;
import cm.cetic.mezesse.repository.RenvoiRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import cm.cetic.mezesse.util.Horloge;
import cm.cetic.mezesse.util.Textes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Élèves : inscription (un par un, par lot, par import), fiche, photos, renvoi et réinscription. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EleveService {

  private static final Set<String> TYPES_PHOTO = Set.of("image/jpeg", "image/png");
  private static final int LOT_MAX = 200;

  /** Libellés des champs dans les messages d'erreur d'un lot ou d'un import. */
  private static final Map<String, String> LIBELLES =
      Map.of(
          "nom", "nom", "prenoms", "prénoms", "sexe", "sexe", "dateNaissance", "date de naissance", "lieuNaissance", "lieu de naissance",
          "matriculeNational", "matricule national", "parentNom", "parent", "parentTelephone", "téléphone du parent",
          "parentAdresse", "adresse du parent", "redoublant", "redoublant");

  private final EleveRepository eleves;
  private final InscriptionRepository inscriptions;
  private final RenvoiRepository renvois;
  private final ClasseRepository classes;
  private final EtablissementRepository etablissements;
  private final NoteRepository notes;
  private final UtilisateurRepository utilisateurs;
  private final MatriculeService matricules;
  private final StockageService stockage;
  private final JournalDecisions journal;
  private final AnneeCouranteService anneeCourante;
  private final ImportEleves importEleves;
  private final Validator validateur;
  private final ObjectMapper json;
  private final PlatformTransactionManager transactions;

  /** Champs de la fiche qu'une désapprobation remet en l'état (AnnulationService). */
  static Map<String, Object> fiche(Eleve e) {
    return Donnees.objet(
        "matriculeNational", e.getMatriculeNational(), "nom", e.getNom(), "prenoms", e.getPrenoms(), "sexe", e.getSexe().name(),
        "dateNaissance", e.getDateNaissance().toString(), "lieuNaissance", e.getLieuNaissance(), "parentNom", e.getParentNom(),
        "parentTelephone", e.getParentTelephone(), "parentAdresse", e.getParentAdresse());
  }

  // ---------- Consultation ----------

  /** Élèves d'une année ; par défaut les actifs, ou statut RENVOYE, ou TOUS. */
  public List<Ligne> liste(String anneeId, String statut, String classeId, String q) {
    Specification<Inscription> spec =
        (root, query, cb) -> {
          Join<Object, Object> eleve = (Join<Object, Object>) root.fetch("eleve", JoinType.INNER);
          Join<Object, Object> classe = (Join<Object, Object>) root.fetch("classe", JoinType.INNER);
          root.fetch("annee", JoinType.INNER);
          List<Predicate> conditions = new ArrayList<>();
          conditions.add(cb.equal(root.get("annee").get("id"), anneeId));
          String s = statut == null ? "ACTIF" : statut;
          if (!s.equals("TOUS")) conditions.add(cb.equal(root.get("statut"), statutDe(s)));
          if (classeId != null) conditions.add(cb.equal(classe.get("id"), classeId));
          if (q != null) {
            String motif = "%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            conditions.add(
                cb.or(
                    cb.like(cb.lower(eleve.get("nom")), motif, '\\'),
                    cb.like(cb.lower(eleve.get("prenoms")), motif, '\\'),
                    cb.like(cb.lower(eleve.get("matricule")), motif, '\\'),
                    cb.like(cb.lower(eleve.get("matriculeNational")), motif, '\\')));
          }
          query.orderBy(cb.asc(classe.get("niveau")), cb.asc(eleve.get("nom")), cb.asc(eleve.get("prenoms")));
          return cb.and(conditions.toArray(Predicate[]::new));
        };
    List<Inscription> liste = inscriptions.findAll(spec);
    Map<String, RenvoiVue> dernierRenvoi = new LinkedHashMap<>();
    if (!liste.isEmpty()) {
      for (Renvoi r : renvois.desInscriptions(liste.stream().map(Inscription::getId).toList())) {
        dernierRenvoi.putIfAbsent(r.getInscription().getId(), RenvoiVue.de(r));
      }
    }
    return liste.stream()
        .map(
            i ->
                new Ligne(
                    i.getId(), i.getEleveId(), i.getAnneeId(), i.getClasseId(), i.isRedoublant(), i.getStatut(), i.getCreeLe(), Vue.de(i.getEleve()),
                    classeRef(i.getClasse()), anneeRef(i.getAnnee()),
                    dernierRenvoi.containsKey(i.getId()) ? List.of(dernierRenvoi.get(i.getId())) : List.of()))
        .toList();
  }

  private static StatutInscription statutDe(String s) {
    try {
      return StatutInscription.valueOf(s);
    } catch (IllegalArgumentException e) {
      throw ApiException.invalide("statut : ACTIF, RENVOYE ou TOUS");
    }
  }

  private static ClasseRef classeRef(Classe c) {
    return new ClasseRef(c.getId(), c.getNom(), c.getNiveau());
  }

  private static AnneeRef anneeRef(AnneeScolaire a) {
    return new AnneeRef(a.getId(), a.getLibelle());
  }

  public Fiche fiche(String id) {
    Eleve e = eleves.findById(id).orElseThrow(() -> ApiException.introuvable("Élève"));
    List<Inscription> parcours = inscriptions.deLEleve(id);
    Map<String, List<RenvoiVue>> parInscription =
        parcours.isEmpty()
            ? Map.of()
            : renvois.desInscriptions(parcours.stream().map(Inscription::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getInscription().getId(), LinkedHashMap::new, Collectors.mapping(RenvoiVue::de, Collectors.toList())));
    return new Fiche(
        e.getId(), e.getMatricule(), e.getMatriculeNational(), e.getNom(), e.getPrenoms(), e.getSexe(), e.getDateNaissance(), e.getLieuNaissance(),
        e.getPhotoCle(), e.getParentNom(), e.getParentTelephone(), e.getParentAdresse(), e.getCreeLe(), e.getMajLe(),
        parcours.stream()
            .map(
                i ->
                    new InscriptionVue(
                        i.getId(), i.getEleveId(), i.getAnneeId(), i.getClasseId(), i.isRedoublant(), i.getStatut(), i.getCreeLe(),
                        classeRef(i.getClasse()), anneeRef(i.getAnnee()), parInscription.getOrDefault(i.getId(), List.of())))
            .toList());
  }

  // ---------- Inscription ----------

  private record Contexte(AnneeScolaire annee, String prefixe, Classe classe) {}

  private Contexte contexte(String anneeId, String classeId) {
    Classe classe = classes.findById(classeId).orElse(null);
    if (classe == null || !classe.getAnneeId().equals(anneeId)) throw ApiException.invalide("Cette classe n'appartient pas à l'année choisie.");
    String prefixe = etablissements.findById(Etablissement.ID).map(Etablissement::getPrefixeMatricule).orElse("CTM");
    return new Contexte(classe.getAnnee(), prefixe, classe);
  }

  private static void remplir(Eleve e, Identite d) {
    e.setMatriculeNational(Textes.optionnel(d.getMatriculeNational()));
    e.setNom(Textes.majuscules(d.getNom().strip()));
    e.setPrenoms(d.getPrenoms().strip());
    e.setSexe(Sexe.valueOf(d.getSexe()));
    e.setDateNaissance(d.date());
    e.setLieuNaissance(d.getLieuNaissance().strip());
    e.setParentNom(Textes.optionnel(d.getParentNom()));
    e.setParentTelephone(Textes.optionnel(d.getParentTelephone()));
    e.setParentAdresse(Textes.optionnel(d.getParentAdresse()));
  }

  /** Crée l'élève, son matricule et son inscription dans la classe. */
  private Eleve inscrire(Contexte c, Identite d, String photoCle) {
    Eleve e = new Eleve();
    remplir(e, d);
    e.setMatricule(matricules.nouveau(c.prefixe(), c.annee().getLibelle()));
    e.setPhotoCle(photoCle);
    eleves.save(e);
    Inscription i = new Inscription();
    i.setEleve(e);
    i.setAnnee(c.annee());
    i.setClasse(c.classe());
    i.setRedoublant(d.estRedoublant());
    inscriptions.save(i);
    return e;
  }

  /** Inscription d'un élève par la direction, directement dans une classe de l'année. */
  @Transactional
  public Vue creer(UtilisateurConnecte moi, String anneeId, Creation d) {
    Contexte c = contexte(anneeId, d.getClasseId());
    Eleve e = inscrire(c, d, null);
    journal.consigner(
        moi, DomaineDecision.SCOLARITE, "eleve.inscrire",
        "Inscription de " + e.nomComplet() + " (" + e.getMatricule() + ") en " + c.classe().getNom() + (d.estRedoublant() ? ", redoublant" : ""),
        "/eleves/" + e.getId(), Donnees.objet("eleveId", e.getId()));
    return Vue.de(e);
  }

  /** Problèmes d'une ligne, dans l'ordre des champs, sans doublon : « nom obligatoire, sexe à choisir ». */
  private List<String> problemes(Identite ligne) {
    List<String> ordre = GlobalExceptionHandler.ordreDesChamps(Identite.class);
    Set<String> vus = new LinkedHashSet<>();
    validateur.validate(ligne).stream()
        .sorted(Comparator.comparingInt((ConstraintViolation<Identite> v) -> ordre.indexOf(v.getPropertyPath().toString())))
        .forEach(v -> vus.add(LIBELLES.getOrDefault(v.getPropertyPath().toString(), v.getPropertyPath().toString()) + " " + v.getMessage()));
    return new ArrayList<>(vus);
  }

  /**
   * Saisie groupée : plusieurs élèves d'une même classe, enregistrés d'un coup. Tout ou rien : une
   * seule ligne invalide et rien n'est enregistré, avec l'erreur de chaque ligne. Photos facultatives :
   * champ « photo_&lt;n° de ligne - 1&gt; ».
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ResultatLot ajouterLot(UtilisateurConnecte moi, String anneeDemandee, JsonNode corps, Map<String, MultipartFile> fichiers) {
    if (corps == null || !corps.isObject()) throw ApiException.invalide("Données invalides.");
    String classeId = corps.path("classeId").asText("").strip();
    if (classeId.isEmpty()) throw ApiException.invalide("classeId : classe obligatoire");
    JsonNode lignes = corps.path("eleves");
    if (!lignes.isArray() || lignes.isEmpty()) throw ApiException.invalide("eleves : aucun élève à enregistrer");
    if (lignes.size() > LOT_MAX) throw ApiException.invalide("eleves : " + LOT_MAX + " élèves au plus par envoi");
    Map<String, MultipartFile> photos = photosRecues(fichiers);

    List<ErreurLigne> erreurs = new ArrayList<>();
    List<Identite> valides = new ArrayList<>();
    List<MultipartFile> photosValides = new ArrayList<>();
    for (int i = 0; i < lignes.size(); i++) {
      Identite ligne;
      List<String> problemes;
      try {
        ligne = json.treeToValue(lignes.get(i), Identite.class);
        problemes = problemes(ligne);
      } catch (IOException | IllegalArgumentException ex) {
        ligne = null;
        problemes = new ArrayList<>(List.of("données illisibles"));
      }
      MultipartFile photo = photos.get(String.valueOf(i));
      if (photo != null && !TYPES_PHOTO.contains(photo.getContentType())) problemes.add("photo en JPEG ou PNG uniquement");
      if (!problemes.isEmpty()) erreurs.add(new ErreurLigne(i + 1, String.join(", ", problemes)));
      else {
        valides.add(ligne);
        photosValides.add(photo);
      }
    }
    if (!erreurs.isEmpty()) {
      String detail = String.join("\n", erreurs.stream().limit(10).map(e -> "Ligne " + e.ligne() + " : " + e.message()).toList());
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "Rien n'a été enregistré : " + erreurs.size() + " ligne(s) à corriger.\n" + detail, Map.of("erreurs", erreurs));
    }
    String anneeId = anneeCourante.anneeDe(anneeDemandee);
    // Les photos sont déposées avant la transaction ; elles sont retirées si l'inscription échoue.
    List<String> cles = new ArrayList<>();
    for (MultipartFile p : photosValides) cles.add(p == null ? null : deposerPhoto(p, "inscription"));
    try {
      List<Eleve> crees =
          new TransactionTemplate(transactions)
              .execute(
                  t -> {
                    Contexte c = contexte(anneeId, classeId);
                    List<Eleve> liste = new ArrayList<>();
                    for (int k = 0; k < valides.size(); k++) liste.add(inscrire(c, valides.get(k), cles.get(k)));
                    List<String> noms = liste.stream().map(Eleve::nomComplet).toList();
                    journal.consigner(
                        moi, DomaineDecision.SCOLARITE, "eleve.ajouter",
                        "Inscription de " + liste.size() + " élève(s) en " + c.classe().getNom() + " : " + apercu(noms), "/eleves?classe=" + classeId,
                        Donnees.objet("eleveIds", liste.stream().map(Eleve::getId).toList()));
                    return liste;
                  });
      long avecPhoto = cles.stream().filter(Objects::nonNull).count();
      return new ResultatLot(
          crees.size() + " élève(s) inscrit(s)" + (avecPhoto > 0 ? ", dont " + avecPhoto + " avec photo" : "") + ".",
          crees.size(), crees.stream().map(Vue::de).toList());
    } catch (RuntimeException e) {
      cles.stream().filter(Objects::nonNull).forEach(stockage::supprimer);
      throw e;
    }
  }

  private static String apercu(List<String> noms) {
    return String.join(", ", noms.stream().limit(8).toList()) + (noms.size() > 8 ? "…" : "");
  }

  /** Photos d'un formulaire groupé : champ « photo_&lt;clé&gt; » → fichier. */
  private static Map<String, MultipartFile> photosRecues(Map<String, MultipartFile> fichiers) {
    Map<String, MultipartFile> photos = new LinkedHashMap<>();
    if (fichiers != null) fichiers.forEach((champ, f) -> {
      if (champ.startsWith("photo_")) photos.put(champ.substring(6), f);
    });
    return photos;
  }

  private String deposerPhoto(MultipartFile f, String prefixe) {
    String cle = "eleves/" + prefixe + "-" + UUID.randomUUID() + ("image/png".equals(f.getContentType()) ? ".png" : ".jpg");
    try {
      stockage.deposer(cle, f.getBytes(), f.getContentType());
    } catch (IOException e) {
      throw new IllegalStateException("Lecture de la photo impossible", e);
    }
    return cle;
  }

  /** Photos de plusieurs élèves d'un coup : champs « photo_&lt;id de l'élève&gt; ». Tout ou rien. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Nombre photos(UtilisateurConnecte moi, Map<String, MultipartFile> fichiers) {
    Map<String, MultipartFile> photos = photosRecues(fichiers);
    if (photos.isEmpty()) throw ApiException.invalide("Aucune photo reçue.");
    List<Eleve> trouves = eleves.findByIdIn(photos.keySet());
    long inconnus = photos.keySet().stream().filter(id -> trouves.stream().noneMatch(e -> e.getId().equals(id))).count();
    if (inconnus > 0) throw ApiException.invalide(inconnus + " élève(s) introuvable(s) : rechargez la page.");
    List<Eleve> mauvais = trouves.stream().filter(e -> !TYPES_PHOTO.contains(photos.get(e.getId()).getContentType())).toList();
    if (!mauvais.isEmpty()) {
      throw ApiException.invalide("Photos en JPEG ou PNG uniquement : " + String.join(", ", mauvais.stream().map(Eleve::nomComplet).toList()) + ".");
    }
    // Les anciennes photos sont gardées : une désapprobation peut les remettre.
    Map<String, String> nouvelles = new LinkedHashMap<>();
    for (Eleve e : trouves) nouvelles.put(e.getId(), deposerPhoto(photos.get(e.getId()), e.getId()));
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              t -> {
                List<Map<String, Object>> annulation = new ArrayList<>();
                List<String> noms = new ArrayList<>();
                for (Eleve e : eleves.findByIdIn(nouvelles.keySet())) {
                  annulation.add(Donnees.objet("eleveId", e.getId(), "avantCle", e.getPhotoCle(), "apresCle", nouvelles.get(e.getId())));
                  noms.add(e.nomComplet());
                  e.setPhotoCle(nouvelles.get(e.getId()));
                }
                journal.consigner(
                    moi, DomaineDecision.SCOLARITE, "eleve.photos", "Photos de " + noms.size() + " élève(s) : " + apercu(noms),
                    nouvelles.size() == 1 ? "/eleves/" + nouvelles.keySet().iterator().next() : null, Donnees.objet("photos", annulation));
              });
    } catch (RuntimeException e) {
      nouvelles.values().forEach(stockage::supprimer);
      throw e;
    }
    return new Nombre(nouvelles.size() + " photo(s) enregistrée(s).", nouvelles.size());
  }

  /** Photo d'identité d'un élève (affichée sur le bulletin et la carte scolaire). */
  @Transactional
  public PhotoCle changerPhoto(UtilisateurConnecte moi, String id, MultipartFile f) {
    if (f == null || f.isEmpty()) throw ApiException.invalide("Aucun fichier reçu.");
    if (!TYPES_PHOTO.contains(f.getContentType())) throw ApiException.invalide("Photo en JPEG ou PNG uniquement.");
    Eleve e = eleves.findById(id).orElseThrow(() -> ApiException.introuvable("Élève"));
    String cle = "eleves/" + id + "-" + System.currentTimeMillis() + ("image/png".equals(f.getContentType()) ? ".png" : ".jpg");
    try {
      stockage.deposer(cle, f.getBytes(), f.getContentType());
    } catch (IOException ex) {
      throw new IllegalStateException("Lecture de la photo impossible", ex);
    }
    String avant = e.getPhotoCle();
    e.setPhotoCle(cle);
    journal.consigner(
        moi, DomaineDecision.SCOLARITE, "eleve.photo", (avant != null ? "Remplacement" : "Ajout") + " de la photo de " + e.nomComplet(),
        "/eleves/" + id, Donnees.objet("eleveId", id, "avantCle", avant, "apresCle", cle));
    return new PhotoCle(cle);
  }

  public record Photo(byte[] contenu, String type) {}

  public Photo photo(String id) {
    String cle = eleves.findById(id).map(Eleve::getPhotoCle).orElse(null);
    byte[] contenu = cle == null ? null : stockage.lire(cle);
    if (contenu == null) throw ApiException.introuvable("Photo");
    return new Photo(contenu, cle.endsWith(".png") ? "image/png" : "image/jpeg");
  }

  // ---------- Modification ----------

  @Transactional
  public Vue modifier(UtilisateurConnecte moi, String id, String anneeId, Modification d) {
    Eleve e = eleves.findById(id).orElseThrow(() -> ApiException.introuvable("Élève"));
    Map<String, Object> avantFiche = fiche(e);
    remplir(e, d);
    Inscription ins = inscriptions.findByEleve_IdAndAnnee_Id(id, anneeId).orElse(null);
    String classeAvant = ins == null ? null : ins.getClasseId();
    Boolean redoublantAvant = ins == null ? null : ins.isRedoublant();
    List<String> changements = new ArrayList<>();
    String classeId = Textes.optionnel(d.getClasseId());
    if (ins != null && (classeId != null || d.getRedoublant() != null)) {
      if (classeId != null && !classeId.equals(ins.getClasseId())) {
        if (notes.countByInscription_Id(ins.getId()) > 0) {
          throw ApiException.conflit("L'élève a déjà des notes dans sa classe : le changement de classe n'est plus possible.");
        }
        Contexte c = contexte(anneeId, classeId);
        changements.add("changement de classe : " + ins.getClasse().getNom() + " → " + c.classe().getNom());
        ins.setClasse(c.classe());
      }
      if (d.getRedoublant() != null && d.getRedoublant() != ins.isRedoublant()) {
        changements.add(d.getRedoublant() ? "déclaré redoublant" : "ne redouble plus");
        ins.setRedoublant(d.getRedoublant());
      }
    }
    eleves.saveAndFlush(e);
    Map<String, Object> avant = new LinkedHashMap<>(avantFiche);
    avant.put("classeId", classeAvant);
    avant.put("redoublant", redoublantAvant);
    Map<String, Object> apres = fiche(e);
    apres.put("classeId", ins == null ? null : ins.getClasseId());
    apres.put("redoublant", ins == null ? null : ins.isRedoublant());
    journal.consigner(
        moi, DomaineDecision.SCOLARITE, "eleve.modifier",
        "Modification de la fiche de " + e.nomComplet() + (changements.isEmpty() ? "" : " : " + String.join(", ", changements)),
        "/eleves/" + id, Donnees.objet("eleveId", id, "inscriptionId", ins == null ? null : ins.getId(), "avant", avant, "apres", apres));
    return Vue.de(e);
  }

  // ---------- Import en masse ----------

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Nombre importer(UtilisateurConnecte moi, String anneeDemandee, String classeId, MultipartFile f) {
    if (f == null || f.isEmpty()) throw ApiException.invalide("Aucun fichier reçu.");
    if (classeId == null || classeId.isBlank()) throw ApiException.invalide("classeId : classe obligatoire");
    String anneeId = anneeCourante.anneeDe(anneeDemandee);
    List<LigneLue> lignes;
    try {
      lignes = importEleves.lire(f.getBytes(), f.getOriginalFilename());
    } catch (IOException e) {
      throw ApiException.invalide("Fichier illisible.");
    }
    List<String> erreurs = new ArrayList<>();
    for (LigneLue l : lignes) {
      List<String> p = problemes(l.identite()).stream().map(s -> s.startsWith("date de naissance") ? s + " (JJ/MM/AAAA)" : s).toList();
      if (!p.isEmpty()) erreurs.add("Ligne " + l.numero() + " : " + String.join(", ", p));
    }
    if (!erreurs.isEmpty()) throw ApiException.invalide("Import refusé, aucun élève créé.\n" + String.join("\n", erreurs.stream().limit(15).toList()));
    if (lignes.isEmpty()) throw ApiException.invalide("Aucun élève trouvé dans le fichier.");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            t -> {
              Contexte c = contexte(anneeId, classeId);
              List<String> ids = new ArrayList<>();
              for (LigneLue l : lignes) ids.add(inscrire(c, l.identite(), null).getId());
              journal.consigner(
                  moi, DomaineDecision.SCOLARITE, "eleve.importer",
                  "Import de " + lignes.size() + " élève(s) en " + c.classe().getNom() + " (fichier " + f.getOriginalFilename() + ")",
                  "/eleves?classe=" + classeId, Donnees.objet("eleveIds", ids));
            });
    return new Nombre(lignes.size() + " élève(s) importé(s).", lignes.size());
  }

  // ---------- Renvoi et réintégration (Proviseur) ----------

  @Transactional
  public void renvoyer(UtilisateurConnecte moi, String inscriptionId, String motif, java.time.LocalDate date) {
    Inscription ins = inscriptions.findById(inscriptionId).orElseThrow(() -> ApiException.introuvable("Inscription"));
    if (ins.getStatut() == StatutInscription.RENVOYE) throw ApiException.conflit("Cet élève est déjà renvoyé.");
    ins.setStatut(StatutInscription.RENVOYE);
    Renvoi r = new Renvoi();
    r.setInscription(ins);
    r.setMotif(motif);
    r.setDate(date);
    r.setPar(utilisateurs.getReferenceById(moi.id()));
    renvois.save(r);
  }

  @Transactional
  public void reintegrer(UtilisateurConnecte moi, String inscriptionId, String motif) {
    Inscription ins = inscriptions.findById(inscriptionId).orElseThrow(() -> ApiException.introuvable("Inscription"));
    if (ins.getStatut() != StatutInscription.RENVOYE) throw ApiException.conflit("Cet élève n'est pas renvoyé.");
    ins.setStatut(StatutInscription.ACTIF);
    renvois.findByInscription_IdAndReintegreLeIsNullOrderByCreeLeDesc(inscriptionId).stream()
        .findFirst()
        .ifPresent(
            r -> {
              r.setReintegreLe(Horloge.maintenant());
              r.setReintegrePar(utilisateurs.getReferenceById(moi.id()));
              r.setMotifReintegration(Textes.optionnel(motif));
            });
  }

  // ---------- Réinscription d'une année sur l'autre (SG) ----------

  @Transactional
  public String reinscrire(UtilisateurConnecte moi, Reinscription d) {
    Classe classe = classes.findById(d.classeId()).orElseThrow(() -> ApiException.introuvable("Classe"));
    List<String> deja = inscriptions.elevesDejaInscrits(classe.getAnneeId(), d.eleveIds());
    List<String> aInscrire = d.eleveIds().stream().distinct().filter(id -> !deja.contains(id)).toList();
    List<Eleve> trouves = eleves.findByIdIn(aInscrire);
    if (trouves.size() != aInscrire.size()) throw ApiException.invalide("Un des élèves choisis est introuvable.");
    List<String> creees = new ArrayList<>();
    for (Eleve e : trouves) {
      Inscription i = new Inscription();
      i.setEleve(e);
      i.setAnnee(classe.getAnnee());
      i.setClasse(classe);
      i.setRedoublant(d.redoublant());
      creees.add(inscriptions.save(i).getId());
    }
    if (!aInscrire.isEmpty()) {
      journal.consigner(
          moi, DomaineDecision.SCOLARITE, "eleve.reinscrire",
          "Réinscription de " + aInscrire.size() + " élève(s) en " + classe.getNom() + " pour " + classe.getAnnee().getLibelle()
              + (d.redoublant() ? ", comme redoublants" : ""),
          "/eleves?classe=" + classe.getId(), Donnees.objet("inscriptionIds", creees));
    }
    return aInscrire.size() + " élève(s) inscrit(s) en " + classe.getNom() + "."
        + (deja.isEmpty() ? "" : " " + deja.size() + " déjà inscrit(s) cette année, ignoré(s).");
  }

  public byte[] modeleImport() {
    return importEleves.modele();
  }
}
