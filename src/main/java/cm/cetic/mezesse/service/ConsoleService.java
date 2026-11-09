package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.ConsoleDto.Apercu;
import cm.cetic.mezesse.dto.ConsoleDto.CompteSansAppareil;
import cm.cetic.mezesse.dto.ConsoleDto.DetailPlantage;
import cm.cetic.mezesse.dto.ConsoleDto.GroupePlantage;
import cm.cetic.mezesse.dto.ConsoleDto.LigneAppareil;
import cm.cetic.mezesse.dto.ConsoleDto.LigneJournal;
import cm.cetic.mezesse.dto.ConsoleDto.OccurrencePlantage;
import cm.cetic.mezesse.dto.ConsoleDto.Parc;
import cm.cetic.mezesse.dto.ConsoleDto.VersionRepartie;
import cm.cetic.mezesse.entity.Appareil;
import cm.cetic.mezesse.entity.Plantage;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AppareilRepository;
import cm.cetic.mezesse.repository.JournalConsoleRepository;
import cm.cetic.mezesse.repository.PlantageRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectures de la console technique : données techniques seulement, jamais les notes ni les élèves. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsoleService {

  private final AppareilRepository appareils;
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
