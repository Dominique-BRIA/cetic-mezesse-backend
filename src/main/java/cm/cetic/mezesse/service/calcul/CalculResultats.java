package cm.cetic.mezesse.service.calcul;

import static cm.cetic.mezesse.util.Nombres.arrondi;

import cm.cetic.mezesse.service.calcul.Resultats.Groupe;
import cm.cetic.mezesse.service.calcul.Resultats.LigneGroupe;
import cm.cetic.mezesse.service.calcul.Resultats.LigneMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.MatiereEntree;
import cm.cetic.mezesse.service.calcul.Resultats.NoteEntree;
import cm.cetic.mezesse.service.calcul.Resultats.ProfilClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatsClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Seuil;
import cm.cetic.mezesse.service.calcul.Resultats.StatMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.Tranche;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Calcul des résultats d'une classe pour une période (séquence, trimestre ou année). Module pur : il
 * ne touche pas la base, ce qui permet de le tester isolément.
 *
 * <p>Règles appliquées (docs/REGLES-DE-GESTION.md) :
 *
 * <ul>
 *   <li>une matière n'est « évaluée » sur une séquence que si au moins un élève de la classe y a une
 *       note ; une matière non évaluée est retirée du calcul pour tout le monde ;
 *   <li>dans une matière évaluée, l'élève sans note a 0 ;
 *   <li>trimestre = moyenne des séquences évaluées du trimestre, année = moyenne des trimestres évalués ;
 *   <li>les rangs sont calculés sur les valeurs arrondies au centième, ex æquo compris.
 * </ul>
 */
public final class CalculResultats {

  private CalculResultats() {}

  private static final Collator ORDRE_ALPHABETIQUE = Collator.getInstance(Locale.FRENCH);

  public record Entrees(
      Periode periode, List<MatiereEntree> matieres, List<String> inscriptions, List<NoteEntree> notes, List<Seuil> grades, List<Seuil> cotes) {}

  static Double moyenneDe(List<Double> valeurs) {
    if (valeurs.isEmpty()) return null;
    double s = 0;
    for (double v : valeurs) s += v;
    return s / valeurs.size();
  }

  static Double ecartTypeDe(List<Double> valeurs) {
    Double m = moyenneDe(valeurs);
    if (m == null) return null;
    double s = 0;
    for (double v : valeurs) s += (v - m) * (v - m);
    return Math.sqrt(s / valeurs.size());
  }

  /** Mention d'une valeur : le seuil le plus haut dont la borne basse est atteinte. */
  public static String mention(Double valeur, List<Seuil> seuils) {
    if (valeur == null) return null;
    double v = arrondi(valeur);
    return seuils.stream()
        .sorted(Comparator.comparingDouble(Seuil::min).reversed())
        .filter(s -> v >= s.min())
        .map(Seuil::libelle)
        .findFirst()
        .orElse(null);
  }

  /** Rangs avec ex æquo : deux valeurs identiques au centième ont le même rang. */
  public static <T> Map<T, Rang> classer(List<T> elements, Function<T, Double> valeur) {
    List<T> tries = new ArrayList<>(elements.stream().filter(e -> valeur.apply(e) != null).toList());
    tries.sort(Comparator.comparingDouble((T e) -> arrondi(valeur.apply(e))).reversed());
    Map<T, Rang> rangs = new IdentityHashMap<>();
    for (T e : tries) {
      double v = arrondi(valeur.apply(e));
      int premier = -1;
      int nombre = 0;
      for (int i = 0; i < tries.size(); i++) {
        if (arrondi(valeur.apply(tries.get(i))) == v) {
          if (premier < 0) premier = i;
          nombre++;
        }
      }
      rangs.put(e, new Rang(premier + 1, nombre > 1));
    }
    return rangs;
  }

  /** « 1er », « 2e », « 3e ex » ; en anglais « 1st », « 2nd », « 11th ». */
  public static String formatRang(Rang r) {
    return formatRang(r, false);
  }

  public static String formatRang(Rang r, boolean anglais) {
    if (r == null) return "";
    String suffixe;
    if (anglais) {
      int d = r.rang() % 10;
      int c = r.rang() % 100;
      suffixe = c >= 11 && c <= 13 ? "th" : d == 1 ? "st" : d == 2 ? "nd" : d == 3 ? "rd" : "th";
    } else {
      suffixe = r.rang() == 1 ? "er" : "e";
    }
    return r.rang() + suffixe + (r.exAequo() ? " ex" : "");
  }

  /** Index des notes : matière → séquence → inscription → valeur. */
  private static Map<String, Map<Integer, Map<String, Double>>> indexer(List<NoteEntree> notes, Set<String> inscriptions) {
    Map<String, Map<Integer, Map<String, Double>>> index = new HashMap<>();
    for (NoteEntree n : notes) {
      if (!inscriptions.contains(n.inscriptionId())) continue;
      index.computeIfAbsent(n.matiereId(), k -> new HashMap<>()).computeIfAbsent(n.sequence(), k -> new HashMap<>()).put(n.inscriptionId(), n.valeur());
    }
    return index;
  }

  /** Note d'une séquence : null si la matière n'y est pas évaluée, 0 si l'élève n'a pas de note. */
  private static Double noteSequence(Map<String, Map<Integer, Map<String, Double>>> index, String matiereId, int seq, String inscriptionId) {
    Map<String, Double> parEleve = index.getOrDefault(matiereId, Map.of()).get(seq);
    if (parEleve == null || parEleve.isEmpty()) return null;
    return parEleve.getOrDefault(inscriptionId, 0.0);
  }

  private static Double moyenneNonNulle(List<Double> valeurs) {
    return moyenneDe(valeurs.stream().filter(Objects::nonNull).toList());
  }

  private record NotePeriode(Double note, List<Double> detail) {}

  /** Note de l'élève dans une matière sur la période, avec le détail affiché au bulletin. */
  private static NotePeriode notePeriode(Map<String, Map<Integer, Map<String, Double>>> index, String matiereId, Periode periode, String inscriptionId) {
    if (periode.estSequence()) return new NotePeriode(noteSequence(index, matiereId, periode.numero(), inscriptionId), List.of());
    List<Double> detail = new ArrayList<>();
    if (periode.estTrimestre()) {
      for (int s : periode.sequences()) detail.add(noteSequence(index, matiereId, s, inscriptionId));
    } else {
      for (Periode t : Periode.TRIMESTRES) detail.add(notePeriode(index, matiereId, t, inscriptionId).note());
    }
    return new NotePeriode(moyenneNonNulle(detail), detail);
  }

  public static ResultatsClasse calculer(Entrees e) {
    Set<String> inscrits = new HashSet<>(e.inscriptions());
    var index = indexer(e.notes(), inscrits);
    List<MatiereEntree> matieres = new ArrayList<>(e.matieres());
    matieres.sort(
        Comparator.comparingInt((MatiereEntree m) -> m.groupe().ordre())
            .thenComparingInt(MatiereEntree::ordre)
            .thenComparing(MatiereEntree::nom, ORDRE_ALPHABETIQUE));

    Map<String, Groupe> groupesVus = new LinkedHashMap<>();
    for (MatiereEntree m : matieres) groupesVus.putIfAbsent(m.groupe().id(), m.groupe());
    List<Groupe> groupes = new ArrayList<>(groupesVus.values());
    groupes.sort(Comparator.comparingInt(Groupe::ordre));

    // Notes de chaque élève dans chaque matière
    Map<String, Map<String, LigneMatiere>> lignes = new LinkedHashMap<>();
    for (String ins : e.inscriptions()) {
      Map<String, LigneMatiere> parMatiere = new LinkedHashMap<>();
      for (MatiereEntree m : matieres) {
        NotePeriode np = notePeriode(index, m.id(), e.periode(), ins);
        parMatiere.put(
            m.id(),
            new LigneMatiere(m.id(), np.note(), np.detail(), np.note() == null ? null : np.note() * m.coefficient(), null, mention(np.note(), e.grades())));
      }
      lignes.put(ins, parMatiere);
    }

    // Statistiques et rangs par matière
    List<StatMatiere> stats = new ArrayList<>();
    for (MatiereEntree m : matieres) {
      List<LigneMatiere> lignesMatiere = e.inscriptions().stream().map(ins -> lignes.get(ins).get(m.id())).toList();
      List<Double> valeurs = lignesMatiere.stream().map(LigneMatiere::note).filter(Objects::nonNull).toList();
      Map<LigneMatiere, Rang> rangs = classer(lignesMatiere, LigneMatiere::note);
      for (String ins : e.inscriptions()) {
        LigneMatiere l = lignes.get(ins).get(m.id());
        lignes.get(ins).put(m.id(), l.avecRang(rangs.get(l)));
      }
      boolean evaluee = !valeurs.isEmpty();
      stats.add(
          new StatMatiere(
              m, evaluee, moyenneDe(valeurs),
              evaluee ? valeurs.stream().mapToDouble(Double::doubleValue).min().getAsDouble() : null,
              evaluee ? valeurs.stream().mapToDouble(Double::doubleValue).max().getAsDouble() : null,
              evaluee ? valeurs.stream().filter(v -> arrondi(v) >= 10).count() * 100.0 / valeurs.size() : null,
              ecartTypeDe(valeurs), valeurs.size()));
    }

    // Groupes et moyenne générale de chaque élève
    List<ResultatEleve> eleves = new ArrayList<>();
    for (String ins : e.inscriptions()) {
      Map<String, LigneMatiere> parMatiere = lignes.get(ins);
      List<LigneGroupe> lignesGroupes = new ArrayList<>();
      for (Groupe g : groupes) {
        int coefficient = 0;
        double total = 0;
        for (MatiereEntree m : matieres) {
          LigneMatiere l = parMatiere.get(m.id());
          if (!m.groupe().id().equals(g.id()) || l.note() == null) continue;
          coefficient += m.coefficient();
          total += l.total();
        }
        lignesGroupes.add(new LigneGroupe(g.id(), coefficient, total, coefficient > 0 ? total / coefficient : null));
      }
      int totalCoef = lignesGroupes.stream().mapToInt(LigneGroupe::coefficient).sum();
      double totalPoints = lignesGroupes.stream().mapToDouble(LigneGroupe::total).sum();
      Double moyenne = totalCoef > 0 ? totalPoints / totalCoef : null;
      eleves.add(
          new ResultatEleve(
              ins, matieres.stream().map(m -> parMatiere.get(m.id())).toList(), lignesGroupes, totalCoef, totalPoints, moyenne, null,
              mention(moyenne, e.cotes())));
    }
    Map<ResultatEleve, Rang> rangs = classer(eleves, ResultatEleve::moyenne);
    List<ResultatEleve> classes =
        eleves.stream()
            .map(r -> new ResultatEleve(r.inscriptionId(), r.matieres(), r.groupes(), r.totalCoef(), r.totalPoints(), r.moyenne(), rangs.get(r), r.cote()))
            .toList();
    return new ResultatsClasse(e.periode(), stats, groupes, classes, profilDe(classes.stream().map(ResultatEleve::moyenne).toList()));
  }

  /** Profil d'un ensemble de moyennes (null = élève sans moyenne, compté dans l'effectif). */
  public static ProfilClasse profilDe(List<Double> moyennesAvecNulles) {
    List<Double> moyennes = moyennesAvecNulles.stream().filter(Objects::nonNull).toList();
    int reussis = (int) moyennes.stream().filter(v -> arrondi(v) >= 10).count();
    boolean vide = moyennes.isEmpty();
    return new ProfilClasse(
        moyennesAvecNulles.size(), moyenneDe(moyennes),
        vide ? null : moyennes.stream().mapToDouble(Double::doubleValue).min().getAsDouble(),
        vide ? null : moyennes.stream().mapToDouble(Double::doubleValue).max().getAsDouble(),
        reussis, vide ? null : reussis * 100.0 / moyennes.size(), ecartTypeDe(moyennes));
  }

  /** Tranches des histogrammes. */
  private static final double[][] TRANCHES = {{0, 5}, {5, 8}, {8, 10}, {10, 12}, {12, 14}, {14, 16}, {16, 20.01}};

  public static List<Tranche> repartition(List<Double> valeurs) {
    List<Tranche> r = new ArrayList<>();
    for (double[] t : TRANCHES) {
      int n = (int) valeurs.stream().filter(v -> arrondi(v) >= t[0] && arrondi(v) < t[1]).count();
      r.add(new Tranche((int) t[0] + "–" + (t[1] > 20 ? 20 : (int) t[1]), n));
    }
    return r;
  }
}
