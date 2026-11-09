package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.EnteteOfficiel;
import cm.cetic.mezesse.dto.ConsoleDto.Analytique;
import cm.cetic.mezesse.dto.ConsoleDto.Apercu;
import cm.cetic.mezesse.dto.ConsoleDto.CompteSansAppareil;
import cm.cetic.mezesse.dto.ConsoleDto.Cumul;
import cm.cetic.mezesse.dto.ConsoleDto.DetailPlantage;
import cm.cetic.mezesse.dto.ConsoleDto.GroupePlantage;
import cm.cetic.mezesse.dto.ConsoleDto.JourAnalytique;
import cm.cetic.mezesse.dto.ConsoleDto.LigneAppareil;
import cm.cetic.mezesse.dto.ConsoleDto.LigneJournal;
import cm.cetic.mezesse.dto.ConsoleDto.OccurrencePlantage;
import cm.cetic.mezesse.dto.ConsoleDto.Parc;
import cm.cetic.mezesse.dto.ConsoleDto.Part;
import cm.cetic.mezesse.dto.ConsoleDto.RoleParc;
import cm.cetic.mezesse.dto.ConsoleDto.VersionRepartie;
import cm.cetic.mezesse.entity.Appareil;
import cm.cetic.mezesse.entity.Plantage;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AppareilRepository;
import cm.cetic.mezesse.repository.HistoriqueConsoleRepository;
import cm.cetic.mezesse.repository.HistoriqueConsoleRepository.ActiviteLigne;
import cm.cetic.mezesse.repository.HistoriqueConsoleRepository.PlantageLigne;
import cm.cetic.mezesse.repository.JournalConsoleRepository;
import cm.cetic.mezesse.repository.PlantageRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectures de la console technique : données techniques seulement, jamais les notes ni les élèves. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsoleService {

  private static final int PARTS_MAX = 6;

  private final AppareilRepository appareils;
  private final HistoriqueConsoleRepository historique;
  private final PlantageRepository plantages;
  private final UtilisateurRepository utilisateurs;
  private final JournalConsoleRepository journalLignes;
  private final JournalConsoleService journal;
  private final PolitiqueMiseAJourService politique;

  public Apercu apercu() {
    Instant maintenant = Horloge.maintenant();
    List<Appareil> parc = appareils.tousAvecCompte();
    List<Plantage> lignes = plantages.findAllByOrderByDerniereFoisDesc();
    List<CompteSansAppareil> sans = comptesSansAppareil(parc);
    Instant ilYASept = maintenant.minus(Duration.ofDays(7));
    Instant hier = maintenant.minus(Duration.ofHours(24));
    List<VersionRepartie> repartition =
        parc.stream()
            .collect(Collectors.groupingBy(Appareil::getNumeroVersion))
            .entrySet()
            .stream()
            .map(e -> new VersionRepartie(e.getKey(), e.getValue().get(0).getNomVersion(), e.getValue().size()))
            .sorted(Comparator.comparingInt(VersionRepartie::numeroVersion).reversed())
            .toList();
    return new Apercu(
        parc.size(),
        parc.stream().filter(a -> a.getDerniereVue().isAfter(ilYASept)).count(),
        utilisateurs.findAll().stream().filter(Utilisateur::isActif).count(),
        sans.size(),
        lignes.stream().filter(p -> p.getDerniereFois().isAfter(hier)).mapToLong(Plantage::getOccurrences).sum(),
        lignes.stream().filter(p -> !p.isResolu()).map(Plantage::getEmpreinte).distinct().count(),
        repartition,
        politique.lire());
  }

  /** @param jours 7, 30 ou 90 ; la période précédente, de même durée, sert de comparaison */
  public Analytique analytique(int jours) {
    if (!List.of(7, 30, 90).contains(jours)) throw ApiException.invalide("jours : 7, 30 ou 90");
    LocalDate fin = LocalDate.now(EnteteOfficiel.CAMEROUN);
    LocalDate debut = fin.minusDays(jours - 1L);
    LocalDate finPrecedente = debut.minusDays(1);
    LocalDate debutPrecedent = debut.minusDays(jours);

    List<Appareil> parc = appareils.tousAvecCompte();
    List<LocalDate> arrivees = parc.stream().map(a -> LocalDate.ofInstant(a.getPremiereVue(), EnteteOfficiel.CAMEROUN)).toList();
    List<ActiviteLigne> activite = historique.activite(debut);
    List<PlantageLigne> incidents = historique.plantages(debutPrecedent);

    return new Analytique(
        jours,
        new Cumul(historique.appareilsActifs(debut, fin), compterEntre(arrivees, debut, fin), sommeEntre(incidents, debut, fin)),
        new Cumul(historique.appareilsActifs(debutPrecedent, finPrecedente), compterEntre(arrivees, debutPrecedent, finPrecedente), sommeEntre(incidents, debutPrecedent, finPrecedente)),
        serie(fin, jours, activite, incidents, arrivees),
        parts(parc.stream().map(Appareil::getModele)),
        parts(parc.stream().map(a -> a.getVersionAndroid() == null ? null : "Android " + a.getVersionAndroid())),
        rolesDuParc(parc));
  }

  /** Une entrée par jour, du plus ancien à aujourd'hui : une courbe sans trou, même les jours sans rien. */
  static List<JourAnalytique> serie(LocalDate fin, int jours, List<ActiviteLigne> activite, List<PlantageLigne> incidents, List<LocalDate> arrivees) {
    Map<LocalDate, List<ActiviteLigne>> parJour = activite.stream().collect(Collectors.groupingBy(ActiviteLigne::jour));
    Map<LocalDate, List<PlantageLigne>> plantagesParJour = incidents.stream().collect(Collectors.groupingBy(PlantageLigne::jour));
    Map<LocalDate, Long> arriveesParJour = arrivees.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    return Stream.iterate(fin.minusDays(jours - 1L), j -> j.plusDays(1))
        .limit(jours)
        .map(
            j -> {
              List<ActiviteLigne> lignes = parJour.getOrDefault(j, List.of());
              Map<Integer, Long> parVersion = new TreeMap<>();
              lignes.forEach(l -> parVersion.merge(l.numeroVersion(), l.appareils(), Long::sum));
              Map<String, Long> types = new TreeMap<>();
              plantagesParJour.getOrDefault(j, List.of()).forEach(l -> types.merge(l.type(), l.occurrences(), Long::sum));
              return new JourAnalytique(j, lignes.stream().mapToLong(ActiviteLigne::appareils).sum(), arriveesParJour.getOrDefault(j, 0L), parVersion, types);
            })
        .toList();
  }

  private static long compterEntre(List<LocalDate> dates, LocalDate de, LocalDate a) {
    return dates.stream().filter(d -> !d.isBefore(de) && !d.isAfter(a)).count();
  }

  private static long sommeEntre(List<PlantageLigne> lignes, LocalDate de, LocalDate a) {
    return lignes.stream().filter(l -> !l.jour().isBefore(de) && !l.jour().isAfter(a)).mapToLong(PlantageLigne::occurrences).sum();
  }

  /** Les plus fréquents d'abord ; au-delà de six, le reste est regroupé dans « Autres ». */
  static List<Part> parts(Stream<String> valeurs) {
    List<Part> triees =
        valeurs.map(v -> v == null || v.isBlank() ? "Inconnu" : v).collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
            .map(e -> new Part(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(Part::valeur).reversed().thenComparing(Part::libelle))
            .toList();
    if (triees.size() <= PARTS_MAX) return triees;
    List<Part> reduites = new ArrayList<>(triees.subList(0, PARTS_MAX));
    reduites.add(new Part("Autres", triees.subList(PARTS_MAX, triees.size()).stream().mapToLong(Part::valeur).sum()));
    return reduites;
  }

  private List<RoleParc> rolesDuParc(List<Appareil> parc) {
    Set<String> vus = parc.stream().filter(a -> a.getUtilisateur() != null).map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet());
    return utilisateurs.findAll().stream()
        .filter(Utilisateur::isActif)
        .collect(Collectors.groupingBy(Utilisateur::getRole))
        .entrySet()
        .stream()
        .map(
            e -> {
              long equipes = e.getValue().stream().filter(u -> vus.contains(u.getId())).count();
              return new RoleParc(e.getKey().name(), equipes, e.getValue().size() - equipes);
            })
        .sorted(Comparator.comparingLong((RoleParc r) -> r.equipes() + r.sansAppareil()).reversed())
        .toList();
  }

  public Parc parc() {
    List<Appareil> parc = appareils.tousAvecCompte();
    return new Parc(parc.stream().map(ConsoleService::ligne).toList(), comptesSansAppareil(parc));
  }

  private static LigneAppareil ligne(Appareil a) {
    Utilisateur u = a.getUtilisateur();
    return new LigneAppareil(
        a.getInstallationId(), u == null ? null : u.nomComplet(), u == null ? null : u.getRole().name(), a.getNumeroVersion(), a.getNomVersion(), a.getCorrectif(),
        a.getModele(), a.getVersionAndroid(), a.getPremiereVue(), a.getDerniereVue());
  }

  /** Comptes actifs dont aucun téléphone ne s'est signalé : site seul, ou application trop ancienne pour le faire. */
  private List<CompteSansAppareil> comptesSansAppareil(List<Appareil> parc) {
    Set<String> vus = parc.stream().filter(a -> a.getUtilisateur() != null).map(a -> a.getUtilisateur().getId()).collect(Collectors.toSet());
    return utilisateurs.findAll().stream()
        .filter(u -> u.isActif() && !vus.contains(u.getId()))
        .sorted(Comparator.comparing(Utilisateur::getNom))
        .map(u -> new CompteSansAppareil(u.getId(), u.nomComplet(), u.getRole().name(), u.getTelephone()))
        .toList();
  }

  /** @param etat ouverts, resolus ou tous */
  public List<GroupePlantage> plantages(String etat) {
    if (!List.of("ouverts", "resolus", "tous").contains(etat)) throw ApiException.invalide("etat : ouverts, resolus ou tous");
    return plantages.findAllByOrderByDerniereFoisDesc().stream()
        .collect(Collectors.groupingBy(Plantage::getEmpreinte))
        .values()
        .stream()
        .map(ConsoleService::groupe)
        .filter(g -> etat.equals("tous") || g.resolu() == etat.equals("resolus"))
        .sorted(Comparator.comparing(GroupePlantage::derniereFois).reversed())
        .toList();
  }

  public DetailPlantage plantage(String empreinte) {
    List<Plantage> lignes = plantages.findByEmpreinteOrderByDerniereFoisDesc(empreinte);
    if (lignes.isEmpty()) throw ApiException.introuvable("Plantage");
    Map<String, Appareil> parc = appareils.tousAvecCompte().stream().collect(Collectors.toMap(Appareil::getInstallationId, Function.identity()));
    List<OccurrencePlantage> occurrences =
        lignes.stream()
            .map(
                p -> {
                  Appareil a = parc.get(p.getInstallationId());
                  String compte = a == null || a.getUtilisateur() == null ? null : a.getUtilisateur().nomComplet();
                  return new OccurrencePlantage(p.getInstallationId(), compte, p.getModele(), p.getVersionAndroid(), p.getNomVersion(), p.getOccurrences(), p.getDerniereFois());
                })
            .toList();
    return new DetailPlantage(groupe(lignes), lignes.get(0).getPile(), occurrences);
  }

  @Transactional
  public void marquer(UtilisateurConnecte moi, String empreinte, boolean resolu) {
    if (plantages.marquer(empreinte, resolu) == 0) throw ApiException.introuvable("Plantage");
    journal.noter(moi.nom(), resolu ? "plantage.resolu" : "plantage.rouvert", empreinte, null);
  }

  public List<LigneJournal> journal() {
    return journalLignes.findTop200ByOrderByDateDesc().stream().map(LigneJournal::de).toList();
  }

  /** @param lignes du plus récent au plus ancien */
  private static GroupePlantage groupe(List<Plantage> lignes) {
    Plantage dernier = lignes.stream().max(Comparator.comparing(Plantage::getDerniereFois)).orElseThrow();
    return new GroupePlantage(
        dernier.getEmpreinte(),
        dernier.getType(),
        dernier.getMessage(),
        lignes.stream().mapToLong(Plantage::getOccurrences).sum(),
        lignes.stream().map(Plantage::getInstallationId).distinct().count(),
        lignes.stream().map(Plantage::getPremiereFois).min(Comparator.naturalOrder()).orElseThrow(),
        dernier.getDerniereFois(),
        lignes.stream().allMatch(Plantage::isResolu),
        lignes.stream().sorted(Comparator.comparingInt(Plantage::getNumeroVersion).reversed()).map(Plantage::getNomVersion).distinct().toList());
  }
}
