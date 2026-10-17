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
      // b : séquence 2 évaluée (a y a une note) donc 0
      assertThat(r.eleves().get(1).matieres().get(0).note()).isEqualTo(4.0);
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

  @Test
  void repartitionDansLesTranchesVingtCompris() {
    List<Tranche> r = CalculResultats.repartition(List.of(0.0, 4.99, 5.0, 9.996, 10.0, 20.0));
    assertThat(r.stream().map(Tranche::effectif).toList()).containsExactly(2, 1, 0, 2, 0, 0, 1);
    assertThat(r.get(0).tranche()).isEqualTo("0–5");
    assertThat(r.get(6).tranche()).isEqualTo("16–20");
  }
}
