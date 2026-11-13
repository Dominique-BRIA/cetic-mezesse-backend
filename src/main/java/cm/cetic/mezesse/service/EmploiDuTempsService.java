package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.EnteteOfficiel;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Conflit;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.CreneauRequete;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.MatiereRef;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.NouvelleVersion;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Personne;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Version;
import cm.cetic.mezesse.dto.EmploiDuTempsDto.Vue;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Creneau;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.EmploiDuTemps;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.CreneauRepository;
import cm.cetic.mezesse.repository.EmploiDuTempsRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.Doublons.Candidat;
import cm.cetic.mezesse.util.Donnees;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emploi du temps des classes : une semaine type par classe, versionnée par date d'effet. La version en
 * vigueur un jour donné est la dernière dont la date d'effet n'est pas passée.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmploiDuTempsService {

  static final String[] JOURS = {"", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"};
  private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  /** Une séance de plus de six heures est sûrement une erreur de saisie. */
  private static final int DUREE_MAX = 360;

  private final EmploiDuTempsRepository versions;
  private final CreneauRepository creneaux;
  private final ClasseRepository classes;
  private final MatiereRepository matieres;
  private final JournalDecisions journal;

  static LocalDate aujourdhui() {
    return LocalDate.now(EnteteOfficiel.CAMEROUN);
  }

  // ---------- Règles ----------

  /** Version en vigueur à une date : la dernière dont la date d'effet est vide ou passée (versions triées). */
  static EmploiDuTemps enVigueur(List<EmploiDuTemps> triees, LocalDate jour) {
    EmploiDuTemps retenue = null;
    for (EmploiDuTemps e : triees) {
      if (e.getValableDu() == null || !e.getValableDu().isAfter(jour)) retenue = e;
    }
    return retenue;
  }

  /** Deux plages horaires se chevauchent (se toucher, 08:25 – 08:25, n'est pas un chevauchement). */
  static boolean chevauchent(LocalTime debutA, LocalTime finA, LocalTime debutB, LocalTime finB) {
    return debutA.isBefore(finB) && debutB.isBefore(finA);
  }

  // ---------- Lecture ----------

  /** L'emploi du temps d'une classe ; sans version précisée, celle en vigueur aujourd'hui. */
  public Vue vue(String classeId, String versionId) {
    Classe classe = classes.findById(classeId).orElseThrow(() -> ApiException.introuvable("Classe"));
    List<EmploiDuTemps> liste = versions.deLaClasse(classeId);
    EmploiDuTemps choisie;
    if (versionId != null) {
      choisie = liste.stream().filter(v -> v.getId().equals(versionId)).findFirst().orElseThrow(() -> ApiException.introuvable("Version"));
    } else {
      choisie = enVigueur(liste, aujourdhui());
      if (choisie == null && !liste.isEmpty()) choisie = liste.get(0);
    }
    List<Version> vues = versionsVues(liste);
    if (choisie == null) return new Vue(classeId, vues, null, List.of(), 0);
    String id = choisie.getId();
    List<Creneau> lignes = creneaux.desVersions(List.of(id));
    List<Creneau> ailleurs = autresClasses(classe, reference(choisie));
    List<cm.cetic.mezesse.dto.EmploiDuTempsDto.Creneau> sortie = new ArrayList<>();
    for (Creneau c : lignes) sortie.add(vue(c, conflits(c.getId(), c.getJour(), c.getDebut(), c.getFin(), c.getMatiere(), lignes, ailleurs)));
    int minutes = lignes.stream().mapToInt(Creneau::duree).sum();
    return new Vue(classeId, vues, vues.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(), sortie, minutes);
  }

  private static List<Version> versionsVues(List<EmploiDuTemps> liste) {
    EmploiDuTemps courante = enVigueur(liste, aujourdhui());
    List<Version> vues = new ArrayList<>();
    for (int i = 0; i < liste.size(); i++) {
      EmploiDuTemps e = liste.get(i);
      LocalDate suivante = i + 1 < liste.size() ? liste.get(i + 1).getValableDu() : null;
      vues.add(new Version(e.getId(), e.getValableDu(), suivante == null ? null : suivante.minusDays(1), e == courante));
    }
    return vues;
  }

  private static cm.cetic.mezesse.dto.EmploiDuTempsDto.Creneau vue(Creneau c, List<Conflit> conflits) {
    Matiere m = c.getMatiere();
    Utilisateur p = m.getProfesseur();
    return new cm.cetic.mezesse.dto.EmploiDuTempsDto.Creneau(
        c.getId(), c.getJour(), HEURE.format(c.getDebut()), HEURE.format(c.getFin()), c.duree(),
        new MatiereRef(m.getId(), m.getNom(), p == null ? null : new Personne(p.getId(), p.getNom(), p.getPrenom())),
        conflits);
  }

  /** Date à laquelle comparer avec les autres classes : la date d'effet de la version, ou aujourd'hui si elle est passée. */
  private static LocalDate reference(EmploiDuTemps e) {
    LocalDate jour = aujourdhui();
    return e.getValableDu() != null && e.getValableDu().isAfter(jour) ? e.getValableDu() : jour;
  }

  /** Créneaux des autres classes de l'année, dans leur version en vigueur à la date donnée. */
  private List<Creneau> autresClasses(Classe classe, LocalDate jour) {
    Map<String, List<EmploiDuTemps>> parClasse =
        versions.deLAnnee(classe.getAnnee().getId()).stream()
            .filter(e -> !e.getClasse().getId().equals(classe.getId()))
            .collect(Collectors.groupingBy((EmploiDuTemps x) -> x.getClasse().getId(), HashMap::new, Collectors.toList()));
    List<String> ids = new ArrayList<>();
    for (List<EmploiDuTemps> liste : parClasse.values()) {
      liste.sort(Comparator.comparing(EmploiDuTemps::getValableDu, Comparator.nullsFirst(Comparator.naturalOrder())));
      EmploiDuTemps e = enVigueur(liste, jour);
      if (e != null) ids.add(e.getId());
    }
    return ids.isEmpty() ? List.of() : creneaux.desVersions(ids);
  }

  /** Cours attendus en même temps : dans la classe (autre créneau), ou du même enseignant dans une autre classe. */
  private static List<Conflit> conflits(
      String exclu, int jour, LocalTime debut, LocalTime fin, Matiere matiere, List<Creneau> memeVersion, List<Creneau> ailleurs) {
    List<Conflit> liste = new ArrayList<>();
    for (Creneau c : memeVersion) {
      if (c.getId().equals(exclu) || c.getJour() != jour || !chevauchent(debut, fin, c.getDebut(), c.getFin())) continue;
      liste.add(conflit(c, true));
    }
    String prof = matiere.getProfesseurId();
    if (prof != null) {
      for (Creneau c : ailleurs) {
        if (c.getJour() != jour || !prof.equals(c.getMatiere().getProfesseurId()) || !chevauchent(debut, fin, c.getDebut(), c.getFin())) continue;
        liste.add(conflit(c, false));
      }
    }
    return liste;
  }

  private static Conflit conflit(Creneau c, boolean memeClasse) {
    return new Conflit(c.getMatiere().getClasse().getNom(), c.getMatiere().getNom(), HEURE.format(c.getDebut()), HEURE.format(c.getFin()), memeClasse);
  }

  // ---------- Versions ----------

  @Transactional
  public Vue creerVersion(UtilisateurConnecte moi, NouvelleVersion d) {
    Classe classe = classes.findById(d.classeId()).orElseThrow(() -> ApiException.introuvable("Classe"));
    List<EmploiDuTemps> liste = versions.deLaClasse(classe.getId());
    if (liste.stream().anyMatch(v -> Objects.equals(v.getValableDu(), d.valableDu()))) {
      throw ApiException.conflit(
          d.valableDu() == null ? "Cette classe a déjà un emploi du temps depuis la rentrée." : "Une version commence déjà le " + DATE.format(d.valableDu()) + ".");
    }
    EmploiDuTemps source = d.copier() ? (d.valableDu() == null ? null : enVigueur(liste, d.valableDu())) : null;
    EmploiDuTemps e = new EmploiDuTemps();
    e.setClasse(classe);
    e.setValableDu(d.valableDu());
    versions.saveAndFlush(e);
    int copies = 0;
    if (source != null) {
      for (Creneau c : creneaux.desVersions(List.of(source.getId()))) {
        Creneau n = new Creneau();
        n.setEmploi(e);
        n.setMatiere(c.getMatiere());
        n.setJour(c.getJour());
        n.setDebut(c.getDebut());
        n.setFin(c.getFin());
        creneaux.save(n);
        copies++;
      }
      creneaux.flush();
    }
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "edt.version.creer",
        "Nouvel emploi du temps de " + classe.getNom() + " " + aPartirDu(d.valableDu()) + (copies > 0 ? " (" + copies + " créneau(x) repris)" : ""),
        lien(classe.getId()), Donnees.objet("id", e.getId()));
    return vue(classe.getId(), e.getId());
  }

  @Transactional
  public Vue supprimerVersion(UtilisateurConnecte moi, String id) {
    EmploiDuTemps e = versions.findById(id).orElseThrow(() -> ApiException.introuvable("Version"));
    String classeId = e.getClasse().getId();
    String nomClasse = e.getClasse().getNom();
    List<Map<String, Object>> sauvegarde = new ArrayList<>();
    for (Creneau c : creneaux.desVersions(List.of(id))) sauvegarde.add(champs(c));
    versions.delete(e);
    versions.flush();
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "edt.version.supprimer",
        "Suppression de l'emploi du temps de " + nomClasse + " " + aPartirDu(e.getValableDu()) + " (" + sauvegarde.size() + " créneau(x))",
        lien(classeId), Donnees.objet("classeId", classeId, "valableDu", e.getValableDu(), "creneaux", sauvegarde));
    return vue(classeId, null);
  }

  // ---------- Créneaux ----------

  @Transactional
  public Vue ajouterCreneau(UtilisateurConnecte moi, CreneauRequete d) {
    Classe classe = classes.findById(d.classeId()).orElseThrow(() -> ApiException.introuvable("Classe"));
    EmploiDuTemps e = versionPour(classe, d.versionId());
    Matiere m = matiereDe(classe, d.matiereId());
    LocalTime debut = LocalTime.parse(d.debut());
    LocalTime fin = LocalTime.parse(d.fin());
    verifierHoraire(debut, fin);
    String note = avertir(classe, e, null, d.jour(), debut, fin, m);
    Creneau c = new Creneau();
    c.setEmploi(e);
    c.setMatiere(m);
    c.setJour(d.jour());
    c.setDebut(debut);
    c.setFin(fin);
    creneaux.saveAndFlush(c);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "edt.creneau.creer",
        "Emploi du temps de " + classe.getNom() + " : " + m.getNom() + " " + quand(d.jour(), debut, fin) + note, lien(classe.getId()), Donnees.objet("id", c.getId()));
    return vue(classe.getId(), e.getId());
  }

  @Transactional
  public Vue modifierCreneau(UtilisateurConnecte moi, String id, CreneauRequete d) {
    Creneau c = creneaux.findById(id).orElseThrow(() -> ApiException.introuvable("Créneau"));
    EmploiDuTemps e = c.getEmploi();
    Classe classe = e.getClasse();
    if (!classe.getId().equals(d.classeId())) throw ApiException.invalide("Ce créneau appartient à une autre classe.");
    Matiere m = matiereDe(classe, d.matiereId());
    LocalTime debut = LocalTime.parse(d.debut());
    LocalTime fin = LocalTime.parse(d.fin());
    verifierHoraire(debut, fin);
    String note = avertir(classe, e, id, d.jour(), debut, fin, m);
    Map<String, Object> avant = champs(c);
    String libelleAvant = c.getMatiere().getNom() + " " + quand(c.getJour(), c.getDebut(), c.getFin());
    c.setMatiere(m);
    c.setJour(d.jour());
    c.setDebut(debut);
    c.setFin(fin);
    creneaux.saveAndFlush(c);
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "edt.creneau.modifier",
        "Emploi du temps de " + classe.getNom() + " : " + libelleAvant + " devient " + m.getNom() + " " + quand(d.jour(), debut, fin) + note,
        lien(classe.getId()), Donnees.objet("id", id, "avant", avant, "apres", champs(c)));
    return vue(classe.getId(), e.getId());
  }

  @Transactional
  public Vue supprimerCreneau(UtilisateurConnecte moi, String id) {
    Creneau c = creneaux.findById(id).orElseThrow(() -> ApiException.introuvable("Créneau"));
    EmploiDuTemps e = c.getEmploi();
    Classe classe = e.getClasse();
    Map<String, Object> sauvegarde = champs(c);
    String libelle = c.getMatiere().getNom() + " " + quand(c.getJour(), c.getDebut(), c.getFin());
    creneaux.delete(c);
    creneaux.flush();
    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "edt.creneau.supprimer", "Emploi du temps de " + classe.getNom() + " : retrait de " + libelle,
        lien(classe.getId()), sauvegarde);
    return vue(classe.getId(), e.getId());
  }

  /** La version demandée, ou celle en vigueur aujourd'hui ; la première version est créée au premier créneau. */
  private EmploiDuTemps versionPour(Classe classe, String versionId) {
    if (versionId != null) {
      EmploiDuTemps e = versions.findById(versionId).orElseThrow(() -> ApiException.introuvable("Version"));
      if (!e.getClasse().getId().equals(classe.getId())) throw ApiException.invalide("Cette version appartient à une autre classe.");
      return e;
    }
    List<EmploiDuTemps> liste = versions.deLaClasse(classe.getId());
    EmploiDuTemps e = enVigueur(liste, aujourdhui());
    if (e != null) return e;
    if (!liste.isEmpty()) return liste.get(0);
    EmploiDuTemps premiere = new EmploiDuTemps();
    premiere.setClasse(classe);
    return versions.saveAndFlush(premiere);
  }

  private Matiere matiereDe(Classe classe, String matiereId) {
    Matiere m = matieres.findById(matiereId).orElseThrow(() -> ApiException.introuvable("Matière"));
    if (!m.getClasseId().equals(classe.getId())) throw ApiException.invalide("Cette matière n'est pas enseignée dans " + classe.getNom() + ".");
    return m;
  }

  private static void verifierHoraire(LocalTime debut, LocalTime fin) {
    if (!fin.isAfter(debut)) throw ApiException.invalide("L'heure de fin doit être après l'heure de début.");
    if (Duration.between(debut, fin).toMinutes() > DUREE_MAX) throw ApiException.invalide("Une séance ne peut pas durer plus de 6 heures.");
  }

  /** Avertit (sans refuser) si un autre cours est prévu à la même heure dans la classe ou pour l'enseignant. */
  private String avertir(Classe classe, EmploiDuTemps e, String exclu, int jour, LocalTime debut, LocalTime fin, Matiere m) {
    List<Creneau> memeVersion = creneaux.desVersions(List.of(e.getId()));
    List<Creneau> ailleurs = autresClasses(classe, reference(e));
    List<Candidat> candidats = new ArrayList<>();
    for (Conflit k : conflits(exclu, jour, debut, fin, m, memeVersion, ailleurs)) {
      String detail = k.memeClasse() ? "Même classe" : "Même enseignant, en " + k.classe();
      candidats.add(new Candidat(null, "creneau", k.matiere() + " de " + k.debut() + " à " + k.fin() + " (" + k.classe() + ")", detail, "HORAIRE", null, null, null));
    }
    return Doublons.avertir("Un autre cours est prévu à la même heure", candidats);
  }

  // ---------- Désapprobation par le Proviseur (AnnulationService) ----------

  static Map<String, Object> champs(Creneau c) {
    return Donnees.objet(
        "emploiId", c.getEmploiId() != null ? c.getEmploiId() : c.getEmploi().getId(), "matiereId", c.getMatiere().getId(), "jour", c.getJour(),
        "debut", HEURE.format(c.getDebut()), "fin", HEURE.format(c.getFin()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public String defaire(String action, Map<String, Object> a) {
    return switch (action) {
      case "edt.version.creer" -> {
        EmploiDuTemps e = versions.findById(texte(a.get("id"))).orElse(null);
        if (e == null) yield "Cette version de l'emploi du temps avait déjà été supprimée.";
        versions.delete(e);
        yield "La version de l'emploi du temps de " + e.getClasse().getNom() + " " + aPartirDu(e.getValableDu()) + " a été supprimée.";
      }
      case "edt.version.supprimer" -> {
        Classe classe = classes.findById(texte(a.get("classeId"))).orElseThrow(() -> ApiException.conflit("La classe n'existe plus."));
        LocalDate du = a.get("valableDu") == null ? null : LocalDate.parse(texte(a.get("valableDu")).substring(0, 10));
        if (versions.deLaClasse(classe.getId()).stream().anyMatch(v -> Objects.equals(v.getValableDu(), du))) {
          throw ApiException.conflit("Une autre version commence déjà à cette date : l'ancienne ne peut pas être recréée.");
        }
        EmploiDuTemps e = new EmploiDuTemps();
        e.setClasse(classe);
        e.setValableDu(du);
        versions.saveAndFlush(e);
        int n = 0;
        if (a.get("creneaux") instanceof List<?> liste) {
          for (Object o : liste) {
            if (o instanceof Map<?, ?> m && recreer(e, m)) n++;
          }
        }
        yield "La version de l'emploi du temps de " + classe.getNom() + " " + aPartirDu(du) + " a été recréée avec " + n + " créneau(x).";
      }
      case "edt.creneau.creer" -> {
        Creneau c = creneaux.findById(texte(a.get("id"))).orElse(null);
        if (c == null) yield "Ce créneau avait déjà été retiré.";
        String libelle = c.getMatiere().getNom() + " " + quand(c.getJour(), c.getDebut(), c.getFin());
        creneaux.delete(c);
        yield "Le créneau " + libelle + " a été retiré.";
      }
      case "edt.creneau.modifier" -> {
        Creneau c = creneaux.findById(texte(a.get("id"))).orElseThrow(() -> ApiException.conflit("Ce créneau n'existe plus."));
        if (!(a.get("avant") instanceof Map<?, ?> avant) || !(a.get("apres") instanceof Map<?, ?> apres) || !memes(champs(c), apres)) {
          yield "Rien à défaire : le créneau a été modifié depuis.";
        }
        Matiere m = matieres.findById(texte(avant.get("matiereId"))).orElseThrow(() -> ApiException.conflit("L'ancienne matière n'existe plus."));
        c.setMatiere(m);
        c.setJour(((Number) avant.get("jour")).intValue());
        c.setDebut(LocalTime.parse(texte(avant.get("debut"))));
        c.setFin(LocalTime.parse(texte(avant.get("fin"))));
        yield "Le créneau a repris son ancienne valeur : " + m.getNom() + " " + quand(c.getJour(), c.getDebut(), c.getFin()) + ".";
      }
      case "edt.creneau.supprimer" -> {
        EmploiDuTemps e = versions.findById(texte(a.get("emploiId"))).orElseThrow(() -> ApiException.conflit("Cette version de l'emploi du temps n'existe plus."));
        if (!recreer(e, a)) throw ApiException.conflit("La matière de ce créneau n'existe plus.");
        yield "Le créneau a été remis dans l'emploi du temps de " + e.getClasse().getNom() + ".";
      }
      default -> throw ApiException.conflit("Ce type de décision ne peut pas être annulé automatiquement.");
    };
  }

  /** Le créneau a-t-il encore les valeurs enregistrées après la décision ? */
  private static boolean memes(Map<String, Object> actuel, Map<?, ?> enregistre) {
    for (String cle : List.of("matiereId", "jour", "debut", "fin")) {
      if (!Objects.equals(texte(actuel.get(cle)), texte(enregistre.get(cle)))) return false;
    }
    return true;
  }

  private boolean recreer(EmploiDuTemps e, Map<?, ?> m) {
    Matiere matiere = matieres.findById(texte(m.get("matiereId"))).orElse(null);
    if (matiere == null) return false;
    Creneau c = new Creneau();
    c.setEmploi(e);
    c.setMatiere(matiere);
    c.setJour(((Number) m.get("jour")).intValue());
    c.setDebut(LocalTime.parse(texte(m.get("debut"))));
    c.setFin(LocalTime.parse(texte(m.get("fin"))));
    creneaux.saveAndFlush(c);
    return true;
  }

  // ---------- Libellés ----------

  private static String texte(Object v) {
    return v == null ? null : v.toString();
  }

  static String quand(int jour, LocalTime debut, LocalTime fin) {
    return "le " + JOURS[jour] + " de " + HEURE.format(debut) + " à " + HEURE.format(fin);
  }

  private static String aPartirDu(LocalDate d) {
    return d == null ? "depuis la rentrée" : "à partir du " + DATE.format(d);
  }

  private static String lien(String classeId) {
    return "/emploi-du-temps?classe=" + classeId;
  }
}
