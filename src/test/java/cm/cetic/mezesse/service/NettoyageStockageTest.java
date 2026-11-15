package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NettoyageStockageTest {

  private static final Instant MAINTENANT = Instant.parse("2026-11-15T01:30:00Z");

  private static StockageService.Objet fichier(String cle, Duration age) {
    return new StockageService.Objet(cle, MAINTENANT.minus(age));
  }

  @Test
  void unePhotoQuiNeSertPlusPart() {
    assertThat(NettoyageStockage.aSupprimer(fichier("eleves/a-1.jpg", Duration.ofDays(40)), Set.of(), MAINTENANT)).isTrue();
  }

  @Test
  void unePhotoUtiliseeOuCiteeResteAuStockage() {
    Set<String> gardees = Set.of("eleves/a-1.jpg");
    assertThat(NettoyageStockage.aSupprimer(fichier("eleves/a-1.jpg", Duration.ofDays(400)), gardees, MAINTENANT)).isFalse();
  }

  @Test
  void unFichierDeposeIlYAMoinsDeDeuxJoursReste() {
    assertThat(NettoyageStockage.aSupprimer(fichier("justificatifs/x.pdf", Duration.ofHours(30)), Set.of(), MAINTENANT)).isFalse();
    assertThat(NettoyageStockage.aSupprimer(fichier("justificatifs/x.pdf", Duration.ofDays(3)), Set.of(), MAINTENANT)).isTrue();
  }

  @Test
  void lesClesCiteesParUneDecisionSontRetrouvees() {
    String donnees = "{\"eleveId\": \"e1\", \"avantCle\": \"eleves/e1-1731.jpg\", \"apresCle\": null, \"pieces\": [\"justificatifs/3f2a.pdf\"]}";
    assertThat(NettoyageStockage.citees(donnees)).containsExactlyInAnyOrder("eleves/e1-1731.jpg", "justificatifs/3f2a.pdf");
    assertThat(NettoyageStockage.citees(null)).isEmpty();
  }

  @Test
  void leGardeFouBloquePlusDeLaMoitieDesFichiers() {
    assertThat(NettoyageStockage.bloquer(60, 100)).isTrue();
    assertThat(NettoyageStockage.bloquer(40, 100)).isFalse();
    // Peu de fichiers : pas de blocage (débuts de l'année, essais).
    assertThat(NettoyageStockage.bloquer(5, 6)).isFalse();
  }
}
