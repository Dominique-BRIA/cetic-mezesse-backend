package cm.cetic.mezesse.repository;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Compteurs quotidiens de la console technique. Gardés 190 jours : de quoi comparer 90 jours aux 90 précédents. */
@Repository
@RequiredArgsConstructor
public class HistoriqueConsoleRepository {

  private static final int CONSERVATION_JOURS = 190;

  public record ActiviteLigne(LocalDate jour, int numeroVersion, long appareils) {}

  public record PlantageLigne(LocalDate jour, String type, long occurrences) {}

  private final JdbcTemplate jdbc;

  public void noterActivite(LocalDate jour, String installationId, int numeroVersion) {
    jdbc.update(
        """
        INSERT INTO "ActiviteJour" ("jour", "installationId", "numeroVersion") VALUES (?, ?, ?)
        ON CONFLICT ("jour", "installationId") DO UPDATE SET "numeroVersion" = EXCLUDED."numeroVersion"
        """,
        jour,
        installationId,
        numeroVersion);
    jdbc.update("DELETE FROM \"ActiviteJour\" WHERE \"jour\" < ?", jour.minusDays(CONSERVATION_JOURS));
  }

  public void noterPlantage(LocalDate jour, String type, int numeroVersion) {
    jdbc.update(
        """
        INSERT INTO "PlantageJour" ("jour", "type", "numeroVersion") VALUES (?, ?, ?)
        ON CONFLICT ("jour", "type", "numeroVersion") DO UPDATE SET "occurrences" = "PlantageJour"."occurrences" + 1
        """,
        jour,
        type,
        numeroVersion);
    jdbc.update("DELETE FROM \"PlantageJour\" WHERE \"jour\" < ?", jour.minusDays(CONSERVATION_JOURS));
  }

  public List<ActiviteLigne> activite(LocalDate depuis) {
    return jdbc.query(
        "SELECT \"jour\", \"numeroVersion\", COUNT(*) AS n FROM \"ActiviteJour\" WHERE \"jour\" >= ? GROUP BY \"jour\", \"numeroVersion\"",
        (r, i) -> new ActiviteLigne(r.getObject("jour", LocalDate.class), r.getInt("numeroVersion"), r.getLong("n")),
        depuis);
  }

  public List<PlantageLigne> plantages(LocalDate depuis) {
    return jdbc.query(
        "SELECT \"jour\", \"type\", SUM(\"occurrences\") AS n FROM \"PlantageJour\" WHERE \"jour\" >= ? GROUP BY \"jour\", \"type\"",
        (r, i) -> new PlantageLigne(r.getObject("jour", LocalDate.class), r.getString("type"), r.getLong("n")),
        depuis);
  }

  /** Téléphones différents actifs entre deux dates, incluses. */
  public long appareilsActifs(LocalDate de, LocalDate a) {
    Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT \"installationId\") FROM \"ActiviteJour\" WHERE \"jour\" BETWEEN ? AND ?", Long.class, de, a);
    return n == null ? 0 : n;
  }
}
