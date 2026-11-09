package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Regroupement des plantages de même origine. */
class AppareilServiceTest {

  static final String PILE_A =
      """
      java.lang.RuntimeException: Unable to get provider
      \tat android.app.ActivityThread.installProvider(ActivityThread.java:9201)
      \tat s0.a.b(r8-map-id-7e213461fa1eed22eb21d813619f90b7f72d0a5afb5e40b108e8d7993df05189:105)
      """;

  @Test
  void meme_plantage_malgre_les_numeros_de_ligne_et_la_table_r8() {
    String autre = PILE_A.replace("9201", "9344").replace("7e213461fa1eed22", "aa11bb22cc33dd44").replace(":105", ":98");
    assertThat(AppareilService.empreinte("NATIF", "m", autre)).isEqualTo(AppareilService.empreinte("NATIF", "m", PILE_A));
  }

  @Test
  void piles_differentes_ou_types_differents_separes() {
    String e = AppareilService.empreinte("NATIF", "m", PILE_A);
    assertThat(AppareilService.empreinte("NATIF", "m", PILE_A.replace("installProvider", "handleBindApplication"))).isNotEqualTo(e);
    assertThat(AppareilService.empreinte("DART", "m", PILE_A)).isNotEqualTo(e);
  }

  @Test
  void sans_pile_le_message_sert_d_empreinte() {
    assertThat(AppareilService.empreinte("DART", "erreur A", null)).isNotEqualTo(AppareilService.empreinte("DART", "erreur B", " "));
    assertThat(AppareilService.empreinte("DART", "erreur 12", null)).isEqualTo(AppareilService.empreinte("DART", "erreur 345", ""));
  }
}
