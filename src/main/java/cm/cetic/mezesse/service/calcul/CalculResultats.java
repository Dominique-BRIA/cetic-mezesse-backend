package cm.cetic.mezesse.service.calcul;

import static cm.cetic.mezesse.util.Nombres.arrondi;

import cm.cetic.mezesse.service.calcul.Resultats.Critere;
import cm.cetic.mezesse.service.calcul.Resultats.Decision;
import cm.cetic.mezesse.service.calcul.Resultats.Groupe;
import cm.cetic.mezesse.service.calcul.Resultats.LigneGroupe;
import cm.cetic.mezesse.service.calcul.Resultats.LigneMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.MatiereEntree;
import cm.cetic.mezesse.service.calcul.Resultats.NoteEntree;
import cm.cetic.mezesse.service.calcul.Resultats.ProfilClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import cm.cetic.mezesse.service.calcul.Resultats.Regles;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatsClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Seuil;
import cm.cetic.mezesse.service.calcul.Resultats.StatMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.Tranche;
import java.nio.charset.StandardCharsets;
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
 *   <li>sur une séquence, selon la part des matières évaluées que l'élève a composées : en dessous du
 *       seuil « non classé » (1/3), il n'est pas classé ; entre ce seuil et le seuil « ignorées » (2/3),
 *       les matières non composées valent 0 ; au-delà, elles sont ignorées (leur coefficient ne compte pas) ;
 *   <li>trimestre : les séquences où l'élève est classé ; une matière y vaut la moyenne de ses notes (une
 *       seule note : celle-ci) ; non classé aux deux séquences, il n'est pas classé au trimestre ;
 *   <li>année : moyenne des trimestres où l'élève est classé ; aucun trimestre classé, pas classé ;
 *   <li>les rangs sont calculés sur les valeurs arrondies au centième ; les ex æquo partagent leur rang,
 *       sauf à la première place, qui revient à un seul élève (départage réglé dans les paramètres, puis
 *       tirage au sort).
 * </ul>
 */
public final class CalculResultats {

  private CalculResultats() {}

  private static final Collator ORDRE_ALPHABETIQUE = Collator.getInstance(Locale.FRENCH);

  /**
   * @param minutesNonJustifiees absences non justifiées de la période, par inscription (départage, tableau d'honneur)
   * @param noms nom complet de chaque inscription (départage par ordre alphabétique)
   */
  public record Entrees(
      Periode periode,
      List<MatiereEntree> matieres,
      List<String> inscriptions,
      List<NoteEntree> notes,
      List<Seuil> grades,
      List<Seuil> cotes,
      Regles regles,
      Map<String, Integer> minutesNonJustifiees,
      Map<String, String> noms) {

    public Entrees(Periode periode, List<MatiereEntree> matieres, List<String> inscriptions, List<NoteEntree> notes, List<Seuil> grades, List<Seuil> cotes) {
      this(periode, matieres, inscriptions, notes, grades, cotes, Regles.DEFAUT, Map.of(), Map.of());
    }
  }

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

  /**
   * Les élèves désignés : pour chaque rang, celui que l'ordre « departage » place en tête parmi ceux qui l'occupent
   * (seul, ou parmi les ex æquo). Les rangs ne changent pas : les autres ex æquo restent « ex ».
   */
  public static <T> Set<T> designes(List<T> elements, Map<T, Rang> rangs, Comparator<T> departage) {
    Map<Integer, List<T>> parRang = new java.util.TreeMap<>();
    for (T x : elements) {
      Rang r = rangs.get(x);
      if (r != null) parRang.computeIfAbsent(r.rang(), k -> new ArrayList<>()).add(x);
    }
    Set<T> choisis = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (List<T> groupe : parRang.values()) {
      if (departage != null) groupe.sort(departage);
      choisis.add(groupe.get(0));
    }
    return choisis;
  }

  /**
   * Ordre de départage (le meilleur d'abord) : les critères dans l'ordre réglé, puis un tirage au sort
   * stable (le même à chaque calcul, d'après l'identifiant et la période).
   *
   * @param moyennesGroupes moyenne de chaque groupe de matières, par nom de groupe en minuscules
   */
  public static <T> Comparator<T> departage(
      List<Critere> criteres,
      Function<T, Map<String, Double>> moyennesGroupes,
      Function<T, Integer> minutes,
      Function<T, String> nom,
      Function<T, String> cleTirage) {
    Comparator<T> c = (a, b) -> 0;
    for (Critere k : criteres) {
      switch (k.type()) {
        case Critere.GROUPE -> {
          String g = k.groupe() == null ? "" : k.groupe().strip().toLowerCase(Locale.ROOT);
          Function<T, Double> m = e -> moyennesGroupes.apply(e).get(g);
          c = c.thenComparing((a, b) -> Double.compare(arrondiOuMoins(m.apply(b)), arrondiOuMoins(m.apply(a))));
        }
        case Critere.DISCIPLINE -> c = c.thenComparingInt(e -> Objects.requireNonNullElse(minutes.apply(e), 0));
        case Critere.ALPHABETIQUE -> c = c.thenComparing(e -> Objects.requireNonNullElse(nom.apply(e), ""), ORDRE_ALPHABETIQUE);
        default -> {}
      }
    }
    return c.thenComparingLong(e -> tirage(cleTirage.apply(e)));
  }

  private static double arrondiOuMoins(Double v) {
    return v == null ? -1 : arrondi(v);
  }

  /** Tirage au sort stable : un nombre tiré de la clé (toujours le même pour la même clé). */
  static long tirage(String cle) {
    long h = 1125899906842597L;
    for (byte b : cle.getBytes(StandardCharsets.UTF_8)) h = 31 * h + b;
    h ^= h >>> 33;
    h *= 0xff51afd7ed558ccdL;
    h ^= h >>> 33;
    return h;
  }

  /** Critères par défaut : les groupes dont le nom contient « professionnel », la discipline, l'ordre alphabétique. */
  public static List<Critere> criteresParDefaut(List<Groupe> groupes) {
    List<Critere> l = new ArrayList<>();
    for (Groupe g : groupes) {
      if (g.nom().toLowerCase(Locale.ROOT).contains("professionnel")) l.add(new Critere(Critere.GROUPE, g.nom()));
    }
    l.add(new Critere(Critere.DISCIPLINE, null));
    l.add(new Critere(Critere.ALPHABETIQUE, null));
    return l;
  }

  /** Décision de fin d'année : 10 et plus, admis ; à partir de la moyenne de délibération, admis après délibération. */
  public static Decision decision(Double moyenneAnnuelle, Double deliberation) {
    if (moyenneAnnuelle == null) return null;
    double v = arrondi(moyenneAnnuelle);
    if (v >= 10) return Decision.ADMIS;
    if (deliberation != null && v >= deliberation) return Decision.ADMIS_DELIBERATION;
    return Decision.REDOUBLE;
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

  // ---------- Notes de chaque élève ----------

  /** Comment comptent les matières non composées d'un élève sur une séquence. */
  enum Bande {
    NON_CLASSE,
    ZERO,
    IGNOREES
  }

  /** Part des matières composées : sous le seuil « non classé », pas classé ; sous le seuil « ignorées », 0 ; sinon ignorées. */
  static Bande bande(int composees, int evaluees, Regles r) {
    double part = composees * 100.0 / evaluees;
    if (part < r.seuilNonClasse()) return Bande.NON_CLASSE;
    if (part < r.seuilIgnorees()) return Bande.ZERO;
    return Bande.IGNOREES;
  }

  /** L'élève sur une séquence : sa bande, et la valeur de chaque matière évaluée (note, 0, ou null si ignorée). */
  private record SequenceEleve(Bande bande, Map<String, Double> valeurs) {}

  /** Une matière sur la période : sa note (null si non évaluée ou ignorée) et le détail affiché au bulletin. */
  private record NotePeriode(Double note, List<Double> detail) {}

  /** Données de calcul partagées par toutes les périodes. */
  private static final class Contexte {
    final Map<String, Map<Integer, Map<String, Double>>> notes = new HashMap<>(); // matière → séquence → inscription → note
    final List<MatiereEntree> matieres;
    final Regles regles;
    final Map<Integer, Map<String, SequenceEleve>> sequences = new HashMap<>(); // séquence → inscription

    Contexte(List<MatiereEntree> matieres, List<NoteEntree> lesNotes, Set<String> inscrits, Regles regles) {
      this.matieres = matieres;
      this.regles = regles;
      for (NoteEntree n : lesNotes) {
        if (!inscrits.contains(n.inscriptionId())) continue;
        notes.computeIfAbsent(n.matiereId(), k -> new HashMap<>()).computeIfAbsent(n.sequence(), k -> new HashMap<>()).put(n.inscriptionId(), n.valeur());
      }
    }

    Double brute(String matiereId, int seq, String ins) {
      return notes.getOrDefault(matiereId, Map.of()).getOrDefault(seq, Map.of()).get(ins);
    }

    List<String> evaluees(int seq) {
      return matieres.stream().map(MatiereEntree::id).filter(m -> !notes.getOrDefault(m, Map.of()).getOrDefault(seq, Map.of()).isEmpty()).toList();
    }

    /** null si la séquence n'est pas évaluée (aucune note dans la classe). */
    SequenceEleve sequence(int seq, String ins) {
      return sequences.computeIfAbsent(seq, k -> new HashMap<>()).computeIfAbsent(ins, k -> calculerSequence(seq, ins));
    }

    private SequenceEleve calculerSequence(int seq, String ins) {
      List<String> evaluees = evaluees(seq);
      if (evaluees.isEmpty()) return null;
      int composees = (int) evaluees.stream().filter(m -> brute(m, seq, ins) != null).count();
      Bande b = bande(composees, evaluees.size(), regles);
      Map<String, Double> valeurs = new HashMap<>();
      for (String m : evaluees) {
        Double n = brute(m, seq, ins);
        valeurs.put(m, n != null ? n : b == Bande.ZERO ? 0.0 : null);
      }
      return new SequenceEleve(b, valeurs);
    }

    boolean classeSequence(int seq, String ins) {
      SequenceEleve s = sequence(seq, ins);
      return s != null && s.bande() != Bande.NON_CLASSE;
    }

    /** Classé sur la période : séquence classée, au moins une séquence classée du trimestre, au moins un trimestre classé. */
    boolean classe(Periode p, String ins) {
      if (p.estSequence()) return classeSequence(p.numero(), ins);
      if (p.estTrimestre()) return p.sequences().stream().anyMatch(s -> classeSequence(s, ins));
      return Periode.TRIMESTRES.stream().anyMatch(t -> classe(t, ins));
    }

    NotePeriode note(MatiereEntree m, Periode p, String ins) {
      if (p.estSequence()) {
        SequenceEleve s = sequence(p.numero(), ins);
        Double v = s == null ? null : s.valeurs().get(m.id());
        // Non classé : seules ses notes réelles s'affichent.
        if (s != null && s.bande() == Bande.NON_CLASSE) v = brute(m.id(), p.numero(), ins);
        return new NotePeriode(v, List.of());
      }
      if (p.estTrimestre()) {
        List<Double> detail = new ArrayList<>();
        for (int seq : p.sequences()) detail.add(note(m, Periode.valueOf("S" + seq), ins).note());
        List<Integer> classees = p.sequences().stream().filter(seq -> classeSequence(seq, ins)).toList();
        if (classees.isEmpty()) return new NotePeriode(moyenneNonNulle(detail), detail);
        List<Double> brutes = classees.stream().map(seq -> brute(m.id(), seq, ins)).filter(Objects::nonNull).toList();
        if (!brutes.isEmpty()) return new NotePeriode(moyenneDe(brutes), detail);
        // Jamais composée : 0 si une séquence classée la compte à 0, sinon ignorée.
        boolean zero = classees.stream().map(seq -> sequence(seq, ins)).anyMatch(s -> s.bande() == Bande.ZERO && s.valeurs().containsKey(m.id()));
        return new NotePeriode(zero ? 0.0 : null, detail);
      }
      List<Double> detail = new ArrayList<>();
      for (Periode t : Periode.TRIMESTRES) detail.add(note(m, t, ins).note());
      List<Double> classees = new ArrayList<>();
      for (int i = 0; i < Periode.TRIMESTRES.size(); i++) {
        if (classe(Periode.TRIMESTRES.get(i), ins) && detail.get(i) != null) classees.add(detail.get(i));
      }
      if (!classe(p, ins)) return new NotePeriode(moyenneNonNulle(detail), detail);
      return new NotePeriode(moyenneDe(classees), detail);
    }

    /** Moyenne de la période : pondérée par les coefficients ; sur l'année, moyenne des trimestres classés. */
    Double moyenne(Periode p, String ins) {
      if (!classe(p, ins)) return null;
      if (p == Periode.A) {
        return moyenneDe(Periode.TRIMESTRES.stream().filter(t -> classe(t, ins)).map(t -> moyenne(t, ins)).filter(Objects::nonNull).toList());
      }
      int coef = 0;
      double total = 0;
      for (MatiereEntree m : matieres) {
        Double n = note(m, p, ins).note();
        if (n == null) continue;
        coef += m.coefficient();
        total += n * m.coefficient();
      }
      return coef > 0 ? total / coef : null;
    }
  }

  private static Double moyenneNonNulle(List<Double> valeurs) {
    return moyenneDe(valeurs.stream().filter(Objects::nonNull).toList());
  }

  public static ResultatsClasse calculer(Entrees e) {
    Set<String> inscrits = new HashSet<>(e.inscriptions());
    List<MatiereEntree> matieres = new ArrayList<>(e.matieres());
    matieres.sort(
        Comparator.comparingInt((MatiereEntree m) -> m.groupe().ordre())
            .thenComparingInt(MatiereEntree::ordre)
            .thenComparing(MatiereEntree::nom, ORDRE_ALPHABETIQUE));
    Regles regles = e.regles() == null ? Regles.DEFAUT : e.regles();
    Contexte ctx = new Contexte(matieres, e.notes(), inscrits, regles);

    Map<String, Groupe> groupesVus = new LinkedHashMap<>();
    for (MatiereEntree m : matieres) groupesVus.putIfAbsent(m.groupe().id(), m.groupe());
    List<Groupe> groupes = new ArrayList<>(groupesVus.values());
    groupes.sort(Comparator.comparingInt(Groupe::ordre));

    // Notes de chaque élève dans chaque matière
    Map<String, Map<String, LigneMatiere>> lignes = new LinkedHashMap<>();
    for (String ins : e.inscriptions()) {
      Map<String, LigneMatiere> parMatiere = new LinkedHashMap<>();
      for (MatiereEntree m : matieres) {
        NotePeriode np = ctx.note(m, e.periode(), ins);
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

    // Groupes et moyenne générale de chaque élève ; la période est évaluée si une matière y a des notes.
    boolean periodeEvaluee = stats.stream().anyMatch(StatMatiere::evaluee);
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
      boolean classe = ctx.classe(e.periode(), ins);
      Double moyenne = ctx.moyenne(e.periode(), ins);
      eleves.add(
          new ResultatEleve(
              ins, matieres.stream().map(m -> parMatiere.get(m.id())).toList(), lignesGroupes, totalCoef, totalPoints, moyenne, null,
              mention(moyenne, e.cotes()), classe && moyenne != null, e.minutesNonJustifiees().getOrDefault(ins, 0),
              periodeEvaluee && !(classe && moyenne != null), false));
    }

    // Les rangs gardent leurs ex æquo ; dans chaque groupe d'ex æquo, le départage réglé (par défaut, matières
    // professionnelles, discipline, ordre alphabétique, puis tirage au sort) désigne celui qui porte son rang sans « ex ».
    List<Critere> criteres = regles.criteres() == null || regles.criteres().isEmpty() ? criteresParDefaut(groupes) : regles.criteres();
    Map<String, String> nomsGroupes = new HashMap<>();
    for (Groupe g : groupes) nomsGroupes.put(g.id(), g.nom().strip().toLowerCase(Locale.ROOT));
    Comparator<ResultatEleve> ordre =
        departage(
            criteres,
            r -> moyennesParNom(r.groupes(), nomsGroupes),
            ResultatEleve::minutesNonJustifiees,
            r -> e.noms().get(r.inscriptionId()),
            r -> r.inscriptionId() + "|" + e.periode().name());
    Map<ResultatEleve, Rang> rangs = classer(eleves, ResultatEleve::moyenne);
    Set<ResultatEleve> designes = designes(eleves, rangs, ordre);
    List<ResultatEleve> classes =
        eleves.stream()
            .map(
                r ->
                    new ResultatEleve(
                        r.inscriptionId(), r.matieres(), r.groupes(), r.totalCoef(), r.totalPoints(), r.moyenne(), rangs.get(r), r.cote(), r.classe(),
                        r.minutesNonJustifiees(), r.nonClasse(), designes.contains(r)))
            .toList();
    // Sur l'année, la délibération compte comme une réussite.
    double seuilReussite = e.periode() == Periode.A && regles.moyenneDeliberation() != null ? Math.min(10, regles.moyenneDeliberation()) : 10;
    return new ResultatsClasse(e.periode(), stats, groupes, classes, profilDe(classes.stream().map(ResultatEleve::moyenne).toList(), seuilReussite));
  }

  /** Moyenne de chaque groupe de l'élève, par nom de groupe en minuscules (départage). */
  public static Map<String, Double> moyennesParNom(List<LigneGroupe> lignes, Map<String, String> nomsGroupes) {
    Map<String, Double> m = new HashMap<>();
    for (LigneGroupe l : lignes) {
      String nom = nomsGroupes.get(l.groupeId());
      if (nom != null && l.moyenne() != null) m.put(nom, l.moyenne());
    }
    return m;
  }

  /** Profil d'un ensemble de moyennes (null = élève sans moyenne, compté dans l'effectif). */
  public static ProfilClasse profilDe(List<Double> moyennesAvecNulles) {
    return profilDe(moyennesAvecNulles, 10);
  }

  /** Profil, avec la moyenne qui vaut réussite (10, ou la moyenne de délibération sur l'année). */
  public static ProfilClasse profilDe(List<Double> moyennesAvecNulles, double seuilReussite) {
    List<Double> moyennes = moyennesAvecNulles.stream().filter(Objects::nonNull).toList();
    int reussis = (int) moyennes.stream().filter(v -> arrondi(v) >= seuilReussite).count();
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
