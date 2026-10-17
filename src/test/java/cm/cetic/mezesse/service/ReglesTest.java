package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.config.EnvironnementLocal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Noms des classes (RG-29), délai de désapprobation (RG-30) et petites règles transverses. */
class ReglesTest {

  @Test
  void niveauEnOrdinal() {
    assertThat(NomsClasses.ordinal(1)).isEqualTo("1re");
    assertThat(NomsClasses.ordinal(2)).isEqualTo("2e");
  }

  @Test
  void pasDeLettreQuandLaClasseEstSeule() {
    assertThat(NomsClasses.noms(1, "Mécanique", "Mechanics", 1)).containsExactly(new NomsClasses.Nom("1re année Mécanique", "Year 1 Mechanics"));
  }

  @Test
  void lettresQuandPlusieursClasses() {
    assertThat(NomsClasses.noms(1, "Mécanique", "Mechanics", 3).stream().map(NomsClasses.Nom::nom))
        .containsExactly("1re année Mécanique A", "1re année Mécanique B", "1re année Mécanique C");
  }

  @Test
  void nomFrancaisQuandIlNyAPasDeNomAnglais() {
    assertThat(NomsClasses.noms(2, "ELEC", null, 2).get(1)).isEqualTo(new NomsClasses.Nom("2e année ELEC B", "Year 2 ELEC B"));
  }

  @Test
  void classeSansSpecialite() {
    assertThat(NomsClasses.noms(3, null, null, 1).get(0).nom()).isEqualTo("3e année");
    assertThat(NomsClasses.noms(3, null, null, 2).get(1).nom()).isEqualTo("3e année B");
  }

  @Test
  void chiffresAuDelaDeZ() {
    assertThat(NomsClasses.lettre(25)).isEqualTo("Z");
    assertThat(NomsClasses.lettre(26)).isEqualTo("27");
  }

  @Test
  void delaiDeDesapprobationDeQuatreSemaines() {
    Instant creeLe = Instant.parse("2026-10-01T08:00:00Z");
    assertThat(JournalDecisions.DELAI_DESAPPROBATION_JOURS).isEqualTo(28);
    assertThat(JournalDecisions.limiteDesapprobation(creeLe)).isEqualTo(Instant.parse("2026-10-29T08:00:00Z"));
    assertThat(JournalDecisions.dansLeDelai(creeLe, Instant.parse("2026-10-15T00:00:00Z"))).isTrue();
    assertThat(JournalDecisions.dansLeDelai(creeLe, Instant.parse("2026-10-29T08:00:00Z"))).isTrue();
    assertThat(JournalDecisions.dansLeDelai(creeLe, Instant.parse("2026-10-29T08:00:01Z"))).isFalse();
  }

  @Test
  void anneeScolaireCommenceEnSeptembre() {
    assertThat(AmorcageService.anneeCourante(LocalDate.of(2026, 10, 17))).isEqualTo("2026-2027");
    assertThat(AmorcageService.anneeCourante(LocalDate.of(2027, 3, 1))).isEqualTo("2026-2027");
  }

  @Test
  void annulationNeRemetQueLesChampsInchangesDepuis() {
    Map<String, Object> avant = new LinkedHashMap<>(Map.of("nom", "A", "coefficient", 2, "dateNaissance", "2011-05-26T00:00:00.000Z"));
    Map<String, Object> apres = new LinkedHashMap<>(Map.of("nom", "B", "coefficient", 3, "dateNaissance", "2011-05-27"));
    // nom modifié depuis par une autre décision : il n'est pas écrasé ; dates et nombres comparés par valeur
    Map<String, Object> actuel = new LinkedHashMap<>(Map.of("nom", "C", "coefficient", 3L, "dateNaissance", LocalDate.of(2011, 5, 27)));
    Map<String, Object> r = AnnulationService.aRestaurer(actuel, avant, apres);
    assertThat(r).containsOnlyKeys("coefficient", "dateNaissance");
  }

  @Test
  void urlPrismaConvertieEnJdbc() throws Exception {
    var methode = Class.forName("cm.cetic.mezesse.config.EnvironnementLocal$Connexion").getDeclaredMethod("depuis", String.class);
    methode.setAccessible(true);
    Object c = methode.invoke(null, "postgresql://moi:mot%40passe@ep-abc-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require&schema=public&channel_binding=require");
    var url = c.getClass().getDeclaredMethod("url");
    var motDePasse = c.getClass().getDeclaredMethod("motDePasse");
    url.setAccessible(true);
    motDePasse.setAccessible(true);
    assertThat(url.invoke(c)).isEqualTo("jdbc:postgresql://ep-abc-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require&prepareThreshold=0");
    assertThat(motDePasse.invoke(c)).isEqualTo("mot@passe");
    assertThat(List.of(EnvironnementLocal.class)).isNotEmpty();
  }
}
