package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.EnteteOfficiel;
import cm.cetic.mezesse.dto.AppareilDto.Rapport;
import cm.cetic.mezesse.dto.AppareilDto.Signal;
import cm.cetic.mezesse.entity.Appareil;
import cm.cetic.mezesse.entity.Plantage;
import cm.cetic.mezesse.repository.AppareilRepository;
import cm.cetic.mezesse.repository.HistoriqueConsoleRepository;
import cm.cetic.mezesse.repository.PlantageRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ce que l'application mobile signale sur elle-même : sa version à l'ouverture, ses plantages. */
@Service
@RequiredArgsConstructor
public class AppareilService {

  private static final Duration CONSERVATION = Duration.ofDays(90);
  private static final int LIGNES_EMPREINTE = 8;

  private final AppareilRepository appareils;
  private final PlantageRepository plantages;
  private final UtilisateurRepository utilisateurs;
  private final HistoriqueConsoleRepository historique;

  @Transactional
  public void signal(UtilisateurConnecte moi, Signal d) {
    Appareil a = appareils.findByInstallationId(d.installationId()).orElseGet(Appareil::new);
    a.setInstallationId(d.installationId());
    a.setUtilisateur(utilisateurs.getReferenceById(moi.id()));
    a.setNumeroVersion(d.numeroVersion());
    a.setNomVersion(d.nomVersion());
    a.setCorrectif(d.correctif());
    a.setModele(d.modele());
    a.setVersionAndroid(d.versionAndroid());
    a.setDerniereVue(Horloge.maintenant());
    appareils.save(a);
    historique.noterActivite(LocalDate.now(EnteteOfficiel.CAMEROUN), d.installationId(), d.numeroVersion());
  }

  /** Un plantage revenu après avoir été marqué résolu redevient ouvert. */
  @Transactional
  public void plantage(Rapport d) {
    String empreinte = empreinte(d.type(), d.message(), d.pile());
    Instant maintenant = Horloge.maintenant();
    Plantage p = plantages.findByEmpreinteAndInstallationIdAndNumeroVersion(empreinte, d.installationId(), d.numeroVersion()).orElse(null);
    if (p == null) {
      p = new Plantage();
      p.setEmpreinte(empreinte);
      p.setInstallationId(d.installationId());
      p.setNumeroVersion(d.numeroVersion());
      p.setPremiereFois(maintenant);
    } else {
      p.setOccurrences(p.getOccurrences() + 1);
    }
    p.setType(d.type());
    p.setMessage(d.message());
    p.setPile(d.pile());
    p.setNomVersion(d.nomVersion());
    p.setModele(d.modele());
    p.setVersionAndroid(d.versionAndroid());
    p.setDerniereFois(maintenant);
    plantages.save(p);
    historique.noterPlantage(LocalDate.now(EnteteOfficiel.CAMEROUN), d.type(), d.numeroVersion());
    plantages.marquer(empreinte, false);
    plantages.supprimerAvant(maintenant.minus(CONSERVATION));
  }

  /**
   * Regroupe les plantages de même origine : type et premières lignes de la pile, sans les chiffres
   * (numéros de ligne, adresses) ni l'identifiant de la table R8, qui changent d'une version à l'autre.
   */
  static String empreinte(String type, String message, String pile) {
    String source = pile == null || pile.isBlank() ? message : pile;
    String reduit =
        source.lines().filter(l -> !l.isBlank()).limit(LIGNES_EMPREINTE).collect(Collectors.joining("\n")).replaceAll("r8-map-id-[0-9a-f]+", "").replaceAll("\\d+", "");
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest((type + "\n" + reduit).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(h, 0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
