package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.EtablissementDto.BaremeVue;
import cm.cetic.mezesse.dto.EtablissementDto.Modification;
import cm.cetic.mezesse.dto.EtablissementDto.SanctionLigne;
import cm.cetic.mezesse.dto.EtablissementDto.SanctionVue;
import cm.cetic.mezesse.dto.EtablissementDto.Seuil;
import cm.cetic.mezesse.dto.EtablissementDto.Vue;
import cm.cetic.mezesse.entity.Bareme;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.SanctionAbsence;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.BaremeRepository;
import cm.cetic.mezesse.repository.EtablissementRepository;
import cm.cetic.mezesse.repository.SanctionAbsenceRepository;
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
  private final SanctionAbsenceRepository sanctionsAbsences;
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

  /** Règles des résultats (délibération, tableau d'honneur, matières composées, départage), réglées par la direction. */
  @Transactional
  public Vue reglesResultats(UtilisateurConnecte moi, cm.cetic.mezesse.dto.EtablissementDto.ReglesResultats d) {
    if (d.seuilMatieresIgnorees() < d.seuilNonClasse()) {
      throw ApiException.invalide("La part qui fait ignorer les matières non composées doit être au moins égale à celle du non-classement.");
    }
    Etablissement e = requis();
    Map<String, Object> avant = donnees(cm.cetic.mezesse.dto.EtablissementDto.ReglesResultats.de(e));
    e.setMoyenneDeliberation(d.moyenneDeliberation() == null ? null : BigDecimal.valueOf(d.moyenneDeliberation()).setScale(2, java.math.RoundingMode.HALF_UP));
    e.setHeuresRefusTableauHonneur(d.heuresRefusTableauHonneur());
    e.setSeuilNonClasse(BigDecimal.valueOf(d.seuilNonClasse()).setScale(2, java.math.RoundingMode.HALF_UP));
    e.setSeuilMatieresIgnorees(BigDecimal.valueOf(d.seuilMatieresIgnorees()).setScale(2, java.math.RoundingMode.HALF_UP));
    List<cm.cetic.mezesse.dto.EtablissementDto.CritereDepartage> criteres = d.criteres() == null ? List.of() : d.criteres();
    e.setCriteresDepartage(
        criteres.isEmpty()
            ? null
            : criteres.stream()
                .map(c -> {
                  Map<String, String> m = new java.util.LinkedHashMap<>();
                  m.put("type", c.type());
                  if ("GROUPE".equals(c.type())) m.put("groupe", c.groupe() == null ? "" : c.groupe().strip());
                  return m;
                })
                .collect(java.util.stream.Collectors.toList()));
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "resultats.regles", "Règles des résultats modifiées (délibération, tableau d'honneur, classement)", "/parametres",
        Donnees.objet("avant", avant, "apres", donnees(cm.cetic.mezesse.dto.EtablissementDto.ReglesResultats.de(e))));
    return Vue.de(e);
  }

  /** Les règles en objet JSON, pour le journal et l'annulation. */
  static Map<String, Object> donnees(cm.cetic.mezesse.dto.EtablissementDto.ReglesResultats r) {
    List<Map<String, Object>> criteres = new java.util.ArrayList<>();
    for (var c : r.criteres()) criteres.add(Donnees.objet("type", c.type(), "groupe", c.groupe()));
    return Donnees.objet(
        "moyenneDeliberation", r.moyenneDeliberation(), "heuresRefusTableauHonneur", r.heuresRefusTableauHonneur(), "seuilNonClasse", r.seuilNonClasse(),
        "seuilMatieresIgnorees", r.seuilMatieresIgnorees(), "criteres", criteres);
  }

  public List<SanctionVue> sanctions() {
    return sanctionsAbsences.findAllByOrderByMinutesAsc().stream().map(s -> new SanctionVue(s.getId(), s.getLibelle(), s.getLigne(), s.getMinutes())).toList();
  }

  /** Les trois paliers et leur libellé, tel qu'il s'affiche et s'imprime. */
  public static final Map<BilanAbsences.Sanction, String> NOMS_SANCTIONS =
      Map.of(BilanAbsences.Sanction.AVERTISSEMENT, "Avertissement conduite", BilanAbsences.Sanction.BLAME, "Blâme conduite", BilanAbsences.Sanction.EXCLUSION, "Exclusion");

  /** Le SG règle les heures des trois paliers (avertissement, blâme, exclusion) ; un palier sans heures n'est pas utilisé. */
  @Transactional
  public List<SanctionVue> remplacerSanctions(UtilisateurConnecte moi, List<SanctionLigne> lignes) {
    if (lignes.stream().anyMatch(l -> !NOMS_SANCTIONS.containsKey(l.ligne()))) throw ApiException.invalide("Sanctions possibles : avertissement, blâme, exclusion.");
    if (new HashSet<>(lignes.stream().map(SanctionLigne::ligne).toList()).size() != lignes.size()) throw ApiException.invalide("Chaque sanction ne figure qu'une fois.");
    List<BilanAbsences.Regle> regles =
        lignes.stream()
            .filter(l -> l.heures() != null && l.heures() > 0)
            .sorted(Comparator.comparing(SanctionLigne::ligne))
            .map(l -> new BilanAbsences.Regle(NOMS_SANCTIONS.get(l.ligne()), l.ligne(), (int) Math.round(l.heures() * 60)))
            .toList();
    for (int k = 1; k < regles.size(); k++) {
      if (regles.get(k).minutes() <= regles.get(k - 1).minutes()) {
        throw ApiException.invalide(regles.get(k).libelle() + " doit partir de plus d'heures que " + regles.get(k - 1).libelle().toLowerCase(java.util.Locale.ROOT) + ".");
      }
    }
    List<Map<String, Object>> avant = sanctions().stream().map(v -> Donnees.objet("libelle", v.libelle(), "ligne", v.ligne().name(), "minutes", v.minutes())).toList();
    remplacerSanctions(regles);
    List<String> resume = regles.stream().map(r -> r.libelle() + " dès " + BilanAbsences.duree(r.minutes())).toList();
    journal.consigner(
        moi, DomaineDecision.ADMINISTRATION, "sanctions.liste",
        "Sanctions pour absences non justifiées : " + (resume.isEmpty() ? "aucune" : String.join(", ", resume)), "/parametres",
        Donnees.objet("avant", avant));
    return sanctions();
  }

  /** Remplace la liste (sert aussi à rétablir l'ancienne). */
  @Transactional
  public void remplacerSanctions(List<BilanAbsences.Regle> regles) {
    sanctionsAbsences.toutSupprimer();
    sanctionsAbsences.saveAll(
        regles.stream()
            .map(
                r -> {
                  SanctionAbsence s = new SanctionAbsence();
                  s.setLibelle(r.libelle());
                  s.setLigne(r.ligne());
                  s.setMinutes(r.minutes());
                  return s;
                })
            .toList());
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
