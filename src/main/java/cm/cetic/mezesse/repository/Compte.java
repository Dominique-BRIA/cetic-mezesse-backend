package cm.cetic.mezesse.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Résultat d'un « count … group by » : clé (un identifiant) et nombre de lignes. */
public record Compte(String cle, long nombre) {

  public static Map<String, Long> enMap(Collection<Compte> comptes) {
    Map<String, Long> m = new HashMap<>();
    for (Compte c : comptes) m.put(c.cle(), c.nombre());
    return m;
  }
}
