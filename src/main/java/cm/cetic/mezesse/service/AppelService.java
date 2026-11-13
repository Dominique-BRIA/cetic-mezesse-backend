package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.EnteteOfficiel;
import cm.cetic.mezesse.dto.AppelDto.Absences;
import cm.cetic.mezesse.dto.AppelDto.Case;
import cm.cetic.mezesse.dto.AppelDto.CaseCreneau;
import cm.cetic.mezesse.dto.AppelDto.Enregistrer;
import cm.cetic.mezesse.dto.AppelDto.EleveVue;
import cm.cetic.mezesse.dto.AppelDto.FicheVue;
import cm.cetic.mezesse.dto.AppelDto.Historique;
import cm.cetic.mezesse.dto.AppelDto.Personne;
import cm.cetic.mezesse.dto.AppelDto.SeanceAjout;
import cm.cetic.mezesse.dto.AppelDto.SeanceModif;
import cm.cetic.mezesse.dto.AppelDto.SeanceVue;
import cm.cetic.mezesse.dto.AppelDto.SequenceRef;
import cm.cetic.mezesse.dto.AppelDto.Vue;
import cm.cetic.mezesse.entity.Absence;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Creneau;
import cm.cetic.mezesse.entity.EmploiDuTemps;
import cm.cetic.mezesse.entity.FicheAppel;
import cm.cetic.mezesse.entity.HistoriqueAbsence;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Seance;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AbsenceRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.CreneauRepository;
import cm.cetic.mezesse.repository.EmploiDuTempsRepository;
import cm.cetic.mezesse.repository.FicheAppelRepository;
import cm.cetic.mezesse.repository.HistoriqueAbsenceRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.RenvoiRepository;
import cm.cetic.mezesse.repository.SeanceRepository;
import cm.cetic.mezesse.repository.SequenceRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import cm.cetic.mezesse.util.Horloge;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feuille d'appel du jour (SG) : les séances viennent de l'emploi du temps en vigueur ce jour-là, tous les
 * élèves sont présents par défaut, le SG ne coche que les absents ; sa saisie reste sur son appareil jusqu'à
 * l'enregistrement. Toute opération est historisée.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppelService {

  private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final int DUREE_MAX = 360;

  private final FicheAppelRepository fiches;
  private final SeanceRepository seances;
  private final AbsenceRepository absences;
  private final HistoriqueAbsenceRepository historique;
  private final ClasseRepository classes;
  private final InscriptionRepository inscriptions;
  private final RenvoiRepository renvois;
  private final SequenceRepository sequences;
  private final EmploiDuTempsRepository versions;
  private final CreneauRepository creneaux;
  private final MatiereRepository matieres;
  private final UtilisateurRepository utilisateurs;
  private final cm.cetic.mezesse.repository.PieceJustificativeRepository pieces;
  private final StockageService stockage;
  private final BilanAbsences bilan;
  private final cm.cetic.mezesse.repository.JustificationRepository justifications;

  // ---------- Règles ----------

  /** Séquence proposée pour une nouvelle feuille : la dernière dont la saisie est ouverte, sinon la dernière. */
  static Sequence sequenceProposee(List<Sequence> triees) {
    Sequence ouverte = null;
    for (Sequence s : triees) if (s.isSaisieOuverte()) ouverte = s;
    if (ouverte != null) return ouverte;
    return triees.isEmpty() ? null : triees.get(triees.size() - 1);
  }

  /** L'élève était-il renvoyé ce jour-là (renvoi commencé, pas encore réintégré) ? */
  static boolean renvoyeLe(List<Renvoi> renvoisDeLEleve, LocalDate jour) {
    for (Renvoi r : renvoisDeLEleve) {
      if (r.getDate().isAfter(jour)) continue;
      if (r.getReintegreLe() == null || r.getReintegreLe().atZone(EnteteOfficiel.CAMEROUN).toLocalDate().isAfter(jour)) return true;
    }
    return false;
  }

  // ---------- Lecture ----------

  public Vue vue(String classeId, LocalDate date) {
    Classe classe = classe(classeId);
    FicheAppel fiche = fiches.findByClasse_IdAndDate(classeId, date).orElse(null);
    return vue(classe, date, fiche);
  }

  private Vue vue(Classe classe, LocalDate date, FicheAppel fiche) {
    List<Sequence> seqs = sequences.findByAnnee_IdOrderByNumeroAsc(classe.getAnnee().getId());
    Sequence proposee = sequenceProposee(seqs);
    List<SequenceRef> refs = seqs.stream().map(s -> new SequenceRef(s.getId(), s.getNumero(), s.getTrimestre(), s.isSaisieOuverte())).toList();
    List<Inscription> eleves = inscriptions.deLaClasse(classe.getId());
    Map<String, List<Renvoi>> parInscription =
        eleves.isEmpty()
            ? Map.of()
            : renvois.desInscriptions(eleves.stream().map(Inscription::getId).toList()).stream().collect(Collectors.groupingBy(r -> r.getInscription().getId()));
    List<EleveVue> ev =
        eleves.stream()
            .map(i -> new EleveVue(
                i.getId(), i.getEleve().getId(), i.getEleve().getNom(), i.getEleve().getPrenoms(), i.getEleve().getMatricule(),
                renvoyeLe(parInscription.getOrDefault(i.getId(), List.of()), date)))
            .toList();
    boolean edt = !versions.deLaClasse(classe.getId()).isEmpty();
    int jour = date.getDayOfWeek().getValue();
    if (fiche == null) {
      List<SeanceVue> apercu = prevues(classe, date).stream().map(c -> apercu(c)).toList();
      return new Vue(classe.getId(), classe.getNom(), date, jour, null, refs, proposee == null ? null : proposee.getId(), apercu, ev, List.of(), edt, List.of());
    }
    List<SeanceVue> sv = seances.deLaFiche(fiche.getId()).stream().map(AppelService::vue).toList();
    List<Case> cases = absences.deLaFiche(fiche.getId()).stream().map(a -> new Case(a.getSeance().getId(), a.getInscription().getId())).toList();
    Utilisateur auteur = fiche.getCreePar();
    FicheVue fv = new FicheVue(fiche.getId(), fiche.getSequence().getId(), fiche.getCreeLe(), auteur == null ? null : auteur.nomComplet(), fiche.getModifieLe());
    List<Case> justifiees = justifications.casesJustifiees(fiche.getId()).stream().map(o -> new Case((String) o[0], (String) o[1])).toList();
    return new Vue(classe.getId(), classe.getNom(), date, jour, fv, refs, proposee == null ? null : proposee.getId(), sv, ev, cases, edt, justifiees);
  }

  /** Créneaux de l'emploi du temps en vigueur ce jour-là, dans l'ordre de la journée. */
  private List<Creneau> prevues(Classe classe, LocalDate date) {
    if (date.getDayOfWeek() == DayOfWeek.SUNDAY) return List.of();
    EmploiDuTemps e = EmploiDuTempsService.enVigueur(versions.deLaClasse(classe.getId()), date);
    if (e == null) return List.of();
    int jour = date.getDayOfWeek().getValue();
    return creneaux.desVersions(List.of(e.getId())).stream().filter(c -> c.getJour() == jour).sorted(Comparator.comparing(Creneau::getDebut)).toList();
  }

  private static Personne personne(Utilisateur u) {
    return u == null ? null : new Personne(u.getId(), u.getNom(), u.getPrenom());
  }

  private static SeanceVue apercu(Creneau c) {
    Matiere m = c.getMatiere();
    return new SeanceVue(
        null, c.getId(), m.getId(), m.getNom(), personne(m.getProfesseur()), null, HEURE.format(c.getDebut()), HEURE.format(c.getFin()), c.duree(),
        Seance.PREVUE, false, false, null);
  }

  private static SeanceVue vue(Seance s) {
    return new SeanceVue(
        s.getId(), s.getCreneau() == null ? null : s.getCreneau().getId(), s.getMatiere() == null ? null : s.getMatiere().getId(), s.getNomMatiere(),
        personne(s.getEnseignant()), personne(s.getRemplacant()), HEURE.format(s.getDebut()), HEURE.format(s.getFin()), s.duree(), s.getStatut(),
        s.isAjoutee(), s.isEnseignantAbsent(), s.getMotif());
  }

  /** Pour chaque classe de l'année : l'appel du jour est-il fait, et combien d'élèves étaient absents ? */
  public List<cm.cetic.mezesse.dto.AppelDto.EtatClasse> etatDuJour(String anneeId, LocalDate date) {
    List<cm.cetic.mezesse.dto.AppelDto.EtatClasse> etats = new ArrayList<>();
    for (Classe c : classes.findByAnnee_IdOrderByNiveauAscNomAsc(anneeId)) {
      FicheAppel f = fiches.findByClasse_IdAndDate(c.getId(), date).orElse(null);
      int prevues = f == null ? prevues(c, date).size() : (int) seances.deLaFiche(f.getId()).stream().filter(x -> !x.estAnnulee()).count();
      int absents = f == null ? 0 : (int) absences.deLaFiche(f.getId()).stream().map(a -> a.getInscription().getId()).distinct().count();
      boolean edt = !versions.deLaClasse(c.getId()).isEmpty();
      etats.add(new cm.cetic.mezesse.dto.AppelDto.EtatClasse(c.getId(), c.getNom(), c.getNiveau(), edt, prevues, f != null, absents));
    }
    return etats;
  }

  public List<Historique> historique(String ficheId) {
    fiche(ficheId);
    return historique.deLaFiche(ficheId).stream()
        .map(h -> new Historique(h.getId(), h.getAction(), h.getLibelle(), h.getAvant(), h.getApres(), h.getPar() == null ? null : h.getPar().nomComplet(), h.getCreeLe()))
        .toList();
  }

  // ---------- Feuille ----------

  /**
   * Premier enregistrement de l'appel du jour (la saisie s'est faite sur l'appareil du SG) : la feuille, les
   * séances de l'emploi du temps et les absences cochées, en une seule transaction.
   */
  @Transactional
  public Vue enregistrerAppel(UtilisateurConnecte moi, Enregistrer d) {
    Classe classe = classe(d.classeId());
    FicheAppel existante = fiches.findByClasse_IdAndDate(classe.getId(), d.date()).orElse(null);
    if (existante != null) {
      throw ApiException.conflit(
          "L'appel de ce jour a déjà été enregistré" + (existante.getCreePar() == null ? "" : " par " + existante.getCreePar().nomComplet())
              + ". Rechargez : vos cases cochées restent sur cet appareil et pourront être enregistrées comme correction.");
    }
    if (d.date().isAfter(EmploiDuTempsService.aujourdhui())) throw ApiException.invalide("On ne peut pas faire l'appel d'un jour qui n'est pas encore arrivé.");
    if (d.date().getDayOfWeek() == DayOfWeek.SUNDAY) throw ApiException.invalide("Il n'y a pas cours le dimanche.");
    Sequence seq = d.sequenceId() == null ? sequenceProposee(sequences.findByAnnee_IdOrderByNumeroAsc(classe.getAnnee().getId())) : sequence(classe, d.sequenceId());
    if (seq == null) throw ApiException.conflit("L'année scolaire n'a pas de séquence.");
    FicheAppel f = new FicheAppel();
    f.setClasse(classe);
    f.setDate(d.date());
    f.setSequence(seq);
    f.setCreePar(utilisateurs.getReferenceById(moi.id()));
    fiches.saveAndFlush(f);
    Map<String, String> seanceDuCreneau = new HashMap<>();
    List<Creneau> prevues = prevues(classe, d.date());
    for (Creneau c : prevues) {
      Seance s = new Seance();
      s.setFiche(f);
      s.setCreneau(c);
      s.setMatiere(c.getMatiere());
      s.setNomMatiere(c.getMatiere().getNom());
      s.setEnseignant(c.getMatiere().getProfesseur());
      s.setDebut(c.getDebut());
      s.setFin(c.getFin());
      seances.save(s);
      seanceDuCreneau.put(c.getId(), s.getId());
    }
    seances.flush();
    List<Case> cases = new ArrayList<>();
    for (CaseCreneau c : d.absences()) {
      String seanceId = seanceDuCreneau.get(c.creneauId());
      if (seanceId == null) throw ApiException.conflit("L'emploi du temps de ce jour a changé depuis le début de la saisie : rechargez la page.");
      cases.add(new Case(seanceId, c.inscriptionId()));
    }
    Ecart e = appliquer(moi, f, cases);
    verifierPalier(e);
    tracer(
        moi, f, "fiche.enregistrer",
        "Appel enregistré : " + prevues.size() + " séance(s), " + e.ajoutees().size() + " absence(s)" + (e.ajoutees().isEmpty() ? "" : noms(e.ajoutees()))
            + ", séquence " + seq.getNumero(),
        null, e.ajoutees().isEmpty() ? null : Donnees.objet("absences", e.ajoutees()));
    return vue(classe, d.date(), f);
  }

  /** Correction des absences d'une feuille enregistrée (liste complète : ce qui n'y est plus redevient présent). */
  @Transactional
  public Vue corriger(UtilisateurConnecte moi, String ficheId, Absences d) {
    FicheAppel f = fiche(ficheId);
    if (d.sequenceId() != null) rattacher(moi, f, d.sequenceId());
    Ecart e = appliquer(moi, f, d.absences());
    verifierPalier(e);
    if (!e.ajoutees().isEmpty() || !e.retirees().isEmpty()) {
      tracer(
          moi, f, "absences.corriger", "Correction : " + resume(e.ajoutees(), e.retirees()),
          e.retirees().isEmpty() ? null : Donnees.objet("absences", e.retirees()), e.ajoutees().isEmpty() ? null : Donnees.objet("absences", e.ajoutees()));
    }
    return vue(f.getClasse(), f.getDate(), f);
  }

  /** Nouvelles absences : le Proviseur est prévenu des élèves qui atteignent le palier d'exclusion. */
  private void verifierPalier(Ecart e) {
    bilan.verifierPalier(e.ajoutees().stream().map(m -> (String) m.get("inscriptionId")).distinct().toList());
  }

  private record Ecart(List<Map<String, Object>> ajoutees, List<Map<String, Object>> retirees) {}

  /** Met les absences de la feuille dans l'état demandé, après vérification de chaque case. */
  private Ecart appliquer(UtilisateurConnecte moi, FicheAppel f, List<Case> cases) {
    String ficheId = f.getId();
    Map<String, Seance> parSeance = seances.deLaFiche(ficheId).stream().collect(Collectors.toMap(Seance::getId, s -> s));
    Map<String, Inscription> parInscription = inscriptions.deLaClasse(f.getClasse().getId()).stream().collect(Collectors.toMap(Inscription::getId, i -> i));
    Map<String, List<Renvoi>> renvoisPar =
        parInscription.isEmpty()
            ? Map.of()
            : renvois.desInscriptions(parInscription.keySet()).stream().collect(Collectors.groupingBy(r -> r.getInscription().getId()));
    Set<String> voulues = new HashSet<>();
    for (Case c : cases) {
      Seance s = parSeance.get(c.seanceId());
      Inscription i = parInscription.get(c.inscriptionId());
      if (s == null || i == null) throw ApiException.invalide("Cette case n'appartient pas à la feuille d'appel.");
      if (s.estAnnulee()) throw ApiException.invalide("La séance de " + s.getNomMatiere() + " est annulée : personne n'y est absent.");
      if (renvoyeLe(renvoisPar.getOrDefault(i.getId(), List.of()), f.getDate())) {
        throw ApiException.invalide(i.getEleve().nomComplet() + " était renvoyé(e) ce jour-là : il ne peut pas être compté absent.");
      }
      voulues.add(c.seanceId() + "|" + c.inscriptionId());
    }
    Set<String> deja = new HashSet<>();
    List<Map<String, Object>> retirees = new ArrayList<>();
    for (Absence a : absences.deLaFiche(ficheId)) {
      String cle = a.getSeance().getId() + "|" + a.getInscription().getId();
      if (voulues.contains(cle)) {
        deja.add(cle);
        continue;
      }
      retirees.add(trace(a.getSeance(), a.getInscription()));
      oublierPieces(a);
      absences.delete(a);
    }
    List<Map<String, Object>> ajoutees = new ArrayList<>();
    for (String cle : voulues) {
      if (deja.contains(cle)) continue;
      String[] p = cle.split("\\|");
      Absence a = new Absence();
      a.setSeance(parSeance.get(p[0]));
      a.setInscription(parInscription.get(p[1]));
      a.setPar(utilisateurs.getReferenceById(moi.id()));
      absences.save(a);
      ajoutees.add(trace(a.getSeance(), a.getInscription()));
    }
    absences.flush();
    return new Ecart(ajoutees, retirees);
  }

  @Transactional
  public Vue changerSequence(UtilisateurConnecte moi, String ficheId, String sequenceId) {
    FicheAppel f = fiche(ficheId);
    rattacher(moi, f, sequenceId);
    return vue(f.getClasse(), f.getDate(), f);
  }

  /** Rattache la feuille à une autre séquence (historisé) ; rien si c'est déjà la sienne. */
  private void rattacher(UtilisateurConnecte moi, FicheAppel f, String sequenceId) {
    Sequence avant = f.getSequence();
    Sequence apres = sequence(f.getClasse(), sequenceId);
    if (avant.getId().equals(apres.getId())) return;
    f.setSequence(apres);
    fiches.saveAndFlush(f);
    tracer(
        moi, f, "fiche.sequence", "Feuille rattachée à la séquence " + apres.getNumero() + " (au lieu de la " + avant.getNumero() + ")",
        Donnees.objet("sequence", avant.getNumero()), Donnees.objet("sequence", apres.getNumero()));
  }

  // ---------- Séances ----------

  @Transactional
  public Vue modifierSeance(UtilisateurConnecte moi, String seanceId, SeanceModif d) {
    Seance s = seances.findById(seanceId).orElseThrow(() -> ApiException.introuvable("Séance"));
    FicheAppel f = s.getFiche();
    LocalTime debut = LocalTime.parse(d.debut());
    LocalTime fin = LocalTime.parse(d.fin());
    verifierHoraire(debut, fin);
    Utilisateur remplacant = d.remplacantId() == null ? null : professeur(d.remplacantId());
    if (remplacant != null && s.getEnseignant() != null && remplacant.getId().equals(s.getEnseignant().getId())) remplacant = null;
    Map<String, Object> avant = champs(s);
    s.setDebut(debut);
    s.setFin(fin);
    s.setRemplacant(remplacant);
    s.setEnseignantAbsent(d.enseignantAbsent() && remplacant == null && !d.annulee());
    s.setStatut(d.annulee() ? Seance.ANNULEE : Seance.PREVUE);
    s.setMotif(d.motif() == null || d.motif().isBlank() ? null : d.motif().trim());
    seances.saveAndFlush(s);
    Map<String, Object> apres = champs(s);
    List<Map<String, Object>> effacees = new ArrayList<>();
    if (s.estAnnulee()) {
      for (Absence a : absences.findBySeance_Id(s.getId())) {
        effacees.add(trace(s, a.getInscription()));
        oublierPieces(a);
        absences.delete(a);
      }
      absences.flush();
      if (!effacees.isEmpty()) avant.put("absences", effacees);
    }
    if (!avant.equals(apres) || !effacees.isEmpty()) {
      tracer(moi, f, "seance.modifier", libelleModification(s, avant, apres, effacees.size()), avant, apres);
    }
    return vue(f.getClasse(), f.getDate(), f);
  }

  @Transactional
  public Vue ajouterSeance(UtilisateurConnecte moi, String ficheId, SeanceAjout d) {
    FicheAppel f = fiche(ficheId);
    Matiere m = matieres.findById(d.matiereId()).orElseThrow(() -> ApiException.introuvable("Matière"));
    if (!m.getClasseId().equals(f.getClasse().getId())) throw ApiException.invalide("Cette matière n'est pas enseignée dans " + f.getClasse().getNom() + ".");
    LocalTime debut = LocalTime.parse(d.debut());
    LocalTime fin = LocalTime.parse(d.fin());
    verifierHoraire(debut, fin);
    Seance s = new Seance();
    s.setFiche(f);
    s.setMatiere(m);
    s.setNomMatiere(m.getNom());
    s.setEnseignant(m.getProfesseur());
    s.setDebut(debut);
    s.setFin(fin);
    s.setAjoutee(true);
    s.setMotif(d.motif() == null || d.motif().isBlank() ? null : d.motif().trim());
    seances.saveAndFlush(s);
    tracer(moi, f, "seance.ajouter", "Séance ajoutée : " + libelle(s) + (s.getMotif() == null ? "" : " (" + s.getMotif() + ")"), null, champs(s));
    return vue(f.getClasse(), f.getDate(), f);
  }

  /** Seule une séance ajoutée peut être retirée ; une séance de l'emploi du temps s'annule. */
  @Transactional
  public Vue retirerSeance(UtilisateurConnecte moi, String seanceId) {
    Seance s = seances.findById(seanceId).orElseThrow(() -> ApiException.introuvable("Séance"));
    FicheAppel f = s.getFiche();
    if (!s.isAjoutee()) throw ApiException.conflit("Cette séance vient de l'emploi du temps : annulez-la plutôt que de la retirer.");
    List<Map<String, Object>> effacees = new ArrayList<>();
    for (Absence a : absences.findBySeance_Id(s.getId())) effacees.add(trace(s, a.getInscription()));
    Map<String, Object> avant = champs(s);
    if (!effacees.isEmpty()) avant.put("absences", effacees);
    String lib = libelle(s);
    absences.findBySeance_Id(s.getId()).forEach(this::oublierPieces);
    seances.delete(s);
    seances.flush();
    tracer(moi, f, "seance.retirer", "Séance ajoutée retirée : " + lib + (effacees.isEmpty() ? "" : " (" + effacees.size() + " absence(s) effacée(s))"), avant, null);
    return vue(f.getClasse(), f.getDate(), f);
  }

  // ---------- Outils ----------

  /**
   * Une absence supprimée emporte sa justification (cascade) : les fichiers qui ne servent plus à rien sont
   * retirés du stockage une fois la transaction validée.
   */
  private void oublierPieces(Absence a) {
    List<String> cles = pieces.clesDeLAbsence(a.getId());
    if (cles.isEmpty()) return;
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
        new org.springframework.transaction.support.TransactionSynchronization() {
          @Override
          public void afterCommit() {
            for (String cle : cles) if (pieces.countByCle(cle) == 0) stockage.supprimer(cle);
          }
        });
  }

  private Classe classe(String id) {
    return classes.findById(id).orElseThrow(() -> ApiException.introuvable("Classe"));
  }

  private FicheAppel fiche(String id) {
    return fiches.findById(id).orElseThrow(() -> ApiException.introuvable("Feuille d'appel"));
  }

  private Sequence sequence(Classe classe, String id) {
    Sequence s = sequences.findById(id).orElseThrow(() -> ApiException.introuvable("Séquence"));
    if (!s.getAnnee().getId().equals(classe.getAnnee().getId())) throw ApiException.invalide("Cette séquence n'est pas de l'année de la classe.");
    return s;
  }

  private Utilisateur professeur(String id) {
    Utilisateur u = utilisateurs.findById(id).orElseThrow(() -> ApiException.introuvable("Enseignant"));
    if (u.getRole() != Role.PROFESSEUR) throw ApiException.invalide("Le remplaçant doit être un enseignant.");
    return u;
  }

  private static void verifierHoraire(LocalTime debut, LocalTime fin) {
    if (!fin.isAfter(debut)) throw ApiException.invalide("L'heure de fin doit être après l'heure de début.");
    if (Duration.between(debut, fin).toMinutes() > DUREE_MAX) throw ApiException.invalide("Une séance ne peut pas durer plus de 6 heures.");
  }

  static String libelle(Seance s) {
    return s.getNomMatiere() + " de " + HEURE.format(s.getDebut()) + " à " + HEURE.format(s.getFin());
  }

  private static Map<String, Object> champs(Seance s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("matiere", s.getNomMatiere());
    m.put("debut", HEURE.format(s.getDebut()));
    m.put("fin", HEURE.format(s.getFin()));
    m.put("statut", s.getStatut());
    m.put("remplacant", s.getRemplacant() == null ? null : s.getRemplacant().nomComplet());
    m.put("enseignantAbsent", s.isEnseignantAbsent());
    m.put("motif", s.getMotif());
    return m;
  }

  private static Map<String, Object> trace(Seance s, Inscription i) {
    return Donnees.objet("seanceId", s.getId(), "seance", libelle(s), "inscriptionId", i.getId(), "eleve", i.getEleve().nomComplet());
  }

  private static String libelleModification(Seance s, Map<String, Object> avant, Map<String, Object> apres, int effacees) {
    List<String> quoi = new ArrayList<>();
    if (!Objects.equals(avant.get("statut"), apres.get("statut"))) quoi.add(s.estAnnulee() ? "annulée" : "rétablie");
    if (!Objects.equals(avant.get("debut"), apres.get("debut")) || !Objects.equals(avant.get("fin"), apres.get("fin"))) {
      quoi.add("horaire " + avant.get("debut") + "–" + avant.get("fin") + " → " + apres.get("debut") + "–" + apres.get("fin"));
    }
    if (!Objects.equals(avant.get("remplacant"), apres.get("remplacant"))) quoi.add(apres.get("remplacant") == null ? "plus de remplaçant" : "remplacée par " + apres.get("remplacant"));
    if (!Objects.equals(avant.get("enseignantAbsent"), apres.get("enseignantAbsent"))) quoi.add(s.isEnseignantAbsent() ? "enseignant absent (non assurée)" : "enseignant présent");
    if (!Objects.equals(avant.get("motif"), apres.get("motif")) && s.getMotif() != null) quoi.add("motif : " + s.getMotif());
    if (effacees > 0) quoi.add(effacees + " absence(s) effacée(s)");
    return "Séance de " + s.getNomMatiere() + " : " + (quoi.isEmpty() ? "modifiée" : String.join(", ", quoi));
  }

  private static String resume(List<Map<String, Object>> ajoutees, List<Map<String, Object>> retirees) {
    List<String> morceaux = new ArrayList<>();
    if (!ajoutees.isEmpty()) morceaux.add(ajoutees.size() + " absence(s) cochée(s)" + noms(ajoutees));
    if (!retirees.isEmpty()) morceaux.add(retirees.size() + " retirée(s), élève(s) présent(s)" + noms(retirees));
    return String.join(" ; ", morceaux);
  }

  /** « (ESSONO Landry, NKOLO Paul…) », au plus quatre noms. */
  private static String noms(List<Map<String, Object>> liste) {
    List<String> n = liste.stream().map(m -> String.valueOf(m.get("eleve"))).distinct().toList();
    return " (" + String.join(", ", n.subList(0, Math.min(4, n.size()))) + (n.size() > 4 ? "…" : "") + ")";
  }

  private void tracer(UtilisateurConnecte moi, FicheAppel f, String action, String libelle, Map<String, Object> avant, Map<String, Object> apres) {
    // Après le premier enregistrement, toute opération est une modification de la feuille.
    if (!"fiche.enregistrer".equals(action)) f.setModifieLe(Horloge.maintenant());
    HistoriqueAbsence h = new HistoriqueAbsence();
    h.setFicheId(f.getId());
    h.setClasseId(f.getClasse().getId());
    h.setDate(f.getDate());
    h.setAction(action);
    h.setLibelle(cm.cetic.mezesse.util.Textes.couper(libelle, 1000));
    h.setAvant(avant);
    h.setApres(apres);
    h.setPar(utilisateurs.getReferenceById(moi.id()));
    historique.save(h);
  }
}
