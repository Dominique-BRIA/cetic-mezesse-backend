package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.EnteteOfficiel;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Renvoi;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.repository.SanctionAbsenceRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.RenvoiRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.service.calcul.Periode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Heures d'absence, calculées à la demande depuis les absences (aucun total stocké) : par élève et par
 * période (séquence, trimestre, année), en minutes. Total = justifiées + non justifiées ; une séance annulée
 * ou une présence ne compte jamais. Les sanctions se décident sur les heures non justifiées cumulées depuis
 * la rentrée, jusqu'à la fin de la période.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BilanAbsences {

  public enum Sanction {
    AUCUNE, AVERTISSEMENT, BLAME, EXCLUSION
  }

  /** Une période pour un élève : séances manquées, minutes totales, justifiées, non justifiées. */
  public record Heures(int seances, int minutes, int justifiees, int nonJustifiees) {
    static final Heures RIEN = new Heures(0, 0, 0, 0);
  }

  /** Sanction réglée par le SG : nom, ligne du bulletin, à partir de combien de minutes (incluses). */
  public record Regle(String libelle, Sanction ligne, int minutes) {}

  /** Sanction retenue : ligne du bulletin et nom choisi par le SG (vide : aucune). */
  public record Retenue(Sanction ligne, String libelle) {
    static final Retenue AUCUNE = new Retenue(Sanction.AUCUNE, null);
  }

  /** Bloc « Discipline » d'un élève pour une période. */
  public record Discipline(Heures periode, int cumulNonJustifiees, Retenue retenue, int joursExclusion, boolean paliersExclusionAtteint) {
    public Sanction sanction() {
      return retenue.ligne();
    }
  }

  private final NamedParameterJdbcTemplate sql;
  private final InscriptionRepository inscriptions;
  private final RenvoiRepository renvois;
  private final SanctionAbsenceRepository sanctions;
  private final UtilisateurRepository utilisateurs;
  private final NotificationService notifications;

  // ---------- Règles ----------

  /**
   * Sanction de la période : un renvoi prononcé par le Proviseur l'emporte (exclusion) ; sinon la plus élevée
   * atteinte parmi celles réglées par le SG. Une sanction d'exclusion n'est jamais automatique : sans renvoi,
   * l'élève prend la plus élevée des autres sanctions atteintes.
   */
  static Retenue sanction(int cumulNonJustifiees, List<Regle> regles, int joursExclusion) {
    List<Regle> atteintes = regles.stream().filter(r -> cumulNonJustifiees >= r.minutes()).sorted(Comparator.comparingInt(Regle::minutes).reversed()).toList();
    if (joursExclusion > 0) {
      String nom = atteintes.stream().filter(r -> r.ligne() == Sanction.EXCLUSION).map(Regle::libelle).findFirst().orElse("Exclusion");
      return new Retenue(Sanction.EXCLUSION, nom);
    }
    return atteintes.stream().filter(r -> r.ligne() != Sanction.EXCLUSION).findFirst().map(r -> new Retenue(r.ligne(), r.libelle())).orElse(Retenue.AUCUNE);
  }

  /** Premier palier d'exclusion (le Proviseur est prévenu à partir de là) ; vide : aucune sanction d'exclusion. */
  static Integer palierExclusion(List<Regle> regles) {
    return regles.stream().filter(r -> r.ligne() == Sanction.EXCLUSION).map(Regle::minutes).min(Integer::compare).orElse(null);
  }

  /** Sanctions réglées par le SG, de la plus faible à la plus forte. */
  public List<Regle> regles() {
    return sanctions.findAllByOrderByMinutesAsc().stream().map(s -> new Regle(s.getLibelle(), s.getLigne(), s.getMinutes())).toList();
  }

  /** Jours d'exclusion d'un renvoi compris entre deux dates (la réintégration met fin à l'exclusion). */
  static int joursDansPeriode(Renvoi r, LocalDate du, LocalDate au) {
    LocalDate debut = r.getDate().isBefore(du) ? du : r.getDate();
    LocalDate reintegration = r.getReintegreLe() == null ? null : r.getReintegreLe().atZone(EnteteOfficiel.CAMEROUN).toLocalDate();
    LocalDate fin = reintegration == null ? au.plusDays(1) : (reintegration.isAfter(au.plusDays(1)) ? au.plusDays(1) : reintegration);
    return (int) Math.max(0, ChronoUnit.DAYS.between(debut, fin));
  }

  /** « 5 h 30 », « 55 min », « 0 h ». */
  public static String duree(int minutes) {
    if (minutes <= 0) return "0 h";
    int h = minutes / 60;
    int m = minutes % 60;
    if (h == 0) return m + " min";
    return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
  }

  // ---------- Calculs ----------

  private static final String DUREE = "(EXTRACT(EPOCH FROM (s.fin - s.debut)) / 60)::int";

  /** Heures de chaque inscription sur les séquences données (numéros), toutes classes confondues. */
  public Map<String, Heures> heures(Collection<String> inscriptionIds, Collection<Integer> sequences) {
    if (inscriptionIds.isEmpty()) return Map.of();
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("ins", inscriptionIds).addValue("seqs", sequences);
    Map<String, Heures> r = new HashMap<>();
    sql.query(
        """
        SELECT a."inscriptionId" AS i, COUNT(*) AS n, SUM("""
            + DUREE
            + """
            ) AS total,
              COALESCE(SUM(CASE WHEN j.id IS NOT NULL THEN """
            + DUREE
            + """
             END), 0) AS justifiees
            FROM "Absence" a
            JOIN "Seance" s ON s.id = a."seanceId"
            JOIN "FicheAppel" f ON f.id = s."ficheId"
            JOIN "Sequence" q ON q.id = f."sequenceId"
            LEFT JOIN "Justification" j ON j."absenceId" = a.id
            WHERE a."inscriptionId" IN (:ins) AND q.numero IN (:seqs)
            GROUP BY a."inscriptionId"
            """,
        p,
        rs -> {
          int total = rs.getInt("total");
          int justifiees = rs.getInt("justifiees");
          r.put(rs.getString("i"), new Heures(rs.getInt("n"), total, justifiees, total - justifiees));
        });
    return r;
  }

  /** Dates du premier et du dernier appel d'une classe sur ces séquences (null s'il n'y en a pas). */
  private LocalDate[] bornes(String classeId, Collection<Integer> sequences) {
    MapSqlParameterSource p = new MapSqlParameterSource().addValue("c", classeId).addValue("seqs", sequences);
    Map<String, Object> m =
        sql.queryForMap(
            """
            SELECT MIN(f.date) AS du, MAX(f.date) AS au FROM "FicheAppel" f JOIN "Sequence" q ON q.id = f."sequenceId"
            WHERE f."classeId" = :c AND q.numero IN (:seqs)
            """,
            p);
    if (m.get("du") == null) return null;
    return new LocalDate[] {((java.sql.Date) m.get("du")).toLocalDate(), ((java.sql.Date) m.get("au")).toLocalDate()};
  }

  /** La classe a-t-elle au moins un appel enregistré sur la période ? Sinon le bulletin reste vide (rien n'est inventé). */
  public boolean aDesAppels(String classeId, Periode periode) {
    return bornes(classeId, periode.sequences()) != null;
  }

  /** Bloc « Discipline » de chaque élève de la classe pour la période (bulletins, bilan). */
  public Map<String, Discipline> discipline(String classeId, Periode periode) {
    List<Inscription> liste = inscriptions.deLaClasse(classeId);
    List<String> ids = liste.stream().map(Inscription::getId).toList();
    List<Integer> seqs = periode.sequences();
    int derniere = seqs.stream().mapToInt(Integer::intValue).max().orElse(6);
    List<Integer> depuisRentree = new ArrayList<>();
    for (int k = 1; k <= derniere; k++) depuisRentree.add(k);
    Map<String, Heures> dansPeriode = heures(ids, seqs);
    Map<String, Heures> cumul = heures(ids, depuisRentree);
    LocalDate[] bornes = bornes(classeId, seqs);
    LocalDate du = bornes == null ? LocalDate.of(2000, 1, 1) : bornes[0];
    LocalDate au = bornes == null ? LocalDate.now(EnteteOfficiel.CAMEROUN) : bornes[1];
    Map<String, List<Renvoi>> parInscription =
        ids.isEmpty() ? Map.of() : renvois.desInscriptions(ids).stream().collect(Collectors.groupingBy(r -> r.getInscription().getId()));
    List<Regle> regles = regles();
    Integer ex = palierExclusion(regles);
    Map<String, Discipline> r = new LinkedHashMap<>();
    for (String id : ids) {
      int jours = parInscription.getOrDefault(id, List.of()).stream().mapToInt(x -> joursDansPeriode(x, du, au)).sum();
      int nj = cumul.getOrDefault(id, Heures.RIEN).nonJustifiees();
      r.put(id, new Discipline(dansPeriode.getOrDefault(id, Heures.RIEN), nj, sanction(nj, regles, jours), jours, ex != null && nj >= ex));
    }
    return r;
  }

  // ---------- Palier d'exclusion ----------

  /**
   * Après une opération qui ajoute des heures non justifiées : les élèves qui atteignent le palier
   * d'exclusion sont signalés une fois au Proviseur, qui décide de l'exclusion.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void verifierPalier(Collection<String> inscriptionIds) {
    if (inscriptionIds.isEmpty()) return;
    Integer ex = palierExclusion(regles());
    if (ex == null) return;
    Set<String> deja =
        Set.copyOf(sql.queryForList("SELECT \"inscriptionId\" FROM \"AlerteExclusion\" WHERE \"inscriptionId\" IN (:ins)", Map.of("ins", inscriptionIds), String.class));
    List<String> aVoir = inscriptionIds.stream().filter(i -> !deja.contains(i)).distinct().toList();
    if (aVoir.isEmpty()) return;
    Map<String, Heures> cumul = heures(aVoir, List.of(1, 2, 3, 4, 5, 6));
    List<String> proviseurs = utilisateurs.findByRoleOrderByNomAscPrenomAsc(Role.PROVISEUR).stream().filter(Utilisateur::isActif).map(Utilisateur::getId).toList();
    for (Inscription i : inscriptions.activesParIds(aVoir)) {
      int nj = cumul.getOrDefault(i.getId(), Heures.RIEN).nonJustifiees();
      if (nj < ex) continue;
      sql.update("INSERT INTO \"AlerteExclusion\" (\"inscriptionId\", minutes) VALUES (:i, :m) ON CONFLICT DO NOTHING", Map.of("i", i.getId(), "m", nj));
      notifications.notifier(
          proviseurs, "Palier d'exclusion atteint",
          i.getEleve().nomComplet() + " (" + i.getClasse().getNom() + ") a " + duree(nj) + " d'absences non justifiées depuis la rentrée (palier : " + duree(ex)
              + "). L'exclusion est à votre décision ; sans exclusion, le bulletin portera la sanction la plus élevée atteinte.",
          "/eleves/" + i.getEleveId());
    }
  }

  /** Bilan d'une classe pour une période (écran « Bilan des absences »). */
  public cm.cetic.mezesse.dto.AbsencesDto.Bilan bilan(String classeId, Periode periode) {
    Map<String, Discipline> d = discipline(classeId, periode);
    List<Inscription> liste = inscriptions.deLaClasse(classeId);
    Set<String> prevenus = alertes(d.keySet());
    List<cm.cetic.mezesse.dto.AbsencesDto.BilanEleve> eleves = new ArrayList<>();
    for (Inscription i : liste) {
      Discipline x = d.get(i.getId());
      eleves.add(new cm.cetic.mezesse.dto.AbsencesDto.BilanEleve(
          i.getId(), i.getEleve().getId(), i.getEleve().getNom(), i.getEleve().getPrenoms(), i.getEleve().getMatricule(), x.periode().seances(),
          x.periode().minutes(), x.periode().justifiees(), x.periode().nonJustifiees(), x.cumulNonJustifiees(), x.sanction().name(), x.retenue().libelle(), x.joursExclusion(),
          x.paliersExclusionAtteint(), prevenus.contains(i.getId())));
    }
    return new cm.cetic.mezesse.dto.AbsencesDto.Bilan(classeId, periode.name(), regles(), eleves);
  }

  /** Élèves déjà signalés au Proviseur pour le palier d'exclusion. */
  public Set<String> alertes(Collection<String> inscriptionIds) {
    if (inscriptionIds.isEmpty()) return Set.of();
    return Set.copyOf(sql.queryForList("SELECT \"inscriptionId\" FROM \"AlerteExclusion\" WHERE \"inscriptionId\" IN (:ins)", Map.of("ins", inscriptionIds), String.class));
  }
}
