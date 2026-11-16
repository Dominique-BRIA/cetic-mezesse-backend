package cm.cetic.mezesse.service.calcul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cm.cetic.mezesse.service.calcul.Resultats.Groupe;
import cm.cetic.mezesse.service.calcul.Resultats.MatiereEntree;
import cm.cetic.mezesse.service.calcul.Resultats.NoteEntree;
import cm.cetic.mezesse.service.calcul.Resultats.Rang;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatEleve;
import cm.cetic.mezesse.service.calcul.Resultats.ResultatsClasse;
import cm.cetic.mezesse.service.calcul.Resultats.Seuil;
import cm.cetic.mezesse.service.calcul.Resultats.StatMatiere;
import cm.cetic.mezesse.service.calcul.Resultats.Tranche;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Règles de calcul des résultats (docs/REGLES-DE-GESTION.md). */
class CalculResultatsTest {

  static final List<Seuil> GRADES =
      List.of(new Seuil("A", null, 16), new Seuil("B+", null, 15), new Seuil("B", null, 14), new Seuil("C+", null, 12), new Seuil("C", null, 10), new Seuil("D", null, 0));
  static final List<Seuil> COTES =
      List.of(
          new Seuil("CTBA", null, 16), new Seuil("CBA", null, 14), new Seuil("CA", null, 12), new Seuil("CMA", null, 10), new Seuil("CNA", null, 0));

  static final Groupe G1 = new Groupe("g1", "Général", null, 1);
  static final Groupe G2 = new Groupe("g2", "Pro", null, 2);

  static MatiereEntree mat(String id, int coefficient, Groupe groupe, int ordre) {
    return new MatiereEntree(id, id, null, coefficient, ordre, groupe, null);
  }

  static NoteEntree n(String inscription, String matiere, int sequence, double valeur) {
    return new NoteEntree(inscription, matiere, sequence, valeur);
  }

  static ResultatsClasse calculer(Periode p, List<MatiereEntree> matieres, List<String> inscriptions, List<NoteEntree> notes) {
    return CalculResultats.calculer(new CalculResultats.Entrees(p, matieres, inscriptions, notes, GRADES, COTES));
  }

  @Nested
  class Mentions {
    @Test
    void appliqueLesSeuilsDesGrades() {
      assertThat(CalculResultats.mention(17.4, GRADES)).isEqualTo("A");
      assertThat(CalculResultats.mention(16.0, GRADES)).isEqualTo("A");
      assertThat(CalculResultats.mention(15.99, GRADES)).isEqualTo("B+");
      assertThat(CalculResultats.mention(15.0, GRADES)).isEqualTo("B+");
      assertThat(CalculResultats.mention(14.0, GRADES)).isEqualTo("B");
      assertThat(CalculResultats.mention(12.8, GRADES)).isEqualTo("C+");
      assertThat(CalculResultats.mention(12.05, GRADES)).isEqualTo("C+");
      assertThat(CalculResultats.mention(10.0, GRADES)).isEqualTo("C");
      assertThat(CalculResultats.mention(9.99, GRADES)).isEqualTo("D");
      assertThat(CalculResultats.mention(0.0, GRADES)).isEqualTo("D");
      assertThat(CalculResultats.mention(null, GRADES)).isNull();
    }

    @Test
    void arronditAuCentiemeAvantDeComparer() {
      assertThat(CalculResultats.mention(9.996, GRADES)).isEqualTo("C");
    }

    @Test
    void donneLaCoteDeLaMoyenneGenerale() {
      assertThat(CalculResultats.mention(11.59, COTES)).isEqualTo("CMA");
      assertThat(CalculResultats.mention(16.2, COTES)).isEqualTo("CTBA");
      assertThat(CalculResultats.mention(8.0, COTES)).isEqualTo("CNA");
    }
  }

  @Nested
  class Rangs {
    record V(Double v) {}

    @Test
    void memeRangAuxExAequoEtSautDesSuivants() {
      List<V> e = List.of(new V(12.0), new V(15.0), new V(12.0), new V(9.0));
      Map<V, Rang> r = CalculResultats.classer(e, V::v);
      assertThat(r.get(e.get(1))).isEqualTo(new Rang(1, false));
      assertThat(r.get(e.get(0))).isEqualTo(new Rang(2, true));
      assertThat(r.get(e.get(2))).isEqualTo(new Rang(2, true));
      assertThat(r.get(e.get(3))).isEqualTo(new Rang(4, false));
    }

    @Test
    void ignoreLesValeursNulles() {
      List<V> e = Arrays.asList(new V(null), new V(10.0));
      Map<V, Rang> r = CalculResultats.classer(e, V::v);
      assertThat(r).doesNotContainKey(e.get(0));
      assertThat(r.get(e.get(1)).rang()).isEqualTo(1);
    }

    @Test
    void formateLesRangsEnFrancaisEtEnAnglais() {
      assertThat(CalculResultats.formatRang(new Rang(1, false))).isEqualTo("1er");
      assertThat(CalculResultats.formatRang(new Rang(3, true))).isEqualTo("3e ex");
      assertThat(CalculResultats.formatRang(new Rang(2, false), true)).isEqualTo("2nd");
      assertThat(CalculResultats.formatRang(new Rang(11, false), true)).isEqualTo("11th");
      assertThat(CalculResultats.formatRang(new Rang(23, false), true)).isEqualTo("23rd");
    }
  }

  @Nested
  class Sequence {
    final List<MatiereEntree> matieres = List.of(mat("maths", 4, G1, 1), mat("fr", 2, G1, 2), mat("atelier", 3, G2, 1), mat("eps", 1, G2, 2));
    final List<NoteEntree> notes =
        List.of(
            n("a", "maths", 1, 12), n("b", "maths", 1, 8), n("a", "fr", 1, 14), // b n'a pas de note de français : 0
            n("a", "atelier", 1, 15), n("b", "atelier", 1, 16), n("c", "atelier", 1, 10), // eps : personne n'a de note
            n("a", "maths", 2, 20)); // autre séquence, ignorée
    final ResultatsClasse r = calculer(Periode.S1, matieres, List.of("a", "b", "c"), notes);

    ResultatEleve eleve(String id) {
      return r.eleve(id);
    }

    @Test
    void zeroPourUnEleveSansNoteDansUneMatiereEvaluee() {
      assertThat(eleve("b").matieres().get(1).note()).isEqualTo(0.0);
      assertThat(eleve("c").matieres().get(0).note()).isEqualTo(0.0);
    }

    @Test
    void retirePourTousUneMatiereQuePersonneNaNotee() {
      StatMatiere eps = r.matieres().stream().filter(s -> s.matiere().id().equals("eps")).findFirst().orElseThrow();
      assertThat(eps.evaluee()).isFalse();
      assertThat(eleve("a").matieres().get(3).note()).isNull();
      assertThat(eleve("a").totalCoef()).isEqualTo(4 + 2 + 3);
    }

    @Test
    void totauxGroupesEtMoyenneGenerale() {
      ResultatEleve a = eleve("a");
      assertThat(a.totalPoints()).isEqualTo(12 * 4 + 14 * 2 + 15 * 3);
      assertThat(a.moyenne()).isCloseTo(121.0 / 9, within(1e-10));
      assertThat(a.groupes().get(0).coefficient()).isEqualTo(6);
      assertThat(a.groupes().get(0).total()).isEqualTo(76);
      assertThat(a.groupes().get(0).moyenne()).isCloseTo(76.0 / 6, within(1e-10));
      assertThat(a.groupes().get(1).coefficient()).isEqualTo(3);
      assertThat(a.groupes().get(1).moyenne()).isEqualTo(15.0);
    }

    @Test
    void classementEtStatistiquesDeMatiere() {
      assertThat(eleve("a").rang().rang()).isEqualTo(1);
      StatMatiere maths = r.matieres().get(0);
      assertThat(maths.moyenne()).isCloseTo(20.0 / 3, within(1e-10));
      assertThat(maths.min()).isEqualTo(0.0);
      assertThat(maths.max()).isEqualTo(12.0);
      assertThat(maths.tauxReussite()).isCloseTo(100.0 / 3, within(1e-10));
      assertThat(eleve("b").matieres().get(2).rang()).isEqualTo(new Rang(1, false));
    }

    @Test
    void profilDeLaClasse() {
      assertThat(r.profil().effectif()).isEqualTo(3);
      assertThat(r.profil().nombreMoyennes()).isEqualTo(1);
      assertThat(r.profil().max()).isCloseTo(121.0 / 9, within(1e-10));
    }
  }

  @Nested
  class TrimestreEtAnnee {
    final List<MatiereEntree> matieres = List.of(mat("maths", 2, G1, 1));

    @Test
    void moyenneDesSequencesEvalueesDuTrimestre() {
      ResultatsClasse r = calculer(Periode.T1, matieres, List.of("a", "b"), List.of(n("a", "maths", 1, 10), n("a", "maths", 2, 14), n("b", "maths", 1, 8)));
      assertThat(r.eleves().get(0).matieres().get(0).detail()).containsExactly(10.0, 14.0);
      assertThat(r.eleves().get(0).matieres().get(0).note()).isEqualTo(12.0);
      // b n'a rien composé en séquence 2 : non classé sur cette séquence, sa séquence 1 devient son trimestre.
      assertThat(r.eleves().get(1).matieres().get(0).note()).isEqualTo(8.0);
      assertThat(r.eleves().get(1).moyenne()).isEqualTo(8.0);
    }

    @Test
    void ignoreUneSequenceNonEvaluee() {
      ResultatsClasse r = calculer(Periode.T1, matieres, List.of("a", "b"), List.of(n("a", "maths", 1, 10), n("b", "maths", 1, 16)));
      assertThat(r.eleves().get(0).matieres().get(0).detail()).containsExactly(10.0, null);
      assertThat(r.eleves().get(0).matieres().get(0).note()).isEqualTo(10.0);
    }

    @Test
    void moyenneDesTrimestresSurLAnnee() {
      ResultatsClasse r =
          calculer(
              Periode.A, matieres, List.of("a"),
              List.of(n("a", "maths", 1, 10), n("a", "maths", 2, 12), n("a", "maths", 3, 14), n("a", "maths", 5, 9), n("a", "maths", 6, 11)));
      assertThat(r.eleves().get(0).matieres().get(0).detail()).containsExactly(11.0, 14.0, 10.0);
      assertThat(r.eleves().get(0).matieres().get(0).note()).isCloseTo(35.0 / 3, within(1e-10));
    }
  }

  /** Règle du tiers et des deux tiers : non classé, matières à 0, ou matières ignorées. */
  @Nested
  class MatieresComposees {
    // Six matières de coefficient 1, toutes évaluées (l'élève « ref » a toutes ses notes).
    final List<MatiereEntree> matieres =
        List.of(mat("m1", 1, G1, 1), mat("m2", 1, G1, 2), mat("m3", 1, G1, 3), mat("m4", 1, G2, 1), mat("m5", 1, G2, 2), mat("m6", 1, G2, 3));

    List<NoteEntree> notes(String eleve, int combien) {
      List<NoteEntree> l = new java.util.ArrayList<>();
      for (int i = 1; i <= 6; i++) l.add(n("ref", "m" + i, 1, 10));
      for (int i = 1; i <= combien; i++) l.add(n(eleve, "m" + i, 1, 12));
      return l;
    }

    @Test
    void moinsDuTiersNonClasse() {
      ResultatsClasse r = calculer(Periode.S1, matieres, List.of("ref", "x"), notes("x", 1));
      ResultatEleve x = r.eleve("x");
      assertThat(x.moyenne()).isNull();
      assertThat(x.rang()).isNull();
      assertThat(x.nonClasse()).isTrue();
      assertThat(x.matieres().get(0).note()).isEqualTo(12.0);
      assertThat(x.matieres().get(1).note()).isNull();
    }

    @Test
    void entreLeTiersEtLesDeuxTiersLesMatieresManquantesValentZero() {
      // 3 matières sur 6 : la moitié.
      ResultatEleve x = calculer(Periode.S1, matieres, List.of("ref", "x"), notes("x", 3)).eleve("x");
      assertThat(x.matieres().get(5).note()).isEqualTo(0.0);
      assertThat(x.totalCoef()).isEqualTo(6);
      assertThat(x.moyenne()).isEqualTo(6.0);
    }

    @Test
    void auDelaDesDeuxTiersLesMatieresManquantesSontIgnorees() {
      // 5 matières sur 6.
      ResultatEleve x = calculer(Periode.S1, matieres, List.of("ref", "x"), notes("x", 5)).eleve("x");
      assertThat(x.matieres().get(5).note()).isNull();
      assertThat(x.totalCoef()).isEqualTo(5);
      assertThat(x.moyenne()).isEqualTo(12.0);
    }

    @Test
    void lesSeuilsSeReglent() {
      List<NoteEntree> l = notes("x", 3);
      ResultatsClasse r =
          CalculResultats.calculer(
              new CalculResultats.Entrees(
                  Periode.S1, matieres, List.of("ref", "x"), l, GRADES, COTES, new Resultats.Regles(60, 80, List.of(), null), Map.of(), Map.of()));
      assertThat(r.eleve("x").nonClasse()).isTrue();
    }
  }

  @Nested
  class TrimestreClasse {
    final List<MatiereEntree> matieres = List.of(mat("maths", 2, G1, 1), mat("fr", 1, G1, 2));

    @Test
    void uneSeuleNoteDansLeTrimestreDevientLaNoteTrimestrielle() {
      // x n'a pas de note de français en séquence 1 mais 16 en séquence 2 : 16 devient sa note trimestrielle de français.
      ResultatsClasse r =
          calculer(
              Periode.T1, matieres, List.of("x", "y"),
              List.of(n("x", "maths", 1, 10), n("y", "maths", 1, 10), n("y", "fr", 1, 10), n("x", "maths", 2, 14), n("x", "fr", 2, 16), n("y", "maths", 2, 9), n("y", "fr", 2, 9)));
      assertThat(r.eleve("x").matieres().get(1).note()).isEqualTo(16.0);
    }

    @Test
    void nonClasseAuxDeuxSequencesNonClasseAuTrimestre() {
      ResultatsClasse r =
          calculer(
              Periode.T1, List.of(mat("m1", 1, G1, 1), mat("m2", 1, G1, 2), mat("m3", 1, G1, 3), mat("m4", 1, G1, 4)), List.of("ref", "x"),
              List.of(
                  n("ref", "m1", 1, 10), n("ref", "m2", 1, 10), n("ref", "m3", 1, 10), n("ref", "m4", 1, 10), n("ref", "m1", 2, 10), n("ref", "m2", 2, 10),
                  n("ref", "m3", 2, 10), n("ref", "m4", 2, 10), n("x", "m1", 1, 15), n("x", "m1", 2, 15)));
      assertThat(r.eleve("x").moyenne()).isNull();
      assertThat(r.eleve("x").nonClasse()).isTrue();
    }

    @Test
    void lAnneeEstLaMoyenneDesTrimestresClasses() {
      List<MatiereEntree> m = List.of(mat("maths", 1, G1, 1));
      // x : classé au T1 (12) et au T3 (8), rien au T2 où ref a des notes : année = (12 + 8) / 2.
      ResultatsClasse r =
          calculer(
              Periode.A, m, List.of("ref", "x"),
              List.of(n("ref", "maths", 1, 10), n("ref", "maths", 3, 10), n("ref", "maths", 5, 10), n("x", "maths", 1, 12), n("x", "maths", 5, 8)));
      assertThat(r.eleve("x").moyenne()).isEqualTo(10.0);
    }
  }

  @Nested
  class PremierUnique {
    final List<MatiereEntree> matieres = List.of(mat("fr", 1, G1, 1), mat("atelier", 1, G2, 1));

    ResultatsClasse calculer(List<Resultats.Critere> criteres, Map<String, Integer> minutes) {
      // a et b : 12 de moyenne ; a meilleur en Pro (14 contre 10), b meilleur en Général.
      List<NoteEntree> notes = List.of(n("a", "fr", 1, 10), n("a", "atelier", 1, 14), n("b", "fr", 1, 14), n("b", "atelier", 1, 10), n("c", "fr", 1, 12), n("c", "atelier", 1, 12), n("d", "fr", 1, 5), n("d", "atelier", 1, 5));
      return CalculResultats.calculer(
          new CalculResultats.Entrees(
              Periode.S1, matieres, List.of("a", "b", "c", "d"), notes, GRADES, COTES, new Resultats.Regles(33.33, 66.67, criteres, null), minutes,
              Map.of("a", "ZOA Paul", "b", "ABENA Luc", "c", "MBARGA Eve", "d", "ONANA Jo")));
    }

    ResultatEleve designe(ResultatsClasse r) {
      List<ResultatEleve> l = r.eleves().stream().filter(e -> e.designe() && e.rang() != null && e.rang().rang() == 1).toList();
      assertThat(l).hasSize(1);
      return l.get(0);
    }

    @Test
    void lesRangsGardentLeursExAequoEtLeGroupeChoisiDesigneLePremier() {
      ResultatsClasse r = calculer(List.of(new Resultats.Critere("GROUPE", "Pro")), Map.of());
      assertThat(r.eleve("a").rang()).isEqualTo(new Rang(1, true));
      assertThat(r.eleve("b").rang()).isEqualTo(new Rang(1, true));
      assertThat(r.eleve("c").rang()).isEqualTo(new Rang(1, true));
      assertThat(r.eleve("d").rang()).isEqualTo(new Rang(4, false));
      assertThat(designe(r).inscriptionId()).isEqualTo("a");
      // Sur le bulletin, le premier désigné porte « 1er » sans « ex » ; les autres gardent « 1er ex ».
      assertThat(r.eleve("a").rangBulletin()).isEqualTo(new Rang(1, false));
      assertThat(r.eleve("b").rangBulletin()).isEqualTo(new Rang(1, true));
    }

    @Test
    void puisLaDisciplinePuisLOrdreAlphabetique() {
      assertThat(designe(calculer(List.of(new Resultats.Critere("DISCIPLINE", null)), Map.of("a", 120, "b", 0, "c", 60))).inscriptionId()).isEqualTo("b");
      assertThat(designe(calculer(List.of(new Resultats.Critere("ALPHABETIQUE", null)), Map.of())).inscriptionId()).isEqualTo("b");
    }

    @Test
    void laRegleVautPourChaqueGroupeDExAequo() {
      // e a les notes de d : tous deux 4e ex ; par ordre alphabétique, d (ONANA) passe avant e (TCHOUA).
      List<NoteEntree> notes =
          List.of(
              n("a", "fr", 1, 10), n("a", "atelier", 1, 14), n("b", "fr", 1, 14), n("b", "atelier", 1, 10), n("c", "fr", 1, 12), n("c", "atelier", 1, 12),
              n("d", "fr", 1, 5), n("d", "atelier", 1, 5), n("e", "fr", 1, 5), n("e", "atelier", 1, 5));
      ResultatsClasse r =
          CalculResultats.calculer(
              new CalculResultats.Entrees(
                  Periode.S1, matieres, List.of("a", "b", "c", "d", "e"), notes, GRADES, COTES,
                  new Resultats.Regles(33.33, 66.67, List.of(new Resultats.Critere("ALPHABETIQUE", null)), null), Map.of(),
                  Map.of("a", "ZOA Paul", "b", "ABENA Luc", "c", "MBARGA Eve", "d", "ONANA Jo", "e", "TCHOUA Ida")));
      assertThat(r.eleve("d").rang()).isEqualTo(new Rang(4, true));
      assertThat(r.eleve("e").rang()).isEqualTo(new Rang(4, true));
      assertThat(r.eleve("d").rangBulletin()).isEqualTo(new Rang(4, false));
      assertThat(r.eleve("e").rangBulletin()).isEqualTo(new Rang(4, true));
      // Rangs par matière : d et e ont 5 en français, 4e ex ; d (ONANA) est désigné.
      assertThat(r.eleve("d").matieres().get(0).rang()).isEqualTo(new Rang(4, false));
      assertThat(r.eleve("e").matieres().get(0).rang()).isEqualTo(new Rang(4, true));
      // À la première place aussi : b (ABENA) est désigné.
      assertThat(r.eleve("b").rangBulletin()).isEqualTo(new Rang(1, false));
      assertThat(r.eleve("a").rangBulletin()).isEqualTo(new Rang(1, true));
    }

    @Test
    void sansCritereDecisifUnTirageAuSortStable() {
      ResultatsClasse r1 = calculer(List.of(new Resultats.Critere("DISCIPLINE", null)), Map.of());
      ResultatsClasse r2 = calculer(List.of(new Resultats.Critere("DISCIPLINE", null)), Map.of());
      assertThat(designe(r1).inscriptionId()).isEqualTo(designe(r2).inscriptionId());
    }
  }

  @Test
  void decisionDeFinDAnnee() {
    assertThat(CalculResultats.decision(10.0, 9.0)).isEqualTo(Resultats.Decision.ADMIS);
    assertThat(CalculResultats.decision(9.2, 9.0)).isEqualTo(Resultats.Decision.ADMIS_DELIBERATION);
    assertThat(CalculResultats.decision(8.99, 9.0)).isEqualTo(Resultats.Decision.REDOUBLE);
    assertThat(CalculResultats.decision(9.5, null)).isEqualTo(Resultats.Decision.REDOUBLE);
    assertThat(CalculResultats.decision(null, 9.0)).isNull();
  }

  @Test
  void repartitionDansLesTranchesVingtCompris() {
    List<Tranche> r = CalculResultats.repartition(List.of(0.0, 4.99, 5.0, 9.996, 10.0, 20.0));
    assertThat(r.stream().map(Tranche::effectif).toList()).containsExactly(2, 1, 0, 2, 0, 0, 1);
    assertThat(r.get(0).tranche()).isEqualTo("0–5");
    assertThat(r.get(6).tranche()).isEqualTo("16–20");
  }
}
