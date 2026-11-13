package cm.cetic.mezesse.util;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.util.Ressemblance.Force;
import org.junit.jupiter.api.Test;

/** Repérage des doublons probables : présentation ignorée, fautes de frappe tolérées, homonymes distincts. */
class RessemblanceTest {

  @Test
  void la_presentation_ne_compte_pas() {
    assertThat(Ressemblance.comparer("EBO'O NKOU Astride", "Eboo Nkou astride")).isEqualTo(Force.IDENTIQUE);
    assertThat(Ressemblance.comparer("ZANGA Therence Zidane", "Therence Zidane  ZANGA")).isEqualTo(Force.IDENTIQUE);
    assertThat(Ressemblance.comparer("Éléonore ÉTOA", "eleonore etoa")).isEqualTo(Force.IDENTIQUE);
  }

  @Test
  void une_faute_de_frappe_reste_proche() {
    assertThat(Ressemblance.comparer("ZANG Steve", "ZANG Steeve")).isEqualTo(Force.PROCHE);
    assertThat(Ressemblance.comparer("Anglais", "Anglaise")).isEqualTo(Force.PROCHE);
    assertThat(Ressemblance.comparer("MENYENGUE NGONG Franck Darrell", "MENYENGUE NGONG Franck Darel")).isEqualTo(Force.PROCHE);
  }

  @Test
  void un_nom_complet_qui_en_contient_un_autre_est_proche() {
    assertThat(Ressemblance.comparer("ZANGA Therence", "ZANGA Therence Zidane")).isEqualTo(Force.PROCHE);
  }

  @Test
  void des_noms_differents_ne_se_ressemblent_pas() {
    assertThat(Ressemblance.comparer("MBALLA Ange", "MBARGA Ange")).isNull();
    assertThat(Ressemblance.comparer("NGONO Marie Claire", "NKOULOU Marie Claire")).isNull();
    assertThat(Ressemblance.comparer("EPS", "TP")).isNull();
    assertThat(Ressemblance.comparer("Mathématiques", "Sciences physiques")).isNull();
    assertThat(Ressemblance.comparer("", "ZANG")).isNull();
  }

  @Test
  void un_seul_mot_commun_ne_suffit_pas() {
    assertThat(Ressemblance.comparer("ZANG Steve", "ZANG Aurélie")).isNull();
    assertThat(Ressemblance.comparer("Anglais", "Anglais technique")).isNull();
  }
}
