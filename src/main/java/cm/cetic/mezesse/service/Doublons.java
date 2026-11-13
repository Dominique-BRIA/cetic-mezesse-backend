package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.EleveDto.Identite;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.DecisionRepository;
import cm.cetic.mezesse.repository.EleveRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.util.Donnees;
import cm.cetic.mezesse.util.Horloge;
import cm.cetic.mezesse.util.Ressemblance;
import cm.cetic.mezesse.util.Ressemblance.Force;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Doublons probables, signalés sans être refusés. Un client qui sait afficher l'avertissement envoie
 * « X-Doublons: verifier » : s'il existe une fiche semblable, il reçoit un 409 « DOUBLON » avec les
 * fiches en question, et renvoie « X-Doublons: confirme » si l'utilisateur maintient sa saisie. Sans
 * en-tête (anciennes versions de l'application), rien ne change. Dans tous les cas, le journal du SG
 * garde la trace des fiches semblables.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Doublons {

  public static final String ENTETE = "X-Doublons";
  private static final Duration RECENT = Duration.ofMinutes(15);
  private static final Duration REPETITION = Duration.ofMinutes(10);
  private static final int MAX_PAR_SAISIE = 5;
  private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  public enum Mode {
    IGNORER,
    VERIFIER,
    CONFIRME
  }

  /**
   * Fiche existante qui ressemble à la saisie.
   *
   * @param force FORTE (mêmes lettres, ou même date de naissance) ou FAIBLE (une faute de frappe d'écart)
   * @param ligne rang de la saisie dans un lot ou un import (1, 2…), sinon null
   */
  public record Candidat(String id, String type, String libelle, String detail, String force, String lien, Instant creeLe, Integer ligne) {}

  private final UtilisateurRepository utilisateurs;
  private final EleveRepository eleves;
  private final InscriptionRepository inscriptions;
  private final MatiereRepository matieres;
  private final ClasseRepository classes;
  private final DecisionRepository decisions;

  public static Mode mode() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes r)) return Mode.IGNORER;
    String v = r.getRequest().getHeader(ENTETE);
    if ("verifier".equalsIgnoreCase(v)) return Mode.VERIFIER;
    if ("confirme".equalsIgnoreCase(v)) return Mode.CONFIRME;
    return Mode.IGNORER;
  }

  /**
   * Avertit le client s'il le demande ; sinon renvoie la mention à ajouter au journal.
   *
   * @param objet « un compte », « un élève », « une matière »…
   */
  public static String controler(String objet, List<Candidat> candidats) {
    if (candidats.isEmpty()) return "";
    if (mode() == Mode.VERIFIER) {
      String titre = recent(candidats) ? "Vous venez d'enregistrer " + objet + " semblable" : majuscule(objet) + " semblable existe déjà";
      throw new ApiException(
          HttpStatus.CONFLICT, titre + " : " + noms(candidats) + ". Confirmez pour enregistrer quand même.",
          Donnees.objet("code", "DOUBLON", "titre", titre, "candidats", candidats));
    }
    return " (fiche semblable : " + noms(candidats) + ")";
  }

  /**
   * Avertissement d'un autre genre (cours prévus à la même heure…) : même protocole que les doublons,
   * avec un titre propre.
   */
  public static String avertir(String titre, List<Candidat> candidats) {
    if (candidats.isEmpty()) return "";
    if (mode() == Mode.VERIFIER) {
      throw new ApiException(
          HttpStatus.CONFLICT, titre + " : " + noms(candidats) + ". Confirmez pour enregistrer quand même.",
          Donnees.objet("code", "DOUBLON", "titre", titre, "candidats", candidats));
    }
    return " (" + titre.toLowerCase(java.util.Locale.FRENCH) + " : " + noms(candidats) + ")";
  }

  /** Même chose pour un lot ou un import : les fiches semblables sont rangées par ligne. */
  public static String controlerLot(List<Candidat> candidats) {
    if (candidats.isEmpty()) return "";
    long lignes = candidats.stream().map(Candidat::ligne).distinct().count();
    if (mode() == Mode.VERIFIER) {
      String titre = lignes + " élève(s) ressemble(nt) à des fiches existantes";
      String detail =
          String.join("\n", candidats.stream().limit(10).map(c -> "Ligne " + c.ligne() + " : semblable à " + c.libelle() + (c.detail() == null ? "" : " (" + c.detail() + ")")).toList());
      throw new ApiException(
          HttpStatus.CONFLICT, titre + ".\n" + detail + "\nConfirmez pour enregistrer quand même.",
          Donnees.objet("code", "DOUBLON", "titre", titre, "candidats", candidats));
    }
    return " (" + lignes + " élève(s) semblable(s) à des fiches existantes)";
  }

  /**
   * Même action sur la même fiche il y a quelques minutes (réinitialiser deux fois un mot de passe) :
   * le client qui le demande est averti avant de recommencer.
   *
   * @param fait « réinitialisé le mot de passe de Paul MBARGA »
   */
  public void repetition(String action, String cibleId, String fait) {
    if (mode() != Mode.VERIFIER) return;
    Instant maintenant = Horloge.maintenant();
    decisions.findByActionAndCreeLeAfterOrderByCreeLeDesc(action, maintenant.minus(REPETITION)).stream()
        .filter(d -> d.getAnnulation() != null && cibleId.equals(String.valueOf(d.getAnnulation().get("id"))))
        .findFirst()
        .ifPresent(
            d -> {
              long minutes = Duration.between(d.getCreeLe(), maintenant).toMinutes();
              String quand = minutes < 1 ? "à l'instant" : "il y a " + minutes + " min";
              throw new ApiException(
                  HttpStatus.CONFLICT, "Vous avez " + fait + " " + quand + ". Recommencer ?",
                  Donnees.objet("code", "REPETITION", "titre", "Opération déjà faite", "candidats", List.of()));
            });
  }

  // ---------- Recherche des fiches semblables ----------

  public List<Candidat> enseignants(String exclu, String nom, String prenom) {
    String saisie = prenom + " " + nom;
    return garder(
        utilisateurs.findAll().stream()
            .filter(u -> !u.getId().equals(exclu) && u.getFusionneDansId() == null)
            .map(u -> enseignant(u, Ressemblance.comparer(saisie, u.getPrenom() + " " + u.getNom()), null)));
  }

  static Candidat enseignant(Utilisateur u, Force f, Integer ligne) {
    if (f == null) return null;
    String detail = joindre(u.getDiscipline(), u.getTelephone(), u.isActif() ? null : "compte désactivé");
    return new Candidat(u.getId(), "enseignant", u.getPrenom() + " " + u.getNom(), detail, force(f), "/enseignants", u.getCreeLe(), ligne);
  }

  public List<Candidat> eleves(String exclu, String nom, String prenoms, LocalDate naissance, String anneeId) {
    List<Eleve> tous = elevesActifs(exclu);
    List<Proche> proches = proches(tous, nom, prenoms);
    Map<String, String> classesActuelles = classesActuelles(anneeId, proches.stream().map(p -> p.eleve().getId()).toList());
    return garder(proches.stream().map(p -> eleve(p.eleve(), p.force(), naissance, classesActuelles, null)));
  }

  /** Chaque ligne d'un lot comparée aux fiches existantes, puis aux lignes qui la précèdent. */
  public List<Candidat> elevesDuLot(List<? extends Identite> lignes, String anneeId) {
    List<Eleve> tous = elevesActifs(null);
    List<Candidat> resultat = new ArrayList<>();
    Map<String, String> classesActuelles = new HashMap<>();
    for (int i = 0; i < lignes.size(); i++) {
      Identite l = lignes.get(i);
      List<Proche> proches = proches(tous, l.getNom(), l.getPrenoms());
      List<String> sansClasse = proches.stream().map(p -> p.eleve().getId()).filter(id -> !classesActuelles.containsKey(id)).toList();
      classesActuelles.putAll(classesActuelles(anneeId, sansClasse));
      int rang = i + 1;
      resultat.addAll(garder(proches.stream().map(p -> eleve(p.eleve(), p.force(), l.date(), classesActuelles, rang))));
      for (int j = 0; j < i; j++) {
        Identite avant = lignes.get(j);
        Force f = Ressemblance.comparer(l.getPrenoms() + " " + l.getNom(), avant.getPrenoms() + " " + avant.getNom());
        if (f != null) {
          resultat.add(new Candidat(null, "ligne", avant.getNom() + " " + avant.getPrenoms(), "ligne " + (j + 1) + " du même envoi", force(f), null, null, rang));
        }
      }
    }
    return resultat;
  }

  private List<Eleve> elevesActifs(String exclu) {
    return eleves.findAll().stream().filter(e -> e.getFusionneDansId() == null && !e.getId().equals(exclu)).toList();
  }

  private record Proche(Eleve eleve, Force force) {}

  private static List<Proche> proches(List<Eleve> tous, String nom, String prenoms) {
    String saisie = prenoms + " " + nom;
    return tous.stream().map(e -> new Proche(e, Ressemblance.comparer(saisie, e.getPrenoms() + " " + e.getNom()))).filter(p -> p.force() != null).toList();
  }

  private static Candidat eleve(Eleve e, Force f, LocalDate naissance, Map<String, String> classesActuelles, Integer ligne) {
    boolean memeNaissance = naissance != null && naissance.equals(e.getDateNaissance());
    String detail =
        joindre(
            "né(e) le " + e.getDateNaissance().format(JOUR) + (memeNaissance ? ", même date" : ""),
            classesActuelles.getOrDefault(e.getId(), "sans classe cette année"), e.getMatricule());
    String force = f == Force.IDENTIQUE || memeNaissance ? "FORTE" : "FAIBLE";
    return new Candidat(e.getId(), "eleve", e.nomComplet(), detail, force, "/eleves/" + e.getId(), e.getCreeLe(), ligne);
  }

  private Map<String, String> classesActuelles(String anneeId, Collection<String> eleveIds) {
    if (anneeId == null || eleveIds.isEmpty()) return Map.of();
    return inscriptions.classesDesEleves(anneeId, eleveIds).stream().collect(Collectors.toMap(r -> (String) r[0], r -> (String) r[1], (a, b) -> a));
  }

  public List<Candidat> matieres(String classeId, String nom, String exclu) {
    return garder(
        matieres.findByClasse_Id(classeId).stream()
            .filter(m -> !m.getId().equals(exclu))
            .map(m -> matiere(m, Ressemblance.comparer(nom, m.getNom()))));
  }

  private static Candidat matiere(Matiere m, Force f) {
    if (f == null) return null;
    Utilisateur p = m.getProfesseur();
    String detail = joindre("coefficient " + m.getCoefficient(), p == null ? "sans enseignant" : p.getPrenom() + " " + p.getNom());
    return new Candidat(m.getId(), "matiere", m.getNom() + " (" + m.getClasse().getNom() + ")", detail, force(f), "/matieres?classe=" + m.getClasseId(), null, null);
  }

  /** Une classe de même niveau et même spécialité : en créer une autre renommera les deux (A et B). */
  public List<Candidat> classes(String anneeId, int niveau, String specialiteId) {
    return classes.duGroupe(anneeId, niveau, specialiteId).stream()
        .map(Doublons::classe)
        .toList();
  }

  private static Candidat classe(Classe c) {
    return new Candidat(c.getId(), "classe", c.getNom(), "une nouvelle classe de ce niveau renommera les classes en A, B…", "FORTE", "/classes", null, null);
  }

  // ---------- Outils ----------

  private static List<Candidat> garder(Stream<Candidat> candidats) {
    return candidats.filter(Objects::nonNull).sorted(Comparator.comparing(Candidat::force).reversed().thenComparing(Candidat::libelle)).limit(MAX_PAR_SAISIE).toList();
  }

  private static String force(Force f) {
    return f == Force.IDENTIQUE ? "FORTE" : "FAIBLE";
  }

  private static boolean recent(List<Candidat> candidats) {
    Instant limite = Instant.now().minus(RECENT);
    return candidats.stream().anyMatch(c -> c.creeLe() != null && c.creeLe().isAfter(limite));
  }

  private static String noms(List<Candidat> candidats) {
    return String.join(", ", candidats.stream().limit(3).map(Candidat::libelle).toList()) + (candidats.size() > 3 ? "…" : "");
  }

  private static String majuscule(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String joindre(String... morceaux) {
    String r = String.join(", ", Stream.of(morceaux).filter(m -> m != null && !m.isBlank()).toList());
    return r.isEmpty() ? null : r;
  }
}
