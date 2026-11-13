package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.entity.EmploiDuTemps;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmploiDuTempsServiceTest {

  private static EmploiDuTemps version(String id, LocalDate du) {
    EmploiDuTemps e = new EmploiDuTemps();
    e.setId(id);
    e.setValableDu(du);
    return e;
  }

  private static LocalTime h(String s) {
    return LocalTime.parse(s);
  }

  @Test
  void laVersionEnVigueurEstLaDerniereCommencee() {
    List<EmploiDuTemps> versions =
        List.of(version("rentree", null), version("janvier", LocalDate.of(2027, 1, 4)), version("avril", LocalDate.of(2027, 4, 12)));
    assertThat(EmploiDuTempsService.enVigueur(versions, LocalDate.of(2026, 11, 13)).getId()).isEqualTo("rentree");
    assertThat(EmploiDuTempsService.enVigueur(versions, LocalDate.of(2027, 1, 4)).getId()).isEqualTo("janvier");
    assertThat(EmploiDuTempsService.enVigueur(versions, LocalDate.of(2027, 3, 1)).getId()).isEqualTo("janvier");
    assertThat(EmploiDuTempsService.enVigueur(versions, LocalDate.of(2027, 6, 1)).getId()).isEqualTo("avril");
  }

  @Test
  void aucuneVersionAvantLaPremiereDateDEffet() {
    List<EmploiDuTemps> versions = List.of(version("janvier", LocalDate.of(2027, 1, 4)));
    assertThat(EmploiDuTempsService.enVigueur(versions, LocalDate.of(2026, 12, 1))).isNull();
    assertThat(EmploiDuTempsService.enVigueur(List.of(), LocalDate.of(2026, 12, 1))).isNull();
  }

  @Test
  void deuxSeancesQuiSeSuiventNeSeChevauchentPas() {
    assertThat(EmploiDuTempsService.chevauchent(h("07:30"), h("08:25"), h("08:25"), h("09:20"))).isFalse();
    assertThat(EmploiDuTempsService.chevauchent(h("07:30"), h("08:25"), h("08:00"), h("09:00"))).isTrue();
    assertThat(EmploiDuTempsService.chevauchent(h("10:00"), h("11:30"), h("10:30"), h("11:00"))).isTrue();
    assertThat(EmploiDuTempsService.chevauchent(h("10:00"), h("11:30"), h("07:30"), h("08:25"))).isFalse();
  }

  @Test
  void libelleDuCreneau() {
    assertThat(EmploiDuTempsService.quand(1, h("07:30"), h("08:25"))).isEqualTo("le lundi de 07:30 à 08:25");
  }
}
