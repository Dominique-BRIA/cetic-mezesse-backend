package cm.cetic.mezesse.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Construction de petits objets JSON (valeurs nulles admises, ordre conservé). */
public final class Donnees {

  private Donnees() {}

  public static Map<String, Object> objet(Object... clesValeurs) {
    if (clesValeurs.length % 2 != 0) throw new IllegalArgumentException("Nombre d'arguments impair");
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < clesValeurs.length; i += 2) m.put((String) clesValeurs[i], clesValeurs[i + 1]);
    return m;
  }
}
