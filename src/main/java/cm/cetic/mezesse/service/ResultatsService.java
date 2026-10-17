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
    ResultatsClasse r =
        CalculResultats.calculer(
            new CalculResultats.Entrees(periode, entrees, actives.stream().map(Inscription::getId).toList(), lesNotes, b.grades(), b.cotes()));
    return new DonneesClasse(classe, actives, r, b);
  }
}
