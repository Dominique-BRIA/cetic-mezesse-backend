package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.service.BilanAbsences.Regle;
import cm.cetic.mezesse.service.BilanAbsences.Retenue;
import cm.cetic.mezesse.service.BilanAbsences.Sanction;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class BilanAbsencesTest {

  // Sanctions réglées par le SG (en minutes) : mise en garde 4 h, avertissement 10 h, blâme 20 h,
  // exclusion 30 h, conseil de discipline 40 h (ligne d'exclusion aussi).
  private static final List<Regle> REGLES =
      List.of(
          new Regle("Mise en garde", Sanction.AUCUNE, 240),
          new Regle("Avertissement", Sanction.AVERTISSEMENT, 600),
          new Regle("Blâme", Sanction.BLAME, 1200),
          new Regle("Exclusion 3 jours", Sanction.EXCLUSION, 1800),
          new Regle("Conseil de discipline", Sanction.EXCLUSION, 2400));

  private static Retenue s(int minutes, int jours) {
    return BilanAbsences.sanction(minutes, REGLES, jours);
  }

  @Test
  void seuleLaPlusEleveeEstRetenue() {
    assertThat(s(0, 0)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
    assertThat(s(239, 0)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
    assertThat(s(240, 0)).isEqualTo(new Retenue(Sanction.AUCUNE, "Mise en garde"));
    assertThat(s(600, 0)).isEqualTo(new Retenue(Sanction.AVERTISSEMENT, "Avertissement"));
    assertThat(s(1250, 0)).isEqualTo(new Retenue(Sanction.BLAME, "Blâme"));
  }

  @Test
  void exclusionSansRenvoiDonneLaPlusEleveeDesAutres() {
    assertThat(s(2000, 0)).isEqualTo(new Retenue(Sanction.BLAME, "Blâme"));
    assertThat(s(5000, 0)).isEqualTo(new Retenue(Sanction.BLAME, "Blâme"));
    // Sans autre sanction atteinte : rien.
    assertThat(BilanAbsences.sanction(2000, List.of(new Regle("Exclusion", Sanction.EXCLUSION, 1800)), 0)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
  }

  @Test
  void leRenvoiDuProviseurLEmporte() {
    assertThat(s(2000, 3)).isEqualTo(new Retenue(Sanction.EXCLUSION, "Exclusion 3 jours"));
    assertThat(s(2500, 8)).isEqualTo(new Retenue(Sanction.EXCLUSION, "Conseil de discipline"));
    // Renvoi prononcé avant tout palier d'exclusion.
    assertThat(s(100, 2)).isEqualTo(new Retenue(Sanction.EXCLUSION, "Exclusion"));
  }

  @Test
  void premierPalierDExclusion() {
    assertThat(BilanAbsences.palierExclusion(REGLES)).isEqualTo(1800);
    assertThat(BilanAbsences.palierExclusion(REGLES.subList(0, 3))).isNull();
  }

  @Test
  void aucuneSanctionReglee() {
    assertThat(BilanAbsences.sanction(5000, List.of(), 0)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
  }

  @Test
  void dureesEnHeuresEtMinutes() {
    // 55 + 60 + 90 = 205 minutes = 3 h 25.
    assertThat(BilanAbsences.duree(55 + 60 + 90)).isEqualTo("3 h 25");
    assertThat(BilanAbsences.duree(55)).isEqualTo("55 min");
    assertThat(BilanAbsences.duree(120)).isEqualTo("2 h");
    assertThat(BilanAbsences.duree(0)).isEqualTo("0 h");
  }

  @Test
  void joursDExclusionDansLaPeriode() {
    Renvoi r = new Renvoi();
    r.setDate(LocalDate.of(2026, 11, 10));
    r.setReintegreLe(LocalDate.of(2026, 11, 13).atTime(9, 0).toInstant(ZoneOffset.ofHours(1)));
    assertThat(BilanAbsences.joursDansPeriode(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 15))).isEqualTo(3);
    // Période qui s'arrête le 11 : les 10 et 11 seulement.
    assertThat(BilanAbsences.joursDansPeriode(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 11))).isEqualTo(2);
    // Renvoi après la période : rien.
    assertThat(BilanAbsences.joursDansPeriode(r, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1))).isZero();
  }
}
