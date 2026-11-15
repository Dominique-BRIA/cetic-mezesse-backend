package cm.cetic.mezesse.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Chaque nuit, retire du stockage (Backblaze B2) les fichiers qui ne servent plus : anciennes photos d'élèves (remplacées
 * ou supprimées) et pièces justificatives orphelines. Un fichier est gardé tant qu'il sert (photo d'un élève, pièce d'une
 * justification), tant qu'une décision récente peut encore le remettre (désapprobation dans le délai de 4 semaines), et
 * pendant deux jours après son dépôt (envoi en cours, transaction pas encore validée).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NettoyageStockage {

  static final List<String> DOSSIERS = List.of("eleves/", "justificatifs/");
  /** Délai de désapprobation (28 jours) et une marge. */
  static final Duration DECISIONS_RECENTES = Duration.ofDays(JournalDecisions.DELAI_DESAPPROBATION_JOURS + 7);
  static final Duration DEPOT_RECENT = Duration.ofDays(2);
  private static final Pattern CLE = Pattern.compile("(?:eleves|justificatifs)/[A-Za-z0-9._-]+");

  private final StockageService stockage;
  private final NamedParameterJdbcTemplate sql;

  /**
   * Coupé par défaut : NETTOYAGE_STOCKAGE=true seulement sur le serveur de production, dont la base va avec le bucket.
   * Une API lancée avec une autre base (copie locale) et les identifiants du vrai bucket effacerait sinon ses photos.
   */
  @org.springframework.beans.factory.annotation.Value("${cetic.nettoyage-stockage:false}")
  private boolean actif;

  /** « bloque » : plus de la moitié des fichiers seraient partis, rien n'a été supprimé. */
  public record Bilan(int examines, int supprimes, boolean bloque) {}

  /** Chaque nuit à 2 h 30 (heure du Cameroun) ; l'heure se règle par cetic.nettoyage-cron. */
  @Scheduled(cron = "${cetic.nettoyage-cron:0 30 2 * * *}", zone = "Africa/Douala")
  public void chaqueNuit() {
    if (!actif) return;
    try {
      Bilan b = nettoyer(Instant.now());
      if (b.bloque()) log.warn("Nettoyage du stockage bloqué ({} fichiers) : la base et le bucket ne semblent pas aller ensemble. Rien n'a été supprimé.", b.examines());
      else log.info("Nettoyage du stockage : {} fichier(s) examiné(s), {} supprimé(s).", b.examines(), b.supprimes());
    } catch (RuntimeException e) {
      log.warn("Nettoyage du stockage interrompu : {}", e.getMessage());
    }
  }

  public Bilan nettoyer(Instant maintenant) {
    Set<String> gardees = clesUtilisees(maintenant);
    List<StockageService.Objet> tous = new java.util.ArrayList<>();
    for (String dossier : DOSSIERS) tous.addAll(stockage.lister(dossier));
    List<StockageService.Objet> partants = tous.stream().filter(o -> aSupprimer(o, gardees, maintenant)).toList();
    // Garde-fous : la base ne connaît aucune photo alors que le stockage en a, ou plus de la moitié partirait.
    boolean basePhotosVide = gardees.stream().noneMatch(c -> c.startsWith("eleves/")) && tous.stream().anyMatch(o -> o.cle().startsWith("eleves/"));
    if (basePhotosVide || bloquer(partants.size(), tous.size())) return new Bilan(tous.size(), 0, true);
    for (StockageService.Objet o : partants) stockage.supprimer(o.cle());
    return new Bilan(tous.size(), partants.size(), false);
  }

  /** Garde-fou : au-delà de 20 fichiers, jamais plus de la moitié d'un coup (signe d'une base ou d'un bucket qui ne vont pas ensemble). */
  static boolean bloquer(int partants, int total) {
    return partants > 20 && partants * 2 > total;
  }

  /** Un fichier part s'il n'est ni utilisé, ni cité par une décision récente, ni déposé il y a moins de deux jours. */
  static boolean aSupprimer(StockageService.Objet o, Set<String> gardees, Instant maintenant) {
    if (gardees.contains(o.cle())) return false;
    return o.modifieLe() != null && o.modifieLe().isBefore(maintenant.minus(DEPOT_RECENT));
  }

  /** Photos des élèves, pièces des justifications, et clés citées par les décisions encore désapprouvables. */
  private Set<String> clesUtilisees(Instant maintenant) {
    Set<String> cles = new HashSet<>();
    cles.addAll(sql.queryForList("SELECT \"photoCle\" FROM \"Eleve\" WHERE \"photoCle\" IS NOT NULL", new MapSqlParameterSource(), String.class));
    cles.addAll(sql.queryForList("SELECT cle FROM \"PieceJustificative\" WHERE cle IS NOT NULL", new MapSqlParameterSource(), String.class));
    List<String> donnees =
        sql.queryForList(
            "SELECT annulation::text FROM \"Decision\" WHERE annulation IS NOT NULL AND \"creeLe\" > :depuis",
            new MapSqlParameterSource("depuis", java.sql.Timestamp.from(maintenant.minus(DECISIONS_RECENTES))),
            String.class);
    for (String d : donnees) cles.addAll(citees(d));
    return cles;
  }

  /** Les clés de fichiers citées dans un texte (données d'une décision). */
  static Set<String> citees(String texte) {
    Set<String> s = new HashSet<>();
    if (texte == null) return s;
    Matcher m = CLE.matcher(texte);
    while (m.find()) s.add(m.group());
    return s;
  }
}
