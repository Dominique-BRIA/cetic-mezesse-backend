package cm.cetic.mezesse.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Numéro d'ordre de chaque élève dans sa classe : 1, 2, 3… dans l'ordre alphabétique (nom, prénoms), élèves
 * renvoyés compris pour que les numéros ne bougent pas en cours d'année. C'est souvent ce numéro qu'on relève
 * en classe pour l'appel ; il est le même sur tous les écrans qui listent des élèves.
 */
@Service
@RequiredArgsConstructor
public class NumerosEleves {

  private final NamedParameterJdbcTemplate sql;

  /** Numéro de chaque inscription des classes données (clé : id de l'inscription). */
  public Map<String, Integer> deClasses(Collection<String> classeIds) {
    Map<String, Integer> r = new HashMap<>();
    if (classeIds.isEmpty()) return r;
    sql.query(
        """
        SELECT i.id, ROW_NUMBER() OVER (PARTITION BY i."classeId" ORDER BY e.nom, e.prenoms, e.id) AS n
        FROM "Inscription" i JOIN "Eleve" e ON e.id = i."eleveId"
        WHERE i."classeId" IN (:classes)
        """,
        Map.of("classes", classeIds),
        rs -> {
          r.put(rs.getString("id"), rs.getInt("n"));
        });
    return r;
  }
}
