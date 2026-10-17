package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.AnneeDto.SequenceVue;
import cm.cetic.mezesse.dto.NoteDto.Avancement;
import cm.cetic.mezesse.dto.NoteDto.ClasseNiveau;
import cm.cetic.mezesse.dto.NoteDto.ClasseRef;
import cm.cetic.mezesse.dto.NoteDto.ClasseReleve;
import cm.cetic.mezesse.dto.NoteDto.EleveFeuille;
import cm.cetic.mezesse.dto.NoteDto.EleveRef;
import cm.cetic.mezesse.dto.NoteDto.EleveReleve;
import cm.cetic.mezesse.dto.NoteDto.Feuille;
import cm.cetic.mezesse.dto.NoteDto.GroupeNom;
import cm.cetic.mezesse.dto.NoteDto.Manquantes;
import cm.cetic.mezesse.dto.NoteDto.Manquants;
import cm.cetic.mezesse.dto.NoteDto.MatiereAvancement;
import cm.cetic.mezesse.dto.NoteDto.MatiereFeuille;
import cm.cetic.mezesse.dto.NoteDto.MatiereManquante;
import cm.cetic.mezesse.dto.NoteDto.MatiereReleve;
import cm.cetic.mezesse.dto.NoteDto.NoteSaisie;
import cm.cetic.mezesse.dto.NoteDto.Personne;
import cm.cetic.mezesse.dto.NoteDto.ReleveClasse;
import cm.cetic.mezesse.dto.NoteDto.SaisieFeuille;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.CompetenceVisee;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Note;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.CompetenceViseeRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.NoteRepository;
import cm.cetic.mezesse.repository.SequenceRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.ResultatsService.DonneesClasse;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.LigneMatiere;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saisie des notes par les professeurs, suivi de la saisie et relevés de classe. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService {

  private final MatiereRepository matieres;
  private final SequenceRepository sequences;
  private final InscriptionRepository inscriptions;
  private final NoteRepository notes;
  private final CompetenceViseeRepository competences;
  private final UtilisateurRepository utilisateurs;
  private final ResultatsService resultats;

  /** Un professeur n'accède qu'à ses matières ; la direction à toutes. */
  public Matiere matiereAccessible(UtilisateurConnecte moi, String matiereId) {
    Matiere m = matieres.findById(matiereId).orElseThrow(() -> ApiException.introuvable("Matière"));
    if (moi.estProfesseur() && !moi.id().equals(m.getProfesseurId())) throw ApiException.interdit("Cette matière ne vous est pas attribuée.");
    return m;
  }

  private Sequence sequenceDe(Matiere m, String sequenceId) {
    Sequence s = sequences.findById(sequenceId).orElse(null);
    if (s == null || !s.getAnneeId().equals(m.getClasse().getAnneeId())) throw ApiException.introuvable("Séquence");
    return s;
  }

  public Feuille feuille(UtilisateurConnecte moi, String matiereId, String sequenceId) {
    Matiere m = matiereAccessible(moi, matiereId);
    Sequence s = sequenceDe(m, sequenceId);
    Map<String, Note> parInscription =
        notes.findByMatiere_IdAndSequence_Id(matiereId, sequenceId).stream().collect(Collectors.toMap(n -> n.getInscription().getId(), Function.identity()));
    String competence = competences.findById(new CompetenceVisee.Cle(matiereId, sequenceId)).map(CompetenceVisee::getTexte).orElse("");
    Classe c = m.getClasse();
    List<EleveFeuille> eleves =
        inscriptions.activesDeLaClasse(c.getId()).stream()
            .map(
                i -> {
                  Eleve e = i.getEleve();
                  Note n = parInscription.get(i.getId());
                  return new EleveFeuille(
                      i.getId(), e.getMatricule(), e.getNom(), e.getPrenoms(), e.getSexe(), n == null ? null : n.getValeur().doubleValue(),
                      n == null ? null : n.getMajLe());
                })
            .toList();
    return new Feuille(
        new MatiereFeuille(m.getId(), m.getNom(), m.getCoefficient(), new ClasseRef(c.getId(), c.getNom(), c.getAnneeId()), new GroupeNom(m.getGroupe().getNom())),
        SequenceVue.de(s), competence, eleves);
  }

  /**
   * Enregistrement d'une feuille (ou d'une partie) par le professeur de la matière. Sert aussi à la
   * synchronisation hors connexion du mobile.
   */
  @Transactional
  public String enregistrer(UtilisateurConnecte moi, SaisieFeuille d) {
    Matiere m = matiereAccessible(moi, d.matiereId());
    Sequence s = sequenceDe(m, d.sequenceId());
    if (!s.isSaisieOuverte()) {
      throw ApiException.conflit("La saisie de la séquence " + s.getNumero() + " est fermée. Demandez au Surveillant Général de la rouvrir.");
    }
    Set<String> demandees = new HashSet<>(d.notes().stream().map(NoteSaisie::inscriptionId).toList());
    Set<String> valides = demandees.isEmpty() ? Set.of() : new HashSet<>(inscriptions.activesParmi(demandees, m.getClasseId()));
    long inconnus = d.notes().stream().filter(n -> !valides.contains(n.inscriptionId())).count();
    if (inconnus > 0) throw ApiException.invalide(inconnus + " élève(s) ne font plus partie de la classe ; rechargez la feuille.");

    Utilisateur auteur = utilisateurs.getReferenceById(moi.id());
    for (NoteSaisie n : d.notes()) {
      if (n.valeur() == null) {
        notes.effacer(n.inscriptionId(), m.getId(), s.getId());
        continue;
      }
      Note note = notes.findByInscription_IdAndMatiere_IdAndSequence_Id(n.inscriptionId(), m.getId(), s.getId()).orElse(null);
      if (note == null) {
        note = new Note();
        note.setInscription(inscriptions.getReferenceById(n.inscriptionId()));
        note.setMatiere(m);
        note.setSequence(s);
      }
      note.setValeur(BigDecimal.valueOf(n.valeur()));
      note.setSaisiePar(auteur);
      notes.save(note);
    }
    if (d.competence() != null) {
      CompetenceVisee.Cle cle = new CompetenceVisee.Cle(m.getId(), s.getId());
      if (d.competence().isEmpty()) competences.findById(cle).ifPresent(competences::delete);
      else competences.save(new CompetenceVisee(m.getId(), s.getId(), d.competence()));
    }
    return d.notes().size() + " note(s) enregistrée(s).";
  }

  private static ClasseNiveau classeNiveau(Classe c) {
    return new ClasseNiveau(c.getId(), c.getNom(), c.getNiveau());
  }

  private static Personne personne(Utilisateur u) {
    return u == null ? null : new Personne(u.getNom(), u.getPrenom());
  }

  /** Avancement de la saisie : pour chaque matière visible, nombre de notes par séquence. */
  public Avancement avancement(UtilisateurConnecte moi, String anneeId) {
    List<Matiere> liste = matieres.pourSuivi(anneeId, null, null, moi.estProfesseur() ? moi.id() : null);
    List<Sequence> seqs = sequences.findByAnnee_IdOrderByNumeroAsc(anneeId);
    Map<String, Long> effectifs = Compte.enMap(inscriptions.activesParClasseDeLAnnee(anneeId));
    Map<String, Long> comptes = new HashMap<>();
    if (!liste.isEmpty()) {
      for (Object[] ligne : notes.compterParMatiereEtSequence(liste.stream().map(Matiere::getId).toList())) {
        comptes.put(ligne[0] + "|" + ligne[1], (Long) ligne[2]);
      }
    }
    List<MatiereAvancement> lignes =
        liste.stream()
            .map(
                m -> {
                  Map<String, Long> saisies = new LinkedHashMap<>();
                  for (Sequence q : seqs) saisies.put(q.getId(), comptes.getOrDefault(m.getId() + "|" + q.getId(), 0L));
                  return new MatiereAvancement(
                      m.getId(), m.getNom(), m.getCoefficient(), classeNiveau(m.getClasse()), personne(m.getProfesseur()),
                      effectifs.getOrDefault(m.getClasseId(), 0L), saisies);
                })
            .toList();
    return new Avancement(seqs.stream().map(SequenceVue::de).toList(), lignes);
  }

  /** Élèves sans note dans une matière et une séquence (ou dans toutes les matières visibles). */
  public Manquantes manquantes(UtilisateurConnecte moi, String sequenceId, String matiereId, String classeId) {
    Sequence s = sequences.findById(sequenceId).orElseThrow(() -> ApiException.introuvable("Séquence"));
    List<Manquants> resultat = new ArrayList<>();
    for (Matiere m : matieres.pourSuivi(s.getAnneeId(), matiereId, classeId, moi.estProfesseur() ? moi.id() : null)) {
      List<EleveRef> manquants =
          inscriptions.sansNote(m.getClasseId(), m.getId(), sequenceId).stream()
              .map(i -> new EleveRef(i.getId(), i.getEleve().getMatricule(), i.getEleve().getNom(), i.getEleve().getPrenoms(), i.getEleve().getSexe()))
              .toList();
      resultat.add(
          new Manquants(
              new MatiereManquante(m.getId(), m.getNom(), classeNiveau(m.getClasse()), personne(m.getProfesseur())),
              inscriptions.countByClasse_IdAndStatut(m.getClasseId(), StatutInscription.ACTIF),
              manquants));
    }
    return new Manquantes(SequenceVue.de(s), resultat);
  }

  /** Relevé de toute une classe pour une période (consultation par la direction). */
  public ReleveClasse releve(String classeId, Periode p) {
    DonneesClasse d = resultats.resultatsClasse(classeId, p);
    var r = d.resultats();
    return new ReleveClasse(
        new ClasseReleve(d.classe().getId(), d.classe().getNom(), d.classe().getAnnee().getLibelle()),
        p,
        r.matieres().stream()
            .map(
                s ->
                    new MatiereReleve(
                        s.matiere().id(), s.matiere().nom(), s.matiere().coefficient(), s.matiere().groupe().nom(), s.matiere().professeur(),
                        s.evaluee(), s.moyenne()))
            .toList(),
        r.eleves().stream()
            .map(
                e -> {
                  Inscription i = d.inscription(e.inscriptionId());
                  Eleve el = i.getEleve();
                  return new EleveReleve(
                      i.getId(), el.getId(), el.getMatricule(), el.getNom(), el.getPrenoms(), el.getSexe(),
                      e.matieres().stream().map(LigneMatiere::note).toList(), e.moyenne(), e.rang());
                })
            .toList(),
        r.profil());
  }
}
