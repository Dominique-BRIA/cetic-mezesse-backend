package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.entity.Sequence;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppelServiceTest {

  private static Sequence sequence(int numero, boolean ouverte) {
    Sequence s = new Sequence();
    s.setNumero(numero);
    s.setSaisieOuverte(ouverte);
    return s;
  }

  private static Renvoi renvoi(LocalDate le, LocalDate reintegre) {
    Renvoi r = new Renvoi();
    r.setDate(le);
    if (reintegre != null) r.setReintegreLe(reintegre.atTime(10, 0).toInstant(ZoneOffset.ofHours(1)));
    return r;
  }

  @Test
  void laSequenceProposeeEstLaDerniereOuverte() {
    assertThat(AppelService.sequenceProposee(List.of(sequence(1, false), sequence(2, true), sequence(3, true), sequence(4, false))).getNumero()).isEqualTo(3);
  }

  @Test
  void sansSequenceOuverteLaDerniere() {
    assertThat(AppelService.sequenceProposee(List.of(sequence(1, false), sequence(2, false))).getNumero()).isEqualTo(2);
    assertThat(AppelService.sequenceProposee(List.of())).isNull();
  }

  @Test
  void renvoyeEntreLeRenvoiEtLaReintegration() {
    List<Renvoi> r = List.of(renvoi(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13)));
    assertThat(AppelService.renvoyeLe(r, LocalDate.of(2026, 11, 9))).isFalse();
    assertThat(AppelService.renvoyeLe(r, LocalDate.of(2026, 11, 10))).isTrue();
    assertThat(AppelService.renvoyeLe(r, LocalDate.of(2026, 11, 12))).isTrue();
    // Réintégré le 13 : présent en classe ce jour-là, il peut être compté absent.
    assertThat(AppelService.renvoyeLe(r, LocalDate.of(2026, 11, 13))).isFalse();
  }

  @Test
  void renvoiEnCours() {
    assertThat(AppelService.renvoyeLe(List.of(renvoi(LocalDate.of(2026, 11, 10), null)), LocalDate.of(2027, 1, 5))).isTrue();
  }
}
