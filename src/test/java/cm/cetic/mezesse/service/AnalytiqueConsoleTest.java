package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.dto.ConsoleDto.JourAnalytique;
import cm.cetic.mezesse.dto.ConsoleDto.Part;
import cm.cetic.mezesse.repository.HistoriqueConsoleRepository.ActiviteLigne;
import cm.cetic.mezesse.repository.HistoriqueConsoleRepository.PlantageLigne;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Courbes de la console technique. */
class AnalytiqueConsoleTest {

  static final LocalDate FIN = LocalDate.of(2026, 11, 9);

  @Test
  void la_serie_couvre_chaque_jour_meme_sans_activite() {
    List<JourAnalytique> s = ConsoleService.serie(FIN, 7, List.of(), List.of(), List.of());
    assertThat(s).hasSize(7);
    assertThat(s.get(0).jour()).isEqualTo(LocalDate.of(2026, 11, 3));
    assertThat(s.get(6).jour()).isEqualTo(FIN);
    assertThat(s).allSatisfy(j -> assertThat(j.actifs() + j.nouveaux()).isZero());
  }

  @Test
  void additionne_les_versions_et_les_types_du_meme_jour() {
    List<JourAnalytique> s =
        ConsoleService.serie(
            FIN,
            2,
            List.of(new ActiviteLigne(FIN, 6, 3), new ActiviteLigne(FIN, 7, 2)),
            List.of(new PlantageLigne(FIN, "NATIF", 4), new PlantageLigne(FIN, "DART", 1)),
            List.of(FIN, FIN, FIN.minusDays(1)));
    assertThat(s.get(0).nouveaux()).isEqualTo(1);
    JourAnalytique j = s.get(1);
    assertThat(j.actifs()).isEqualTo(5);
    assertThat(j.nouveaux()).isEqualTo(2);
    assertThat(j.parVersion()).isEqualTo(Map.of(6, 3L, 7, 2L));
    assertThat(j.plantages()).isEqualTo(Map.of("NATIF", 4L, "DART", 1L));
  }

  @Test
  void ignore_ce_qui_sort_de_la_periode() {
    List<JourAnalytique> s = ConsoleService.serie(FIN, 2, List.of(new ActiviteLigne(FIN.minusDays(5), 6, 9)), List.of(new PlantageLigne(FIN.minusDays(5), "DART", 9)), List.of(FIN.minusDays(5)));
    assertThat(s).allSatisfy(j -> assertThat(j.actifs() + j.nouveaux() + j.plantages().size()).isZero());
  }

  @Test
  void regroupe_les_petites_parts_et_nomme_les_valeurs_manquantes() {
    assertThat(ConsoleService.parts(Stream.of("A", "A", "B", null, " "))).containsExactly(new Part("A", 2), new Part("Inconnu", 2), new Part("B", 1));
    List<Part> p = ConsoleService.parts(Stream.of("a", "b", "c", "d", "e", "f", "g", "h"));
    assertThat(p).hasSize(7);
    assertThat(p.get(6)).isEqualTo(new Part("Autres", 2));
  }
}
