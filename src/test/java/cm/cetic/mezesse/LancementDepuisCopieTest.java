package cm.cetic.mezesse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Exécution depuis une copie du jar, à l'abri des déploiements. */
class LancementDepuisCopieTest {

  @Test
  void seul_le_jar_deploye_sur_azure_est_concerne() {
    assertThat(LancementDepuisCopie.estDeploye("/home/site/wwwroot/app.jar")).isTrue();
    assertThat(LancementDepuisCopie.estDeploye("D:\\home\\site\\wwwroot\\app.jar")).isTrue();
    assertThat(LancementDepuisCopie.estDeploye("D:/Dev/cetic-mezesse-backend/target/cetic-api.jar")).isFalse();
    assertThat(LancementDepuisCopie.estDeploye("/home/site/wwwroot/classes:/home/site/wwwroot/lib")).isFalse();
    assertThat(LancementDepuisCopie.estDeploye("")).isFalse();
  }

  @Test
  void la_commande_garde_les_options_et_les_arguments() {
    assertThat(LancementDepuisCopie.commande("/usr/bin/java", List.of("-Xmx512m"), "/tmp/c.jar", new String[] {"--server.port=80"}))
        .containsExactly("/usr/bin/java", "-Xmx512m", "-jar", "/tmp/c.jar", "--server.port=80");
  }
}
