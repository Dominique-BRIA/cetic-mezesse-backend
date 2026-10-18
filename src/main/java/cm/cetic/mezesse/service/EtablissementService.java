package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.EtablissementDto.BaremeVue;
import cm.cetic.mezesse.dto.EtablissementDto.Modification;
import cm.cetic.mezesse.dto.EtablissementDto.Seuil;
import cm.cetic.mezesse.dto.EtablissementDto.Vue;
import cm.cetic.mezesse.entity.Bareme;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.BaremeRepository;
import cm.cetic.mezesse.repository.EtablissementRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import cm.cetic.mezesse.util.Nombres;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Informations de l'établissement et barèmes des grades et des cotes, modifiables par le SG. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EtablissementService {

  private final EtablissementRepository etablissements;
  private final BaremeRepository baremes;
  private final JournalDecisions journal;

  public Vue lire() {
    return Vue.de(etablissements.findById(Etablissement.ID).orElse(null));
  }

  /** L'établissement, obligatoire pour imprimer les documents. */
  public Etablissement requis() {
    return etablissements
        .findById(Etablissement.ID)
        .orElseThrow(() -> ApiException.invalide("Renseignez d'abord les informations de l'établissement (Paramètres)."));
  }

  /** Champs qu'une désapprobation remet en l'état (AnnulationService). */
  static Map<String, Object> champs(Etablissement e) {
    return Donnees.objet(
        "nomFr", e.getNomFr(), "nomEn", e.getNomEn(), "sigle", e.getSigle(), "regionFr", e.getRegionFr(),
        "regionEn", e.getRegionEn(), "departementFr", e.getDepartementFr(), "departementEn", e.getDepartementEn(),
        "arrondissement", e.getArrondissement(), "localite", e.getLocalite(), "code", e.getCode(),
        "boitePostale", e.getBoitePostale(), "telephone", e.getTelephone(), "prefixeMatricule", e.getPrefixeMatricule(),
        "sigleEn", e.getSigleEn());
  }

  @Transactional
  public Vue modifier(UtilisateurConnecte moi, Modification d) {
    Etablissement e = etablissements.findById(Etablissement.ID).orElse(null);
    Map<String, Object> avant = e == null ? null : champs(e);
    if (e == null) e = new Etablissement();
    e.setNomFr(d.nomFr());
    e.setNomEn(d.nomEn());
    e.setSigle(d.sigle());
    e.setRegionFr(d.regionFr());
    e.setRegionEn(d.regionEn());
    e.setDepartementFr(d.departementFr());
    e.setDepartementEn(d.departementEn());
    e.setArrondissement(d.arrondissement());
    e.setLocalite(d.localite());
    e.setCode(d.code());
    e.setBoitePostale(d.boitePostale());
    e.setTelephone(d.telephone());
    e.setPrefixeMatricule(d.prefixeMatricule());
    e.setSigleEn(d.sigleEn());
    e = etablissements.save(e);
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "etablissement.modifier", "Modification des informations de l'établissement", "/parametres",
        Donnees.objet("avant", avant, "apres", champs(e)));
    return Vue.de(e);
  }

  /** Moyenne minimale du tableau d'honneur, réglée par le Proviseur ou le SG. */
  @Transactional
  public Vue seuilTableauHonneur(UtilisateurConnecte moi, double seuil) {
    Etablissement e = requis();
    BigDecimal avant = e.getSeuilTableauHonneur();
    e.setSeuilTableauHonneur(BigDecimal.valueOf(seuil).setScale(2, java.math.RoundingMode.HALF_UP));
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "tableauhonneur.seuil",
        "Tableau d'honneur : moyenne minimale " + Nombres.fr(avant.doubleValue()) + " → " + Nombres.fr(seuil), "/parametres",
        Donnees.objet("avant", avant.doubleValue(), "apres", seuil));
    return Vue.de(e);
  }

  public List<BaremeVue> baremes() {
    return baremes.findAllByOrderByTypeAscMinDesc().stream().map(BaremeVue::de).toList();
  }

  /** Le SG remplace tout un barème d'un coup (grades ou cotes). */
  @Transactional
  public void remplacerBareme(UtilisateurConnecte moi, TypeBareme type, List<Seuil> seuils) {
    if (seuils.size() < 2) throw ApiException.invalide("Un barème compte au moins deux mentions.");
    if (seuils.stream().noneMatch(s -> s.min() == 0)) {
      throw ApiException.invalide("Une mention doit commencer à 0 pour que toute note en reçoive une.");
    }
    if (new HashSet<>(seuils.stream().map(Seuil::min).toList()).size() != seuils.size()) {
      throw ApiException.invalide("Deux mentions ont la même borne.");
    }
    if (new HashSet<>(seuils.stream().map(Seuil::libelle).toList()).size() != seuils.size()) {
      throw ApiException.invalide("Deux mentions ont le même libellé.");
    }
    List<Map<String, Object>> avant =
        baremes.findByType(type).stream()
            .map(b -> Donnees.objet("libelle", b.getLibelle(), "libelleLong", b.getLibelleLong(), "min", b.getMin().doubleValue()))
            .toList();
    remplacer(type, seuils);
    List<String> lignes =
        seuils.stream()
            .sorted(Comparator.comparingDouble(Seuil::min).reversed())
            .map(s -> s.libelle() + " dès " + Nombres.simple(s.min()))
            .toList();
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "bareme.modifier",
        "Modification du barème des " + (type == TypeBareme.GRADE ? "grades" : "cotes") + " : " + String.join(", ", lignes),
        "/parametres", Donnees.objet("type", type.name(), "avant", avant));
  }

  /** Remplace les lignes d'un barème (sert aussi à rétablir l'ancien barème). */
  @Transactional
  public void remplacer(TypeBareme type, List<Seuil> seuils) {
    baremes.supprimerType(type);
    baremes.saveAll(
        seuils.stream()
            .map(
                s -> {
                  Bareme b = new Bareme();
                  b.setType(type);
                  b.setLibelle(s.libelle());
                  b.setLibelleLong(s.libelleLong());
                  b.setMin(BigDecimal.valueOf(s.min()));
                  return b;
                })
            .toList());
  }
}
