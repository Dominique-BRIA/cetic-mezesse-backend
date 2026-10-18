package cm.cetic.mezesse.service;

import cm.cetic.mezesse.config.CeticProperties;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Bareme;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.BaremeRepository;
import cm.cetic.mezesse.repository.EtablissementRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Amorçage d'une base vide : établissement, barèmes, année scolaire, comptes Proviseur et SG. Sans
 * danger à relancer : rien n'est écrasé. Lancement : java -jar cetic-api.jar --amorcer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmorcageService implements ApplicationRunner {

  private record Mention(String libelle, String libelleLong, int min) {}

  private static final List<Mention> GRADES =
      List.of(new Mention("A", null, 16), new Mention("B+", null, 15), new Mention("B", null, 14), new Mention("C+", null, 12), new Mention("C", null, 10), new Mention("D", null, 0));

  private static final List<Mention> COTES =
      List.of(
          new Mention("CTBA", "Compétences très bien acquises", 16), new Mention("CBA", "Compétences bien acquises", 14),
          new Mention("CA", "Compétences acquises", 12), new Mention("CMA", "Compétences moyennement acquises", 10),
          new Mention("CNA", "Compétences non acquises", 0));

  private final EtablissementRepository etablissements;
  private final BaremeRepository baremes;
  private final AnneeScolaireRepository annees;
  private final UtilisateurRepository utilisateurs;
  private final PasswordEncoder motsDePasse;
  private final CeticProperties proprietes;
  private final ApplicationContext contexte;

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption("amorcer")) return;
    amorcer();
    log.info("Amorçage terminé.");
    System.exit(SpringApplication.exit(contexte, () -> 0));
  }

  /** L'année scolaire commence en septembre. */
  static String anneeCourante(LocalDate aujourdhui) {
    int debut = aujourdhui.getMonthValue() >= 8 ? aujourdhui.getYear() : aujourdhui.getYear() - 1;
    return debut + "-" + (debut + 1);
  }

  @Transactional
  public void amorcer() {
    if (!etablissements.existsById(Etablissement.ID)) {
      Etablissement e = new Etablissement();
      e.setNomFr("Collège d'Enseignement Technique Industriel et Commercial de Mezesse");
      e.setNomEn("Technical and Commercial College of Mezesse");
      e.setSigle("CETIC");
      e.setRegionFr("Sud");
      e.setRegionEn("South");
      e.setDepartementFr("Dja-et-Lobo");
      e.setDepartementEn("Dja and Lobo");
      e.setArrondissement("Sangmélima");
      e.setLocalite("Mezesse");
      e.setCode("8CL1TEFD100792107");
      e.setPrefixeMatricule("CTM");
      etablissements.save(e);
    }
    if (baremes.count() == 0) {
      GRADES.forEach(m -> baremes.save(bareme(TypeBareme.GRADE, m)));
      COTES.forEach(m -> baremes.save(bareme(TypeBareme.COTE, m)));
    }
    if (annees.count() == 0) {
      AnneeScolaire a = new AnneeScolaire();
      a.setLibelle(anneeCourante(LocalDate.now()));
      a.setActive(true);
      for (int n = 1; n <= 6; n++) {
        Sequence s = new Sequence();
        s.setAnnee(a);
        s.setNumero(n);
        s.setTrimestre((n + 1) / 2);
        s.setSaisieOuverte(n == 1);
        a.getSequences().add(s);
      }
      annees.save(a);
    }
    CeticProperties.Amorcage c = proprietes.amorcage();
    String empreinte = motsDePasse.encode(c.motDePasse());
    compte(Role.PROVISEUR, c.proviseurTelephone(), "DIRECTEUR", "Le", empreinte);
    compte(Role.SURVEILLANT_GENERAL, c.sgTelephone(), "GÉNÉRAL", "Surveillant", empreinte);
  }

  private static Bareme bareme(TypeBareme type, Mention m) {
    Bareme b = new Bareme();
    b.setType(type);
    b.setLibelle(m.libelle());
    b.setLibelleLong(m.libelleLong());
    b.setMin(BigDecimal.valueOf(m.min()));
    return b;
  }

  private void compte(Role role, String telephone, String nom, String prenom, String empreinte) {
    if (utilisateurs.existsByRole(role)) return;
    Utilisateur u = new Utilisateur();
    u.setRole(role);
    u.setTelephone(telephone);
    u.setNom(nom);
    u.setPrenom(prenom);
    u.setMotDePasse(empreinte);
    utilisateurs.save(u);
    log.info("Compte {} créé : {}", role, telephone);
  }
}
