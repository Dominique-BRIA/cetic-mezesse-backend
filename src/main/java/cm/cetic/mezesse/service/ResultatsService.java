package cm.cetic.mezesse.service;

import cm.cetic.mezesse.entity.Bareme;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.BaremeRepository;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.NoteRepository;
import cm.cetic.mezesse.service.calcul.CalculResultats;
import cm.cetic.mezesse.service.calcul.Periode;
import cm.cetic.mezesse.service.calcul.Resultats.Baremes;
import cm.cetic.mezesse.service.calcul.Resultats.Groupe;
import cm.cetic.mezesse.service.calcul.Resultats.MatiereEntree;
import cm.cetic.mezesse.service.calcul.Resultats.NoteEntree;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatsClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Seuil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Charge depuis la base tout ce qu'il faut pour calculer les résultats d'une classe. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultatsService {

  private final ClasseRepository classes;
  private final MatiereRepository matieres;
  private final InscriptionRepository inscriptions;
  private final NoteRepository notes;
  private final BaremeRepository baremes;
  private final EtablissementService etablissement;
  private final BilanAbsences bilanAbsences;

  /** Classe, élèves actifs (ordre alphabétique) et résultats calculés. */
  public record DonneesClasse(Classe classe, List<Inscription> inscriptions, ResultatsClasse resultats, Baremes baremes) {

    public Inscription inscription(String id) {
      return inscriptions.stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow();
    }
  }

  public Baremes baremes() {
    List<Bareme> lignes = baremes.findAll();
    return new Baremes(seuils(lignes, TypeBareme.GRADE), seuils(lignes, TypeBareme.COTE));
  }

  private static List<Seuil> seuils(List<Bareme> lignes, TypeBareme type) {
    return lignes.stream().filter(b -> b.getType() == type).map(b -> new Seuil(b.getLibelle(), b.getLibelleLong(), b.getMin().doubleValue())).toList();
  }

  /** Règles des paramètres : seuils des matières composées, départage de la première place, délibération. */
  public cm.cetic.mezesse.service.calcul.Resultats.Regles regles() {
    var e = etablissement.requis();
    List<cm.cetic.mezesse.service.calcul.Resultats.Critere> criteres =
        e.getCriteresDepartage() == null
            ? List.of()
            : e.getCriteresDepartage().stream().map(c -> new cm.cetic.mezesse.service.calcul.Resultats.Critere(c.get("type"), c.get("groupe"))).toList();
    return new cm.cetic.mezesse.service.calcul.Resultats.Regles(
        e.getSeuilNonClasse().doubleValue(), e.getSeuilMatieresIgnorees().doubleValue(), criteres,
        e.getMoyenneDeliberation() == null ? null : e.getMoyenneDeliberation().doubleValue());
  }

  public DonneesClasse resultatsClasse(String classeId, Periode periode) {
    return resultatsClasse(classeId, periode, baremes());
  }

  public DonneesClasse resultatsClasse(String classeId, Periode periode, Baremes b) {
    Classe classe = classes.findById(classeId).orElseThrow(() -> ApiException.introuvable("Classe"));
    List<MatiereEntree> entrees =
        matieres.deLaClassePourCalcul(classeId).stream()
            .map(
                (Matiere m) ->
                    new MatiereEntree(
                        m.getId(), m.getNom(), m.getNomEn(), m.getCoefficient(), m.getOrdre(),
                        new Groupe(m.getGroupe().getId(), m.getGroupe().getNom(), m.getGroupe().getNomEn(), m.getGroupe().getOrdre()),
                        m.getProfesseur() == null ? null : m.getProfesseur().nomComplet()))
            .toList();
    List<Inscription> actives = inscriptions.activesDeLaClasse(classeId);
    List<NoteEntree> lesNotes =
        notes.pourCalcul(classeId, periode.sequences()).stream()
            .map(n -> new NoteEntree(n.inscriptionId(), n.matiereId(), n.sequence(), n.valeur().doubleValue()))
            .toList();
    List<String> ids = actives.stream().map(Inscription::getId).toList();
    // Absences non justifiées de la période (départage de la première place, tableau d'honneur) et noms (ordre alphabétique).
    java.util.Map<String, Integer> minutes = new java.util.HashMap<>();
    bilanAbsences.heures(ids, periode.sequences()).forEach((k, h) -> minutes.put(k, h.nonJustifiees()));
    java.util.Map<String, String> noms = new java.util.HashMap<>();
    for (Inscription i : actives) noms.put(i.getId(), i.getEleve().getNom() + " " + i.getEleve().getPrenoms());
    ResultatsClasse r =
        CalculResultats.calculer(new CalculResultats.Entrees(periode, entrees, ids, lesNotes, b.grades(), b.cotes(), regles(), minutes, noms));
    return new DonneesClasse(classe, actives, r, b);
  }
}
