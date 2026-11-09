package cm.cetic.mezesse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sur Azure, un déploiement remplace app.jar, sur un partage réseau, plusieurs minutes avant de
 * redémarrer l'application. Le processus en place, privé de son jar, échoue alors sur toute classe
 * pas encore chargée : les connexions répondaient 500 pendant ce temps. L'application tourne donc
 * sur une copie locale au conteneur, que le déploiement ne touche pas. Si la copie échoue, le
 * démarrage se fait normalement, comme avant.
 */
public final class LancementDepuisCopie {

  /** Présente dans le processus lancé depuis la copie. */
  static final String MARQUE = "CETIC_LANCE_DEPUIS_COPIE";

  private LancementDepuisCopie() {}

  public static boolean actif() {
    return System.getenv(MARQUE) != null;
  }

  /** Ne rend la main que si l'application doit démarrer dans ce processus. */
  static void relancerSiDeploye(String[] args) {
    String jar = System.getProperty("java.class.path", "");
    if (actif() || !estDeploye(jar)) return;
    Path copie;
    Process processus;
    try {
      copie = Files.createTempFile("cetic-api-", ".jar");
      Files.copy(Path.of(jar), copie, StandardCopyOption.REPLACE_EXISTING);
      if (Files.size(copie) != Files.size(Path.of(jar))) throw new IOException("copie incomplète");
      ProcessBuilder lancement =
          new ProcessBuilder(commande(Path.of(System.getProperty("java.home"), "bin", "java").toString(), ManagementFactory.getRuntimeMXBean().getInputArguments(), copie.toString(), args));
      lancement.inheritIO();
      lancement.environment().put(MARQUE, "1");
      processus = lancement.start();
    } catch (IOException | RuntimeException e) {
      System.err.println("Lancement depuis une copie impossible, démarrage normal : " + e);
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> arreter(processus)));
    int code;
    try {
      code = processus.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      code = 1;
    }
    copie.toFile().delete();
    System.exit(code);
  }

  private static void arreter(Process processus) {
    processus.destroy();
    try {
      if (!processus.waitFor(20, TimeUnit.SECONDS)) processus.destroyForcibly();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      processus.destroyForcibly();
    }
  }

  static boolean estDeploye(String classpath) {
    return classpath.endsWith(".jar") && classpath.replace('\\', '/').contains("/site/wwwroot/");
  }

  static List<String> commande(String java, List<String> options, String copie, String[] args) {
    List<String> commande = new ArrayList<>();
    commande.add(java);
    commande.addAll(options);
    commande.add("-jar");
    commande.add(copie);
    commande.addAll(List.of(args));
    return commande;
  }
}
