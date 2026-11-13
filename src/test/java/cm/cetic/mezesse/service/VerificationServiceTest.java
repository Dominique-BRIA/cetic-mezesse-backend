package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Repérage des paires de doublons sur la page « À vérifier ». */
class VerificationServiceTest {

  @Test
  void trouve_les_paires_qui_se_ressemblent_et_elles_seules() {
    List<String> noms = List.of("Therence Zidane ZANGA", "Paul MBARGA", "Therence Zidane ZANGA", "Ange MBALLA", "Steve ZANG", "Steeve ZANG", "Ange MBARGA");
    assertThat(VerificationService.paires(noms)).extracting(p -> p[0] + "-" + p[1]).containsExactly("0-2", "4-5");
  }

  @Test
  void une_paire_n_est_signalee_qu_une_fois_meme_avec_plusieurs_mots_communs() {
    assertThat(VerificationService.paires(List.of("Marie Claire NGONO", "Marie Claire NGONO"))).hasSize(1);
  }
}
