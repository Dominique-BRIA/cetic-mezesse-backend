package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;

import cm.cetic.mezesse.config.CeticProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Choix du stockage des photos : Backblaze B2 en production, dossier local sinon. */
class StockageServiceTest {

  private static CeticProperties proprietes(CeticProperties.B2 b2, String dossier) {
    var session = new CeticProperties.Session(Duration.ofHours(12), Duration.ofDays(90), Duration.ofHours(1));
    return new CeticProperties("secret", session, "", "password", dossier, b2, null);
  }

  @Test
  void clientB2ConstruitAvecLeClientHttpSynchrone() {
    var b2 = new CeticProperties.B2("https://s3.eu-central-003.backblazeb2.com", "id", "cle", "photos");
    assertThat(new StockageService(proprietes(b2, null)).distant()).isTrue();
  }

  @Test
  void dossierLocalSansB2(@TempDir Path dossier) throws Exception {
    StockageService s = new StockageService(proprietes(new CeticProperties.B2("", "", "", ""), dossier.toString()));
    assertThat(s.distant()).isFalse();
    s.deposer("eleves/a.png", new byte[] {1, 2, 3}, "image/png");
    assertThat(Files.readAllBytes(dossier.resolve("eleves/a.png"))).containsExactly(1, 2, 3);
    assertThat(s.lire("eleves/a.png")).containsExactly(1, 2, 3);
    assertThat(s.lire("eleves/absente.png")).isNull();
    s.supprimer("eleves/a.png");
    assertThat(s.lire("eleves/a.png")).isNull();
  }
}
