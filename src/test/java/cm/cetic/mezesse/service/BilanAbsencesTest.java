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

  // Paliers réglés par le SG (en minutes) : avertissement 6 h, blâme 10 h, exclusion 15 h.
  private static final List<Regle> REGLES =
      List.of(
          new Regle("Avertissement conduite", Sanction.AVERTISSEMENT, 360),
          new Regle("Blâme conduite", Sanction.BLAME, 600),
          new Regle("Exclusion", Sanction.EXCLUSION, 900));

  private static Retenue s(int minutes, int jours, String decision) {
    return BilanAbsences.sanction(minutes, REGLES, jours, decision);
  }

  @Test
  void seuleLaPlusEleveeEstRetenue() {
    assertThat(s(0, 0, null)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
    assertThat(s(359, 0, null)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
    assertThat(s(360, 0, null)).isEqualTo(new Retenue(Sanction.AVERTISSEMENT, "Avertissement conduite"));
    assertThat(s(700, 0, null)).isEqualTo(new Retenue(Sanction.BLAME, "Blâme conduite"));
  }

  @Test
  void heuresDExclusionSansDecisionDonnentLeBlame() {
    // L'exclusion n'est jamais automatique : en attendant la décision du Directeur, le blâme.
    assertThat(s(1000, 0, null)).isEqualTo(new Retenue(Sanction.BLAME, "Blâme conduite"));
    assertThat(BilanAbsences.sanction(1000, List.of(new Regle("Exclusion", Sanction.EXCLUSION, 900)), 0, null)).isEqualTo(new Retenue(Sanction.AUCUNE, null));
  }

  @Test
  void decisionsDuDirecteur() {
    assertThat(s(1000, 3, "TEMPORAIRE")).isEqualTo(new Retenue(Sanction.EXCLUSION, "Exclusion temporaire"));
    assertThat(s(1000, 0, "DEFINITIVE")).isEqualTo(new Retenue(Sanction.EXCLUSION, "Exclusion définitive"));
    assertThat(s(1000, 0, "SURSIS")).isEqualTo(new Retenue(Sanction.BLAME, "Blâme conduite (exclusion avec sursis)"));
    // Un renvoi prononcé directement (page Élèves renvoyés) compte comme une exclusion temporaire.
    assertThat(s(100, 2, null)).isEqualTo(new Retenue(Sanction.EXCLUSION, "Exclusion temporaire"));
    // Plusieurs décisions dans la période : la plus forte.
    assertThat(BilanAbsences.decisionRetenue(List.of("SURSIS", "TEMPORAIRE"))).isEqualTo("TEMPORAIRE");
    assertThat(BilanAbsences.decisionRetenue(List.of("TEMPORAIRE", "DEFINITIVE"))).isEqualTo("DEFINITIVE");
    assertThat(BilanAbsences.decisionRetenue(List.of())).isNull();
  }

  @Test
  void palierDExclusion() {
    assertThat(BilanAbsences.palierExclusion(REGLES)).isEqualTo(900);
    assertThat(BilanAbsences.palierExclusion(REGLES.subList(0, 2))).isNull();
  }

  @Test
  void repriseApresJoursOuvres() {
    // Exclusion de 3 jours à partir du jeudi 12/11/2026 : jeudi, vendredi, lundi ; retour le mardi 17.
    assertThat(ExclusionService.reprise(java.time.LocalDate.of(2026, 11, 12), 3)).isEqualTo(java.time.LocalDate.of(2026, 11, 17));
    // 1 jour le vendredi 13 : retour le lundi 16.
    assertThat(ExclusionService.reprise(java.time.LocalDate.of(2026, 11, 13), 1)).isEqualTo(java.time.LocalDate.of(2026, 11, 16));
    // 5 jours à partir du lundi 16 : retour le lundi 23.
    assertThat(ExclusionService.reprise(java.time.LocalDate.of(2026, 11, 16), 5)).isEqualTo(java.time.LocalDate.of(2026, 11, 23));
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
