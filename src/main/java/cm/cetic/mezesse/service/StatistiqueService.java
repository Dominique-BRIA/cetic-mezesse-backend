package cm.cetic.mezesse.service;

import static cm.cetic.mezesse.util.Nombres.arrondi;

import cm.cetic.mezesse.dto.StatistiqueDto;
import cm.cetic.mezesse.dto.StatistiqueDto.ClasseEtablissement;
import cm.cetic.mezesse.dto.StatistiqueDto.ClasseRef;
import cm.cetic.mezesse.dto.StatistiqueDto.EleveEtablissement;
import cm.cetic.mezesse.dto.StatistiqueDto.EleveMoyenne;
import cm.cetic.mezesse.dto.StatistiqueDto.EleveNote;
import cm.cetic.mezesse.dto.StatistiqueDto.Evolution;
import cm.cetic.mezesse.dto.StatistiqueDto.EvolutionMatiere;
import cm.cetic.mezesse.dto.StatistiqueDto.Grade;
import cm.cetic.mezesse.dto.StatistiqueDto.MatiereClasse;
import cm.cetic.mezesse.dto.StatistiqueDto.MatiereComparee;
import cm.cetic.mezesse.dto.StatistiqueDto.MatiereRef;
import cm.cetic.mezesse.dto.StatistiqueDto.ParSexe;
import cm.cetic.mezesse.dto.StatistiqueDto.Ref;
import cm.cetic.mezesse.dto.StatistiqueDto.Resume;
import cm.cetic.mezesse.dto.StatistiqueDto.ResumeMatiere;
import cm.cetic.mezesse.dto.StatistiqueDto.Totaux;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.Compte;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.ResultatsService.DonneesClasse;
import cm.cetic.mezesse.service.calcul.CalculResultats;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Baremes;
import cm.cetic.mezesse.service.calcul.Resultats.ProfilClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.service.calcul.Resultats.Seuil;
import cm.cetic.mezesse.service.calcul.Resultats.StatMatiere;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statistiques d'une matière, d'une classe et de tout l'établissement pour une période. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatistiqueService {

  private static final Collator ORDRE_ALPHABETIQUE = Collator.getInstance(Locale.FRENCH);

  private final ResultatsService resultats;
  private final NoteService notes;
  private final ClasseRepository classes;
  private final InscriptionRepository inscriptions;
  private final UtilisateurRepository utilisateurs;

  private record Valeur(Sexe sexe, double v) {}

  static Resume resume(List<Double> v) {
    ProfilClasse p = CalculResultats.profilDe(v);
    return new Resume(v.size(), p.moyenneGenerale(), p.min(), p.max(), p.tauxReussite(), p.ecartType(), p.nombreMoyennes());
  }

  private static List<ParSexe> parSexe(List<Valeur> valeurs) {
    return Stream.of(Sexe.F, Sexe.M)
        .map(
            sexe -> {
              Resume r = resume(valeurs.stream().filter(x -> x.sexe() == sexe).map(Valeur::v).toList());
              return new ParSexe(sexe, r.effectif(), r.moyenne(), r.min(), r.max(), r.tauxReussite(), r.ecartType(), r.reussis());
            })
        .toList();
  }

  /** Statistiques d'une matière (professeur de la matière ou direction). */
  public StatistiqueDto.Matiere matiere(UtilisateurConnecte moi, String matiereId, Periode p) {
    Matiere m = notes.matiereAccessible(moi, matiereId);
    Baremes b = resultats.baremes();
    DonneesClasse d = resultats.resultatsClasse(m.getClasseId(), p, b);
    int idx = indexMatiere(d, matiereId);
    StatMatiere stat = d.resultats().matieres().get(idx);
    List<EleveNote> notesEleves = new ArrayList<>();
    for (ResultatEleve r : d.resultats().eleves()) {
      var l = r.matieres().get(idx);
      if (l.note() == null) continue;
      Eleve e = d.inscription(r.inscriptionId()).getEleve();
      notesEleves.add(new EleveNote(r.inscriptionId(), e.getId(), e.getMatricule(), e.getNom(), e.getPrenoms(), e.getSexe(), l.note(), l.rang(), l.grade()));
    }
    notesEleves.sort(Comparator.comparingDouble(EleveNote::note).reversed());

    // Évolution sur les six séquences
    List<EvolutionMatiere> evolution = new ArrayList<>();
    for (Periode s : Periode.SEQUENCES) {
      DonneesClasse ds = s == p ? d : resultats.resultatsClasse(m.getClasseId(), s, b);
      StatMatiere st = ds.resultats().matieres().get(indexMatiere(ds, matiereId));
      evolution.add(new EvolutionMatiere(s, st.moyenne(), st.tauxReussite(), st.evaluee()));
    }
    List<Grade> grades =
        b.grades().stream()
            .sorted(Comparator.comparingDouble(Seuil::min).reversed())
            .map(g -> new Grade(g.libelle(), (int) notesEleves.stream().filter(e -> g.libelle().equals(e.grade())).count()))
            .toList();
    List<Double> valeurs = notesEleves.stream().map(EleveNote::note).toList();
    Resume r = resume(valeurs);
    Classe c = m.getClasse();
    return new StatistiqueDto.Matiere(
        new MatiereRef(m.getId(), m.getNom(), m.getCoefficient(), new ClasseRef(c.getId(), c.getNom(), c.getAnneeId())),
        p,
        new ResumeMatiere(r.effectif(), r.moyenne(), r.min(), r.max(), r.tauxReussite(), r.ecartType(), r.reussis(), stat.evaluee()),
        CalculResultats.repartition(valeurs),
        grades,
        parSexe(notesEleves.stream().map(e -> new Valeur(e.sexe(), e.note())).toList()),
        evolution,
        notesEleves);
  }

  private static int indexMatiere(DonneesClasse d, String matiereId) {
    var liste = d.resultats().matieres();
    for (int i = 0; i < liste.size(); i++) if (liste.get(i).matiere().id().equals(matiereId)) return i;
    throw new IllegalStateException("Matière absente des résultats");
  }

  /** Statistiques d'une classe. */
  public StatistiqueDto.Classe classe(String classeId, Periode p) {
    Baremes b = resultats.baremes();
    DonneesClasse d = resultats.resultatsClasse(classeId, p, b);
    List<EleveMoyenne> moyennes = new ArrayList<>();
    for (ResultatEleve r : d.resultats().eleves()) {
      if (r.moyenne() == null) continue;
      Eleve e = d.inscription(r.inscriptionId()).getEleve();
      moyennes.add(new EleveMoyenne(r.inscriptionId(), e.getId(), e.getMatricule(), e.getNom(), e.getPrenoms(), e.getSexe(), r.moyenne(), r.rang()));
    }
    moyennes.sort(Comparator.comparingDouble(EleveMoyenne::moyenne).reversed());

    List<Evolution> evolution = new ArrayList<>();
    for (Periode s : List.of(Periode.S1, Periode.S2, Periode.S3, Periode.S4, Periode.S5, Periode.S6, Periode.T1, Periode.T2, Periode.T3)) {
      DonneesClasse ds = s == p ? d : resultats.resultatsClasse(classeId, s, b);
      evolution.add(new Evolution(s, ds.resultats().profil().moyenneGenerale(), ds.resultats().profil().tauxReussite()));
    }
    List<Double> valeurs = moyennes.stream().map(EleveMoyenne::moyenne).toList();
    return new StatistiqueDto.Classe(
        new Ref(d.classe().getId(), d.classe().getNom()),
        p,
        d.resultats().profil(),
        CalculResultats.repartition(valeurs),
        parSexe(moyennes.stream().map(e -> new Valeur(e.sexe(), e.moyenne())).toList()),
        d.resultats().matieres().stream()
            .map(
                s ->
                    new MatiereClasse(
                        s.matiere().id(), s.matiere().nom(), s.matiere().coefficient(), s.matiere().professeur(), s.evaluee(), s.moyenne(),
                        s.tauxReussite(), s.min(), s.max(), s.ecartType()))
            .toList(),
        evolution,
        moyennes);
  }

  /** Statistiques globales et classement général de l'établissement. */
  public StatistiqueDto.Etablissement etablissement(String anneeId, Periode p) {
    Baremes b = resultats.baremes();
    List<Classe> lesClasses = classes.findByAnnee_IdOrderByNiveauAscNomAsc(anneeId);
    List<DonneesClasse> donnees = lesClasses.stream().map(c -> resultats.resultatsClasse(c.getId(), p, b)).toList();

    List<EleveEtablissement> eleves = new ArrayList<>();
    for (DonneesClasse d : donnees) {
      for (ResultatEleve r : d.resultats().eleves()) {
        Inscription i = d.inscription(r.inscriptionId());
        Eleve e = i.getEleve();
        eleves.add(
            new EleveEtablissement(
                i.getId(), e.getId(), e.getMatricule(), e.getNom(), e.getPrenoms(), e.getSexe(), new Ref(d.classe().getId(), d.classe().getNom()),
                r.moyenne(), r.rang(), null));
      }
    }
    List<EleveEtablissement> avecMoyenne = eleves.stream().filter(e -> e.moyenne() != null).toList();
    Map<EleveEtablissement, Rang> rangs = CalculResultats.classer(avecMoyenne, EleveEtablissement::moyenne);
    List<EleveEtablissement> classement =
        new ArrayList<>(
            avecMoyenne.stream()
                .map(
                    e ->
                        new EleveEtablissement(
                            e.inscriptionId(), e.eleveId(), e.matricule(), e.nom(), e.prenoms(), e.sexe(), e.classe(), e.moyenne(), e.rangClasse(),
                            rangs.get(e)))
                .toList());
    classement.sort(Comparator.comparingInt((EleveEtablissement e) -> e.rang().rang()).thenComparing(EleveEtablissement::nom, ORDRE_ALPHABETIQUE));

    List<ClasseEtablissement> parClasse =
        donnees.stream()
            .map(
                d -> {
                  ProfilClasse pf = d.resultats().profil();
                  return new ClasseEtablissement(
                      d.classe().getId(), d.classe().getNom(), d.classe().getNiveau(), pf.effectif(), pf.moyenneGenerale(), pf.min(), pf.max(),
                      pf.nombreMoyennes(), pf.tauxReussite(), pf.ecartType(), null);
                })
            .toList();
    List<ClasseEtablissement> notees = parClasse.stream().filter(c -> c.moyenneGenerale() != null).toList();
    Map<ClasseEtablissement, Rang> rangsClasses = CalculResultats.classer(notees, ClasseEtablissement::moyenneGenerale);
    List<ClasseEtablissement> classees =
        new ArrayList<>(
            notees.stream()
                .map(
                    c ->
                        new ClasseEtablissement(
                            c.id(), c.nom(), c.niveau(), c.effectif(), c.moyenneGenerale(), c.min(), c.max(), c.nombreMoyennes(), c.tauxReussite(),
                            c.ecartType(), rangsClasses.get(c)))
                .toList());
    classees.sort(Comparator.comparingInt(c -> c.rang().rang()));

    // Matières communes à plusieurs classes, comparées par nom
    Map<String, List<StatMatiere>> parMatiere = new LinkedHashMap<>();
    for (DonneesClasse d : donnees) {
      for (StatMatiere s : d.resultats().matieres()) {
        if (s.evaluee()) parMatiere.computeIfAbsent(s.matiere().nom().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(s);
      }
    }
    List<MatiereComparee> matieres =
        new ArrayList<>(
            parMatiere.values().stream()
                .map(
                    l ->
                        new MatiereComparee(
                            l.get(0).matiere().nom(), l.size(),
                            arrondi(l.stream().mapToDouble(StatMatiere::moyenne).average().orElse(0)),
                            arrondi(l.stream().mapToDouble(StatMatiere::tauxReussite).average().orElse(0))))
                .toList());
    matieres.sort(Comparator.comparingDouble(MatiereComparee::moyenne).reversed());

    Map<String, Long> effectifs = Compte.enMap(inscriptions.compterParStatut(anneeId));
    List<Double> valeurs = avecMoyenne.stream().map(EleveEtablissement::moyenne).toList();
    int dernierRang = classement.isEmpty() ? -1 : classement.get(classement.size() - 1).rang().rang();
    List<ClasseEtablissement> toutes = new ArrayList<>(classees);
    parClasse.stream().filter(c -> c.moyenneGenerale() == null).forEach(toutes::add);
    return new StatistiqueDto.Etablissement(
        p,
        new Totaux(
            effectifs.getOrDefault("ACTIF", 0L), effectifs.getOrDefault("RENVOYE", 0L), lesClasses.size(),
            utilisateurs.countByRoleAndActifTrue(Role.PROFESSEUR),
            eleves.stream().filter(e -> e.sexe() == Sexe.F).count(), eleves.stream().filter(e -> e.sexe() == Sexe.M).count()),
        resume(valeurs),
        CalculResultats.repartition(valeurs),
        parSexe(avecMoyenne.stream().map(e -> new Valeur(e.sexe(), e.moyenne())).toList()),
        classement.stream().filter(e -> e.rang().rang() == 1).toList(),
        classement.stream().filter(e -> e.rang().rang() == dernierRang).toList(),
        classees.isEmpty() ? null : classees.get(0),
        classees.size() > 1 ? classees.get(classees.size() - 1) : null,
        toutes,
        matieres,
        classement);
  }
}
