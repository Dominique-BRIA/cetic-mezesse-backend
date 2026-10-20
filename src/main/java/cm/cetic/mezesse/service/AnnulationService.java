package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.EtablissementDto.Seuil;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.GroupeMatiere;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.entity.Specialite;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.EleveRepository;
import cm.cetic.mezesse.repository.EtablissementRepository;
import cm.cetic.mezesse.repository.GroupeMatiereRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.NoteRepository;
import cm.cetic.mezesse.repository.SequenceRepository;
import cm.cetic.mezesse.repository.SpecialiteRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Désapprouver une décision du SG défait son action (RG-30). Chaque décision garde dans
 * « annulation » de quoi revenir en arrière ; ce service applique ce retour, dans la même transaction
 * que la désapprobation. Une annulation devenue impossible lève un conflit qui explique pourquoi, et
 * la décision reste approuvée.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AnnulationService {

  /** Ce que la désapprobation fera, affiché au Proviseur avant de confirmer. */
  public static final Map<String, String> EFFETS =
      Map.ofEntries(
          Map.entry("classe.creer", "La classe sera supprimée."),
          Map.entry("classe.modifier", "La classe reprendra ses valeurs précédentes."),
          Map.entry("classe.supprimer", "La classe sera recréée avec ses matières."),
          Map.entry("specialite.creer", "La spécialité sera supprimée."),
          Map.entry("specialite.modifier", "La spécialité reprendra son ancien nom, et ses classes aussi."),
          Map.entry("specialite.supprimer", "La spécialité sera recréée."),
          Map.entry("groupe.creer", "Le groupe de matières sera supprimé."),
          Map.entry("groupe.modifier", "Le groupe de matières reprendra ses valeurs précédentes."),
          Map.entry("groupe.supprimer", "Le groupe de matières sera recréé."),
          Map.entry("matiere.creer", "La matière sera supprimée."),
          Map.entry("matiere.modifier", "La matière reprendra ses valeurs précédentes."),
          Map.entry("matiere.supprimer", "La matière sera recréée, sans les notes effacées avec elle."),
          Map.entry("enseignant.creer", "Le compte sera supprimé, ou seulement désactivé si l'enseignant a déjà saisi des notes."),
          Map.entry("enseignant.modifier", "Le compte reprendra ses valeurs précédentes."),
          Map.entry("enseignant.desactiver", "Le compte sera réactivé."),
          Map.entry("enseignant.reactiver", "Le compte sera de nouveau désactivé."),
          Map.entry("enseignant.reinitialiser", "L'ancien mot de passe sera rétabli."),
          Map.entry("eleve.inscrire", "L'élève sera retiré de la plateforme."),
          Map.entry("eleve.modifier", "La fiche de l’élève reprendra ses valeurs précédentes."),
          Map.entry("eleve.photo", "L'ancienne photo sera remise."),
          Map.entry("eleve.photos", "Les photos précédentes seront remises."),
          Map.entry("eleve.importer", "Les élèves importés seront retirés de la plateforme."),
          Map.entry("eleve.ajouter", "Les élèves inscrits ensemble seront retirés de la plateforme."),
          Map.entry("eleve.reinscrire", "Ces réinscriptions seront retirées."),
          Map.entry("annee.creer", "L'année scolaire sera supprimée."),
          Map.entry("annee.activer", "L'année qui était en cours le redeviendra."),
          Map.entry("sequence.ouvrir", "La saisie des notes sera refermée."),
          Map.entry("sequence.fermer", "La saisie des notes sera rouverte."),
          Map.entry("etablissement.modifier", "Les informations de l'établissement reprendront leurs valeurs précédentes."),
          Map.entry("bareme.modifier", "L'ancien barème sera rétabli."),
          Map.entry("tableauhonneur.seuil", "Le tableau d'honneur reprendra son ancienne moyenne minimale."));

  /** Ce qui a été défait, et les fichiers à retirer du stockage une fois la transaction validée. */
  public record Resultat(String effet, List<String> fichiersASupprimer) {
    Resultat(String effet) {
      this(effet, List.of());
    }
  }

  private static final Resultat RIEN = new Resultat("Rien à défaire : les valeurs ont été modifiées depuis par une autre décision.");

  private final ClasseRepository classes;
  private final SpecialiteRepository specialites;
  private final GroupeMatiereRepository groupes;
  private final MatiereRepository matieres;
  private final UtilisateurRepository utilisateurs;
  private final EleveRepository eleves;
  private final InscriptionRepository inscriptions;
  private final NoteRepository notes;
  private final AnneeScolaireRepository annees;
  private final SequenceRepository sequences;
  private final EtablissementRepository etablissements;
  private final NommageClassesService nommage;
  private final SpecialiteService specialiteService;
  private final EtablissementService etablissementService;
  private final AnneeService anneeService;
  private final NotificationService notifications;

  // ---------- Comparaison des valeurs enregistrées dans le journal ----------

  private static final Pattern MINUIT = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T00:00:00(\\.0+)?Z$");

  /** Forme comparable d'une valeur : nombres, dates (« 2011-05-26 » ou à minuit UTC), listes. */
  static Object norme(Object v) {
    if (v == null) return null;
    if (v instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
    if (v instanceof LocalDate d) return d.toString();
    if (v instanceof Enum<?> e) return e.name();
    if (v instanceof Collection<?> c) return c.stream().map(AnnulationService::norme).toList();
    if (v instanceof String s) {
      var m = MINUIT.matcher(s);
      return m.matches() ? m.group(1) : s;
    }
    return v.toString();
  }

  static boolean egal(Object a, Object b) {
    return Objects.equals(norme(a), norme(b));
  }

  /**
   * Ne remet que les champs encore tels que la décision les avait laissés : une modification faite
   * depuis, par une autre décision, n'est pas écrasée.
   */
  static Map<String, Object> aRestaurer(Map<String, Object> actuel, Map<String, Object> avant, Map<String, Object> apres) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (String k : avant.keySet()) {
      if (!egal(avant.get(k), apres.get(k)) && egal(actuel.get(k), apres.get(k))) r.put(k, avant.get(k));
    }
    return r;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> carte(Object v) {
    return v == null ? new LinkedHashMap<>() : new LinkedHashMap<>((Map<String, Object>) v);
  }

  @SuppressWarnings("unchecked")
  private static List<String> textes(Object v) {
    return v == null ? List.of() : ((List<Object>) v).stream().map(o -> o == null ? null : o.toString()).toList();
  }

  private static String texte(Object v) {
    return v == null ? null : v.toString();
  }

  private static int entier(Object v) {
    return ((Number) v).intValue();
  }

  private static LocalDate date(Object v) {
    String s = texte(v);
    return s == null ? null : LocalDate.parse(s.substring(0, 10));
  }

  // ---------- Annulation ----------

  public Resultat annuler(String action, Map<String, Object> a) {
    return switch (action) {
      case "classe.creer" -> classeCreee(a);
      case "classe.modifier" -> classeModifiee(a);
      case "classe.supprimer" -> classeSupprimee(a);
      case "specialite.creer" -> specialiteCreee(a);
      case "specialite.modifier" -> specialiteModifiee(a);
      case "specialite.supprimer" -> specialiteSupprimee(a);
      case "groupe.creer" -> groupeCree(a);
      case "groupe.modifier" -> groupeModifie(a);
      case "groupe.supprimer" -> groupeSupprime(a);
      case "matiere.creer" -> matiereCreee(a);
      case "matiere.modifier" -> matiereModifiee(a);
      case "matiere.supprimer" -> matiereSupprimee(a);
      case "enseignant.creer" -> enseignantCree(a);
      case "enseignant.modifier", "enseignant.desactiver", "enseignant.reactiver" -> enseignantModifie(a);
      case "enseignant.reinitialiser" -> motDePasseReinitialise(a);
      case "eleve.inscrire" -> eleveInscrit(a);
      case "eleve.modifier" -> eleveModifie(a);
      case "eleve.photo" -> photoChangee(a);
      case "eleve.photos" -> photosChangees(a);
      case "eleve.importer", "eleve.ajouter" -> elevesAjoutes(a);
      case "eleve.reinscrire" -> reinscriptions(a);
      case "annee.creer" -> anneeCreee(a);
      case "annee.activer" -> anneeActivee(a);
      case "sequence.ouvrir", "sequence.fermer" -> saisieChangee(a);
      case "etablissement.modifier" -> etablissementModifie(a);
      case "tableauhonneur.seuil" -> seuilModifie(a);
      case "bareme.modifier" -> baremeModifie(a);
      default -> throw ApiException.conflit("Ce type de décision ne peut pas être annulé automatiquement.");
    };
  }

  // ---------- Classes ----------

  private Resultat classeCreee(Map<String, Object> a) {
    Classe c = classes.findById(texte(a.get("classeId"))).orElse(null);
    if (c == null) return new Resultat("La classe avait déjà été supprimée.");
    long n = inscriptions.countByClasse_Id(c.getId());
    if (n > 0) throw ApiException.conflit(c.getNom() + " a maintenant " + n + " élève(s) inscrit(s) : retirez-les avant de désapprouver.");
    String anneeId = c.getAnneeId();
    int niveau = c.getNiveau();
    String specialiteId = c.getSpecialiteId();
    String nom = c.getNom();
    for (GroupeMatiere g : c.getGroupes()) g.getClasses().remove(c);
    matieres.deleteAll(matieres.findByClasse_Id(c.getId()));
    classes.delete(c);
    nommage.renommerGroupe(anneeId, niveau, specialiteId);
    return new Resultat(nom + " a été supprimée.");
  }

  private static Map<String, Object> champsClasse(Classe c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("niveau", c.getNiveau());
    m.put("specialiteId", c.getSpecialiteId());
    m.put("profPrincipalId", c.getProfPrincipalId());
    return m;
  }

  private Resultat classeModifiee(Map<String, Object> a) {
    Classe c = classes.findById(texte(a.get("classeId"))).orElseThrow(() -> ApiException.conflit("Cette classe n'existe plus."));
    Map<String, Object> data = aRestaurer(champsClasse(c), carte(a.get("avant")), carte(a.get("apres")));
    if (data.isEmpty()) return RIEN;
    int niveauAvant = c.getNiveau();
    String specialiteAvant = c.getSpecialiteId();
    int niveau = data.containsKey("niveau") ? entier(data.get("niveau")) : c.getNiveau();
    String specialiteId = data.containsKey("specialiteId") ? texte(data.get("specialiteId")) : c.getSpecialiteId();
    boolean deplacee = niveau != niveauAvant || !Objects.equals(specialiteId, specialiteAvant);
    Specialite s = null;
    if (specialiteId != null) s = specialites.findById(specialiteId).orElseThrow(() -> ApiException.conflit("L'ancienne spécialité n'existe plus."));
    int numero = deplacee ? nommage.numeroSuivant(c.getAnneeId(), niveau, specialiteId) : c.getNumero();
    c.setNiveau(niveau);
    if (data.containsKey("specialiteId")) c.setSpecialite(s);
    if (data.containsKey("profPrincipalId")) {
      String pp = texte(data.get("profPrincipalId"));
      c.setProfPrincipal(pp == null ? null : utilisateurs.findById(pp).orElse(null));
    }
    c.setNumero(numero);
    if (deplacee) {
      nommage.renommerGroupe(c.getAnneeId(), niveauAvant, specialiteAvant);
      nommage.renommerGroupe(c.getAnneeId(), niveau, specialiteId);
    }
    return new Resultat(c.getNom() + " a repris ses valeurs précédentes.");
  }

  @SuppressWarnings("unchecked")
  private Resultat classeSupprimee(Map<String, Object> a) {
    List<GroupeMatiere> sesGroupes = groupes.findAllById(textes(a.get("groupeIds")));
    Set<String> existants = new HashSet<>(sesGroupes.stream().map(GroupeMatiere::getId).toList());
    List<Map<String, Object>> toutes = a.get("matieres") == null ? List.of() : (List<Map<String, Object>>) a.get("matieres");
    List<Map<String, Object>> gardees = toutes.stream().filter(m -> existants.contains(texte(m.get("groupeId")))).toList();
    String specialiteId = texte(a.get("specialiteId"));
    Specialite s = null;
    if (specialiteId != null) {
      s = specialites.findById(specialiteId).orElseThrow(() -> ApiException.conflit("La spécialité de cette classe n'existe plus : recréez-la d'abord."));
    }
    String anneeId = texte(a.get("anneeId"));
    Classe c = new Classe();
    c.setAnnee(annees.findById(anneeId).orElseThrow(() -> ApiException.conflit("L'année de cette classe n'existe plus.")));
    c.setNiveau(entier(a.get("niveau")));
    c.setSpecialite(s);
    c.setNumero(entier(a.get("numero")));
    c.setNom("…" + anneeId + "-" + System.currentTimeMillis());
    String pp = texte(a.get("profPrincipalId"));
    c.setProfPrincipal(pp == null ? null : utilisateurs.findById(pp).orElse(null));
    classes.save(c);
    for (GroupeMatiere g : sesGroupes) g.getClasses().add(c);
    for (Map<String, Object> m : gardees) {
      Matiere nouvelle = new Matiere();
      nouvelle.setClasse(c);
      nouvelle.setGroupe(groupes.getReferenceById(texte(m.get("groupeId"))));
      nouvelle.setNom(texte(m.get("nom")));
      nouvelle.setNomEn(texte(m.get("nomEn")));
      nouvelle.setCoefficient(entier(m.get("coefficient")));
      nouvelle.setOrdre(entier(m.get("ordre")));
      String prof = texte(m.get("professeurId"));
      nouvelle.setProfesseur(prof == null ? null : utilisateurs.findById(prof).orElse(null));
      matieres.save(nouvelle);
    }
    nommage.renommerGroupe(anneeId, c.getNiveau(), specialiteId);
    int perdues = toutes.size() - gardees.size();
    return new Resultat(
        c.getNom() + " a été recréée avec " + gardees.size() + " matière(s)" + (perdues > 0 ? " (" + perdues + " dont le groupe n'existe plus)" : "") + ".");
  }

  // ---------- Spécialités ----------

  private Resultat specialiteCreee(Map<String, Object> a) {
    Specialite s = specialites.findById(texte(a.get("id"))).orElse(null);
    if (s == null) return new Resultat("La spécialité avait déjà été supprimée.");
    long n = classes.countBySpecialite_Id(s.getId());
    if (n > 0) throw ApiException.conflit("La spécialité " + s.getNom() + " est maintenant portée par " + n + " classe(s) : changez-les d'abord.");
    specialites.delete(s);
    return new Resultat("La spécialité " + s.getNom() + " a été supprimée.");
  }

  private Resultat specialiteModifiee(Map<String, Object> a) {
    Specialite s = specialites.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Cette spécialité n'existe plus."));
    Map<String, Object> actuel = new LinkedHashMap<>();
    actuel.put("nom", s.getNom());
    actuel.put("nomEn", s.getNomEn());
    Map<String, Object> data = aRestaurer(actuel, carte(a.get("avant")), carte(a.get("apres")));
    if (data.isEmpty()) return RIEN;
    if (data.containsKey("nom")) s.setNom(texte(data.get("nom")));
    if (data.containsKey("nomEn")) s.setNomEn(texte(data.get("nomEn")));
    specialites.saveAndFlush(s);
    specialiteService.renommerClasses(s.getId());
    return new Resultat("La spécialité a repris le nom " + s.getNom() + ".");
  }

  private Resultat specialiteSupprimee(Map<String, Object> a) {
    Specialite s = new Specialite();
    s.setNom(texte(a.get("nom")));
    s.setNomEn(texte(a.get("nomEn")));
    specialites.saveAndFlush(s);
    return new Resultat("La spécialité " + s.getNom() + " a été recréée.");
  }

  // ---------- Groupes de matières ----------

  private Resultat groupeCree(Map<String, Object> a) {
    GroupeMatiere g = groupes.findById(texte(a.get("id"))).orElse(null);
    if (g == null) return new Resultat("Le groupe avait déjà été supprimé.");
    long n = matieres.countByGroupe_Id(g.getId());
    if (n > 0) throw ApiException.conflit("Le groupe " + g.getNom() + " contient maintenant " + n + " matière(s) : déplacez-les d'abord.");
    groupes.delete(g);
    return new Resultat("Le groupe " + g.getNom() + " a été supprimé.");
  }

  private static List<String> triee(Object ids) {
    return textes(ids).stream().sorted().toList();
  }

  private Resultat groupeModifie(Map<String, Object> a) {
    GroupeMatiere g = groupes.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Ce groupe n'existe plus."));
    Map<String, Object> actuel = new LinkedHashMap<>();
    actuel.put("nom", g.getNom());
    actuel.put("nomEn", g.getNomEn());
    actuel.put("ordre", g.getOrdre());
    actuel.put("classeIds", g.getClasses().stream().map(Classe::getId).sorted().toList());
    Map<String, Object> avant = carte(a.get("avant"));
    avant.put("classeIds", triee(avant.get("classeIds")));
    Map<String, Object> apres = carte(a.get("apres"));
    apres.put("classeIds", triee(apres.get("classeIds")));
    Map<String, Object> data = aRestaurer(actuel, avant, apres);
    if (data.isEmpty()) return RIEN;
    if (data.containsKey("classeIds")) {
      List<String> ids = textes(data.get("classeIds")).stream().filter(Objects::nonNull).toList();
      long dansLesAutres = matieres.countByGroupe_Id(g.getId()) - (ids.isEmpty() ? 0 : matieres.countByGroupe_IdAndClasse_IdIn(g.getId(), ids));
      if (dansLesAutres > 0) {
        throw ApiException.conflit("Des matières de ce groupe appartiennent à des classes qu'il faudrait retirer : déplacez-les d'abord.");
      }
      g.getClasses().clear();
      g.getClasses().addAll(new LinkedHashSet<>(classes.findAllById(ids)));
    }
    if (data.containsKey("nom")) g.setNom(texte(data.get("nom")));
    if (data.containsKey("nomEn")) g.setNomEn(texte(data.get("nomEn")));
    if (data.containsKey("ordre")) g.setOrdre(entier(data.get("ordre")));
    return new Resultat("Le groupe " + g.getNom() + " a repris ses valeurs précédentes.");
  }

  private Resultat groupeSupprime(Map<String, Object> a) {
    GroupeMatiere g = new GroupeMatiere();
    g.setAnnee(annees.findById(texte(a.get("anneeId"))).orElseThrow(() -> ApiException.conflit("L'année de ce groupe n'existe plus.")));
    g.setNom(texte(a.get("nom")));
    g.setNomEn(texte(a.get("nomEn")));
    g.setOrdre(entier(a.get("ordre")));
    g.getClasses().addAll(classes.findAllById(textes(a.get("classeIds"))));
    groupes.saveAndFlush(g);
    return new Resultat("Le groupe " + g.getNom() + " a été recréé.");
  }

  // ---------- Matières ----------

  private Resultat matiereCreee(Map<String, Object> a) {
    Matiere m = matieres.findById(texte(a.get("id"))).orElse(null);
    if (m == null) return new Resultat("La matière avait déjà été supprimée.");
    long n = notes.countByMatiere_Id(m.getId());
    if (n > 0) throw ApiException.conflit(n + " note(s) ont déjà été saisies en " + m.getNom() + " : la matière ne peut plus être retirée automatiquement.");
    String classe = m.getClasse().getNom();
    matieres.delete(m);
    return new Resultat("La matière " + m.getNom() + " (" + classe + ") a été supprimée.");
  }

  private Resultat matiereModifiee(Map<String, Object> a) {
    Matiere m = matieres.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Cette matière n'existe plus."));
    Map<String, Object> data = aRestaurer(MatiereService.champs(m), carte(a.get("avant")), carte(a.get("apres")));
    if (data.isEmpty()) return RIEN;
    String classeId = data.containsKey("classeId") ? texte(data.get("classeId")) : m.getClasseId();
    String groupeId = data.containsKey("groupeId") ? texte(data.get("groupeId")) : m.getGroupeId();
    if (!classeId.equals(m.getClasseId()) && notes.countByMatiere_Id(m.getId()) > 0) {
      throw ApiException.conflit("Des notes ont été saisies depuis : la matière ne peut plus revenir dans son ancienne classe.");
    }
    if (!groupes.existsByIdAndClasses_Id(groupeId, classeId)) throw ApiException.conflit("L'ancien groupe n'est plus rattaché à cette classe.");
    if (data.containsKey("nom")) m.setNom(texte(data.get("nom")));
    if (data.containsKey("nomEn")) m.setNomEn(texte(data.get("nomEn")));
    if (data.containsKey("coefficient")) m.setCoefficient(entier(data.get("coefficient")));
    if (data.containsKey("ordre")) m.setOrdre(entier(data.get("ordre")));
    m.setClasse(classes.getReferenceById(classeId));
    m.setGroupe(groupes.getReferenceById(groupeId));
    if (data.containsKey("professeurId")) {
      String prof = texte(data.get("professeurId"));
      // Un professeur supprimé depuis n'est pas remis.
      if (prof == null) m.setProfesseur(null);
      else utilisateurs.findById(prof).ifPresent(m::setProfesseur);
    }
    return new Resultat("La matière " + m.getNom() + " a repris ses valeurs précédentes.");
  }

  private Resultat matiereSupprimee(Map<String, Object> a) {
    String classeId = texte(a.get("classeId"));
    String groupeId = texte(a.get("groupeId"));
    if (!groupes.existsByIdAndClasses_Id(groupeId, classeId)) throw ApiException.conflit("La classe ou le groupe de cette matière n'existe plus.");
    Matiere m = new Matiere();
    m.setClasse(classes.getReferenceById(classeId));
    m.setGroupe(groupes.getReferenceById(groupeId));
    m.setNom(texte(a.get("nom")));
    m.setNomEn(texte(a.get("nomEn")));
    m.setCoefficient(entier(a.get("coefficient")));
    m.setOrdre(entier(a.get("ordre")));
    String prof = texte(a.get("professeurId"));
    m.setProfesseur(prof == null ? null : utilisateurs.findById(prof).orElse(null));
    matieres.saveAndFlush(m);
    long n = a.get("notes") == null ? 0 : ((Number) a.get("notes")).longValue();
    return new Resultat("La matière " + m.getNom() + " a été recréée" + (n > 0 ? " ; ses " + n + " note(s) effacée(s) ne sont pas récupérables" : "") + ".");
  }

  // ---------- Enseignants ----------

  private Resultat enseignantCree(Map<String, Object> a) {
    Utilisateur u = utilisateurs.findById(texte(a.get("id"))).orElse(null);
    if (u == null) return new Resultat("Le compte avait déjà été supprimé.");
    if (notes.countBySaisiePar_Id(u.getId()) > 0) {
      u.setActif(false);
      return new Resultat(u.nomComplet() + " a déjà saisi des notes : son compte a été désactivé plutôt que supprimé.");
    }
    utilisateurs.delete(u);
    return new Resultat("Le compte de " + u.nomComplet() + " a été supprimé.");
  }

  private Resultat enseignantModifie(Map<String, Object> a) {
    Utilisateur u = utilisateurs.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Ce compte n'existe plus."));
    Map<String, Object> data = aRestaurer(UtilisateurService.champs(u), carte(a.get("avant")), carte(a.get("apres")));
    if (data.isEmpty()) return RIEN;
    if (data.containsKey("nom")) u.setNom(texte(data.get("nom")));
    if (data.containsKey("prenom")) u.setPrenom(texte(data.get("prenom")));
    if (data.containsKey("telephone")) u.setTelephone(texte(data.get("telephone")));
    if (data.containsKey("email")) u.setEmail(texte(data.get("email")));
    if (data.containsKey("discipline")) u.setDiscipline(texte(data.get("discipline")));
    if (data.containsKey("actif")) u.setActif(Boolean.TRUE.equals(data.get("actif")));
    if (data.containsKey("sexe")) u.setSexe(data.get("sexe") == null ? null : Sexe.valueOf(texte(data.get("sexe"))));
    if (data.containsKey("matricule")) u.setMatricule(texte(data.get("matricule")));
    if (data.containsKey("grade")) u.setGrade(texte(data.get("grade")));
    if (data.containsKey("fonction")) u.setFonction(texte(data.get("fonction")));
    if (data.containsKey("datePriseService")) u.setDatePriseService(date(data.get("datePriseService")));
    if (data.containsKey("dateEntreeFonctionPublique")) u.setDateEntreeFonctionPublique(date(data.get("dateEntreeFonctionPublique")));
    if (data.containsKey("acteNomination")) u.setActeNomination(texte(data.get("acteNomination")));
    return new Resultat("Le compte de " + u.nomComplet() + " a repris ses valeurs précédentes.");
  }

  private Resultat motDePasseReinitialise(Map<String, Object> a) {
    Utilisateur u = utilisateurs.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Ce compte n'existe plus."));
    if (!u.getMotDePasse().equals(texte(a.get("apresHash")))) {
      throw ApiException.conflit(u.nomComplet() + " a déjà choisi un nouveau mot de passe depuis la réinitialisation.");
    }
    u.setMotDePasse(texte(a.get("avantHash")));
    u.setDoitChangerMotDePasse(Boolean.TRUE.equals(a.get("avantDoitChanger")));
    // Une session ouverte entre-temps avec « password » est fermée.
    u.fermerSessions();
    return new Resultat(u.nomComplet() + " retrouve son ancien mot de passe.");
  }

  // ---------- Élèves ----------

  private Resultat eleveInscrit(Map<String, Object> a) {
    Eleve e = eleves.findById(texte(a.get("eleveId"))).orElse(null);
    if (e == null) return new Resultat("L'élève avait déjà été retiré.");
    long n = notes.countByInscription_Eleve_Id(e.getId());
    if (n > 0) throw ApiException.conflit(e.nomComplet() + " a déjà " + n + " note(s) : l'élève ne peut plus être retiré automatiquement.");
    eleves.supprimerParIds(List.of(e.getId()));
    return new Resultat(
        e.nomComplet() + " (" + e.getMatricule() + ") a été retiré de la plateforme.", e.getPhotoCle() == null ? List.of() : List.of(e.getPhotoCle()));
  }

  private Resultat eleveModifie(Map<String, Object> a) {
    Eleve e = eleves.findById(texte(a.get("eleveId"))).orElseThrow(() -> ApiException.conflit("Cet élève n'existe plus."));
    Map<String, Object> avant = carte(a.get("avant"));
    Map<String, Object> apres = carte(a.get("apres"));
    Map<String, Object> avantEleve = new LinkedHashMap<>(avant);
    Map<String, Object> apresEleve = new LinkedHashMap<>(apres);
    for (String k : List.of("classeId", "redoublant")) {
      avantEleve.remove(k);
      apresEleve.remove(k);
    }
    Map<String, Object> dataEleve = aRestaurer(EleveService.fiche(e), avantEleve, apresEleve);
    Map<String, Object> dataIns = new LinkedHashMap<>();
    String inscriptionId = texte(a.get("inscriptionId"));
    if (inscriptionId != null) {
      Inscription ins = inscriptions.findById(inscriptionId).orElse(null);
      if (ins != null) {
        Map<String, Object> actuel = new LinkedHashMap<>();
        actuel.put("classeId", ins.getClasseId());
        actuel.put("redoublant", ins.isRedoublant());
        Map<String, Object> av = new LinkedHashMap<>();
        av.put("classeId", avant.get("classeId"));
        av.put("redoublant", avant.get("redoublant"));
        Map<String, Object> ap = new LinkedHashMap<>();
        ap.put("classeId", apres.get("classeId"));
        ap.put("redoublant", apres.get("redoublant"));
        dataIns = aRestaurer(actuel, av, ap);
        if (dataIns.get("classeId") != null) {
          if (notes.countByInscription_Id(ins.getId()) > 0) {
            throw ApiException.conflit("L'élève a des notes dans sa nouvelle classe : il ne peut plus revenir dans l'ancienne.");
          }
          Classe ancienne = classes.findById(texte(dataIns.get("classeId"))).orElseThrow(() -> ApiException.conflit("L'ancienne classe n'existe plus."));
          ins.setClasse(ancienne);
        }
        if (dataIns.get("redoublant") != null) ins.setRedoublant(Boolean.TRUE.equals(dataIns.get("redoublant")));
      }
    }
    if (dataEleve.isEmpty() && dataIns.isEmpty()) return RIEN;
    if (dataEleve.containsKey("matriculeNational")) e.setMatriculeNational(texte(dataEleve.get("matriculeNational")));
    if (dataEleve.containsKey("nom")) e.setNom(texte(dataEleve.get("nom")));
    if (dataEleve.containsKey("prenoms")) e.setPrenoms(texte(dataEleve.get("prenoms")));
    if (dataEleve.containsKey("sexe")) e.setSexe(Sexe.valueOf(texte(dataEleve.get("sexe"))));
    if (dataEleve.containsKey("dateNaissance")) e.setDateNaissance(date(dataEleve.get("dateNaissance")));
    if (dataEleve.containsKey("lieuNaissance")) e.setLieuNaissance(texte(dataEleve.get("lieuNaissance")));
    if (dataEleve.containsKey("parentNom")) e.setParentNom(texte(dataEleve.get("parentNom")));
    if (dataEleve.containsKey("parentTelephone")) e.setParentTelephone(texte(dataEleve.get("parentTelephone")));
    if (dataEleve.containsKey("parentAdresse")) e.setParentAdresse(texte(dataEleve.get("parentAdresse")));
    return new Resultat("La fiche de " + e.nomComplet() + " a repris ses valeurs précédentes.");
  }

  private Resultat photoChangee(Map<String, Object> a) {
    Eleve e = eleves.findById(texte(a.get("eleveId"))).orElseThrow(() -> ApiException.conflit("Cet élève n'existe plus."));
    if (!Objects.equals(e.getPhotoCle(), texte(a.get("apresCle")))) return new Resultat("La photo a été changée depuis : rien à défaire.");
    e.setPhotoCle(texte(a.get("avantCle")));
    return new Resultat("La photo précédente de " + e.nomComplet() + " a été remise.", List.of(texte(a.get("apresCle"))));
  }

  @SuppressWarnings("unchecked")
  private Resultat photosChangees(Map<String, Object> a) {
    List<Map<String, Object>> photos = a.get("photos") == null ? List.of() : (List<Map<String, Object>>) a.get("photos");
    List<String> fichiers = new ArrayList<>();
    for (Map<String, Object> ph : photos) {
      Eleve e = eleves.findById(texte(ph.get("eleveId"))).orElse(null);
      // Élève retiré ou photo changée depuis : on n'y touche pas.
      if (e == null || !Objects.equals(e.getPhotoCle(), texte(ph.get("apresCle")))) continue;
      e.setPhotoCle(texte(ph.get("avantCle")));
      fichiers.add(texte(ph.get("apresCle")));
    }
    return new Resultat(
        fichiers.isEmpty() ? "Les photos ont été changées depuis : rien à défaire." : fichiers.size() + " photo(s) précédente(s) remise(s).", fichiers);
  }

  private Resultat elevesAjoutes(Map<String, Object> a) {
    List<String> ids = textes(a.get("eleveIds"));
    long n = ids.isEmpty() ? 0 : notes.countByInscription_Eleve_IdIn(ids);
    if (n > 0) throw ApiException.conflit("Des élèves importés ont déjà " + n + " note(s) : l'import ne peut plus être défait automatiquement.");
    List<String> photos = ids.isEmpty() ? List.of() : eleves.findByIdInAndPhotoCleIsNotNull(ids).stream().map(Eleve::getPhotoCle).toList();
    int supprimes = ids.isEmpty() ? 0 : eleves.supprimerParIds(ids);
    return new Resultat(supprimes + " élève(s) ont été retirés de la plateforme.", photos);
  }

  private Resultat reinscriptions(Map<String, Object> a) {
    List<String> ids = textes(a.get("inscriptionIds"));
    long n = ids.isEmpty() ? 0 : notes.countByInscription_IdIn(ids);
    if (n > 0) throw ApiException.conflit("Des élèves réinscrits ont déjà " + n + " note(s) : la réinscription ne peut plus être défaite automatiquement.");
    int supprimees = ids.isEmpty() ? 0 : inscriptions.supprimerParIds(ids);
    return new Resultat(supprimees + " réinscription(s) retirée(s).");
  }

  // ---------- Administration ----------

  private Resultat anneeCreee(Map<String, Object> a) {
    AnneeScolaire an = annees.findById(texte(a.get("id"))).orElse(null);
    if (an == null) return new Resultat("L'année avait déjà été supprimée.");
    long n = inscriptions.countByAnnee_Id(an.getId());
    if (n > 0) throw ApiException.conflit("L'année " + an.getLibelle() + " compte déjà " + n + " inscription(s) : elle ne peut plus être supprimée.");
    boolean etaitActive = an.isActive();
    String libelle = an.getLibelle();
    annees.delete(an);
    annees.flush();
    String avantId = texte(a.get("activeAvantId"));
    if (etaitActive && avantId != null) annees.findById(avantId).ifPresent(x -> x.setActive(true));
    return new Resultat("L'année " + libelle + " a été supprimée.");
  }

  private Resultat anneeActivee(Map<String, Object> a) {
    String avantId = texte(a.get("activeAvantId"));
    if (avantId == null) return new Resultat("Aucune année n'était en cours avant : rien à défaire.");
    AnneeScolaire avant = annees.findById(avantId).orElseThrow(() -> ApiException.conflit("L'année qui était en cours n'existe plus."));
    anneeService.activer(avant);
    return new Resultat("L'année " + avant.getLibelle() + " est de nouveau l'année en cours.");
  }

  private Resultat saisieChangee(Map<String, Object> a) {
    Sequence s = sequences.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Cette séquence n'existe plus."));
    boolean avant = Boolean.TRUE.equals(a.get("avant"));
    if (s.isSaisieOuverte() == avant) return RIEN;
    s.setSaisieOuverte(avant);
    if (avant) notifications.notifierOuvertureSequence(s);
    return new Resultat("La saisie de la séquence " + s.getNumero() + " est de nouveau " + (avant ? "ouverte" : "fermée") + ".");
  }

  private Resultat etablissementModifie(Map<String, Object> a) {
    Etablissement e = etablissements.findById(Etablissement.ID).orElse(null);
    if (e == null || a.get("avant") == null) return new Resultat("Il n'y avait pas d'informations à rétablir.");
    Map<String, Object> data = aRestaurer(EtablissementService.champs(e), carte(a.get("avant")), carte(a.get("apres")));
    if (data.isEmpty()) return RIEN;
    data.forEach(
        (k, v) -> {
          String t = texte(v);
          switch (k) {
            case "nomFr" -> e.setNomFr(t);
            case "nomEn" -> e.setNomEn(t);
            case "sigle" -> e.setSigle(t);
            case "regionFr" -> e.setRegionFr(t);
            case "regionEn" -> e.setRegionEn(t);
            case "departementFr" -> e.setDepartementFr(t);
            case "departementEn" -> e.setDepartementEn(t);
            case "arrondissement" -> e.setArrondissement(t);
            case "localite" -> e.setLocalite(t);
            case "code" -> e.setCode(t);
            case "boitePostale" -> e.setBoitePostale(t);
            case "telephone" -> e.setTelephone(t);
            case "prefixeMatricule" -> e.setPrefixeMatricule(t);
            case "sigleEn" -> e.setSigleEn(t);
            default -> {}
          }
        });
    return new Resultat("Les informations de l'établissement ont repris leurs valeurs précédentes.");
  }

  private Resultat seuilModifie(Map<String, Object> a) {
    Etablissement e = etablissements.findById(Etablissement.ID).orElse(null);
    if (e == null) return new Resultat("Il n'y avait pas d'informations à rétablir.");
    if (!egal(e.getSeuilTableauHonneur(), a.get("apres"))) return RIEN;
    e.setSeuilTableauHonneur(new BigDecimal(a.get("avant").toString()));
    return new Resultat("Le tableau d'honneur part de nouveau de " + cm.cetic.mezesse.util.Nombres.fr(e.getSeuilTableauHonneur().doubleValue()) + " de moyenne.");
  }

  @SuppressWarnings("unchecked")
  private Resultat baremeModifie(Map<String, Object> a) {
    TypeBareme type = TypeBareme.valueOf(texte(a.get("type")));
    List<Map<String, Object>> avant = a.get("avant") == null ? List.of() : (List<Map<String, Object>>) a.get("avant");
    etablissementService.remplacer(
        type,
        avant.stream()
            .map(s -> new Seuil(texte(s.get("libelle")), texte(s.get("libelleLong")), ((Number) s.get("min")).doubleValue()))
            .toList());
    return new Resultat("L'ancien barème des " + (type == TypeBareme.GRADE ? "grades" : "cotes") + " a été rétabli.");
  }
}
