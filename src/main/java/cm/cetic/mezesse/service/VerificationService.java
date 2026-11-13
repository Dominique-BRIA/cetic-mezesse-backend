package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.VerificationDto.Action;
import cm.cetic.mezesse.dto.VerificationDto.Categorie;
import cm.cetic.mezesse.dto.VerificationDto.Ignorer;
import cm.cetic.mezesse.dto.VerificationDto.Point;
import cm.cetic.mezesse.dto.VerificationDto.Rapport;
import cm.cetic.mezesse.entity.Classe;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Matiere;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.entity.VerificationIgnoree;
import cm.cetic.mezesse.repository.ClasseRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.MatiereRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.repository.VerificationIgnoreeRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Ressemblance;
import cm.cetic.mezesse.util.Ressemblance.Force;
import cm.cetic.mezesse.util.Textes;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Page « À vérifier » : doublons probables, matières sans enseignant ou confiées à un compte
 * désactivé, classes sans professeur principal, fiches d'élèves incomplètes. Rien n'y est bloquant ;
 * chaque point se corrige depuis sa page, ou se marque « c'est normal » pour ne plus être signalé.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationService {

  private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final UtilisateurRepository utilisateurs;
  private final InscriptionRepository inscriptions;
  private final ClasseRepository classes;
  private final MatiereRepository matieres;
  private final VerificationIgnoreeRepository ignorees;

  public Rapport rapport(String anneeId, boolean avecIgnores) {
    Set<String> ignores = ignorees.findAll().stream().map(VerificationIgnoree::getCle).collect(Collectors.toSet());
    List<Utilisateur> comptes = utilisateurs.findByRoleOrderByNomAscPrenomAsc(Role.PROFESSEUR).stream().filter(u -> u.getFusionneDansId() == null).toList();
    List<Matiere> matieresAnnee = matieres.deLAnnee(anneeId, null, null);
    List<Classe> classesAnnee = classes.deLAnnee(anneeId);
    List<Inscription> inscrits = inscriptions.deLAnnee(anneeId, StatutInscription.ACTIF);

    List<Categorie> brutes = List.of(
        new Categorie(
            "enseignants-doublons", "Comptes d'enseignants en double", "Deux comptes au nom très proche : la même personne a peut-être été enregistrée deux fois.",
            doublonsEnseignants(comptes, matieresAnnee)),
        new Categorie(
            "eleves-doublons", "Élèves en double", "Deux fiches d'élèves au nom très proche cette année.",
            doublonsEleves(inscrits)),
        new Categorie(
            "matieres-doublons", "Matières en double", "Deux matières au nom très proche dans la même classe.",
            doublonsMatieres(matieresAnnee)),
        new Categorie(
            "matieres-enseignant-desactive", "Matières confiées à un compte désactivé", "L'enseignant ne peut plus se connecter : personne ne pourra saisir les notes.",
            matieresEnseignantDesactive(matieresAnnee)),
        new Categorie(
            "matieres-sans-enseignant", "Matières sans enseignant", "Aucun enseignant n'est désigné : personne ne pourra saisir les notes.",
            matieresSansEnseignant(matieresAnnee)),
        new Categorie(
            "classes-sans-pp", "Classes sans professeur principal", "Le professeur principal signe l'appréciation des bulletins.",
            classesSansPrincipal(classesAnnee)),
        new Categorie(
            "eleves-naissance", "Dates de naissance à confirmer", "Né(e) un 1er janvier : c'est souvent une date provisoire saisie en attendant l'acte de naissance.",
            naissancesProvisoires(inscrits)),
        new Categorie(
            "eleves-sans-parent", "Élèves sans parent à contacter", "Ni nom ni téléphone de parent sur la fiche.",
            sansParent(inscrits)));

    List<Categorie> categories = new ArrayList<>();
    long total = 0;
    long nbIgnores = 0;
    for (Categorie c : brutes) {
      List<Point> points = new ArrayList<>();
      for (Point p : c.points()) {
        boolean ignore = ignores.contains(p.cle());
        if (ignore) nbIgnores++;
        else total++;
        if (!ignore || avecIgnores) points.add(new Point(p.cle(), p.gravite(), p.titre(), p.detail(), p.action(), ignore));
      }
      if (!points.isEmpty()) categories.add(new Categorie(c.code(), c.titre(), c.explication(), points));
    }
    return new Rapport(categories, total, nbIgnores);
  }

  @Transactional
  public void ignorer(UtilisateurConnecte moi, Ignorer d) {
    VerificationIgnoree v = ignorees.findById(d.cle()).orElseGet(VerificationIgnoree::new);
    v.setCle(d.cle());
    v.setLibelle(Textes.couper(d.libelle() == null ? d.cle() : d.libelle(), 300));
    v.setPar(utilisateurs.getReferenceById(moi.id()));
    ignorees.save(v);
  }

  @Transactional
  public void retablir(String cle) {
    ignorees.deleteById(cle);
  }

  // ---------- Doublons ----------

  private static List<Point> doublonsEnseignants(List<Utilisateur> comptes, List<Matiere> matieresAnnee) {
    Map<String, Long> parProf = matieresAnnee.stream().filter(m -> m.getProfesseurId() != null).collect(Collectors.groupingBy(Matiere::getProfesseurId, Collectors.counting()));
    List<Point> points = new ArrayList<>();
    for (int[] paire : paires(comptes.stream().map(u -> u.getPrenom() + " " + u.getNom()).toList())) {
      Utilisateur a = comptes.get(paire[0]);
      Utilisateur b = comptes.get(paire[1]);
      Force f = Ressemblance.comparer(a.getPrenom() + " " + a.getNom(), b.getPrenom() + " " + b.getNom());
      // Le compte qui enseigne le plus est proposé comme compte gardé ; à égalité, le plus ancien.
      long ma = parProf.getOrDefault(a.getId(), 0L);
      long mb = parProf.getOrDefault(b.getId(), 0L);
      boolean aGarde = ma != mb ? ma > mb : !a.getCreeLe().isAfter(b.getCreeLe());
      Utilisateur garde = aGarde ? a : b;
      Utilisateur autre = aGarde ? b : a;
      points.add(
          new Point(
              cle("doublon-enseignant", a.getId(), b.getId()), f == Force.IDENTIQUE ? "FORTE" : "FAIBLE",
              garde.nomComplet() + " et " + autre.nomComplet(),
              decrire(garde, parProf) + " — " + decrire(autre, parProf),
              new Action("fusionner-enseignants", "/enseignants", garde.getId(), autre.getId()), false));
    }
    return points;
  }

  private static String decrire(Utilisateur u, Map<String, Long> parProf) {
    long n = parProf.getOrDefault(u.getId(), 0L);
    return joindre(u.getDiscipline(), n + " matière(s)", u.isActif() ? null : "désactivé", "créé le " + JOUR.format(u.getCreeLe().atZone(java.time.ZoneOffset.UTC)));
  }

  private static List<Point> doublonsEleves(List<Inscription> inscrits) {
    List<Eleve> liste = inscrits.stream().map(Inscription::getEleve).filter(e -> e.getFusionneDansId() == null).toList();
    Map<String, String> classeDe = inscrits.stream().collect(Collectors.toMap(i -> i.getEleve().getId(), i -> i.getClasse().getNom(), (x, y) -> x));
    List<Point> points = new ArrayList<>();
    for (int[] paire : paires(liste.stream().map(e -> e.getPrenoms() + " " + e.getNom()).toList())) {
      Eleve a = liste.get(paire[0]);
      Eleve b = liste.get(paire[1]);
      Force f = Ressemblance.comparer(a.getPrenoms() + " " + a.getNom(), b.getPrenoms() + " " + b.getNom());
      boolean memeNaissance = a.getDateNaissance().equals(b.getDateNaissance());
      // La fiche la plus ancienne est proposée comme fiche gardée.
      boolean aGarde = !a.getCreeLe().isAfter(b.getCreeLe());
      Eleve garde = aGarde ? a : b;
      Eleve autre = aGarde ? b : a;
      points.add(
          new Point(
              cle("doublon-eleve", a.getId(), b.getId()), f == Force.IDENTIQUE || memeNaissance ? "FORTE" : "FAIBLE",
              garde.nomComplet() + " et " + autre.nomComplet(),
              decrire(garde, classeDe) + " — " + decrire(autre, classeDe),
              new Action("fusionner-eleves", "/eleves/" + garde.getId(), garde.getId(), autre.getId()), false));
    }
    return points;
  }

  private static String decrire(Eleve e, Map<String, String> classeDe) {
    return joindre(e.getMatricule(), classeDe.get(e.getId()), "né(e) le " + JOUR.format(e.getDateNaissance()));
  }

  private static List<Point> doublonsMatieres(List<Matiere> matieresAnnee) {
    List<Point> points = new ArrayList<>();
    matieresAnnee.stream().collect(Collectors.groupingBy(Matiere::getClasseId)).values().forEach(
        liste -> {
          for (int[] paire : paires(liste.stream().map(Matiere::getNom).toList())) {
            Matiere a = liste.get(paire[0]);
            Matiere b = liste.get(paire[1]);
            points.add(
                new Point(
                    cle("doublon-matiere", a.getId(), b.getId()), "FAIBLE", a.getNom() + " et " + b.getNom() + " (" + a.getClasse().getNom() + ")",
                    "Coefficients " + a.getCoefficient() + " et " + b.getCoefficient() + ". Si c'est la même matière, supprimez celle qui n'a pas de notes.",
                    new Action("ouvrir", "/matieres?classe=" + a.getClasseId(), null, null), false));
          }
        });
    return points;
  }

  /**
   * Paires de noms qui se ressemblent. Seuls les noms qui partagent au moins un mot sont comparés : une
   * faute de frappe touche un mot, rarement tous.
   */
  static List<int[]> paires(List<String> noms) {
    Map<String, List<Integer>> parMot = new HashMap<>();
    for (int i = 0; i < noms.size(); i++) {
      for (String mot : new LinkedHashSet<>(List.of(Ressemblance.normaliser(noms.get(i)).split(" ")))) {
        if (mot.length() >= 2) parMot.computeIfAbsent(mot, k -> new ArrayList<>()).add(i);
      }
    }
    Set<Long> vues = new HashSet<>();
    List<int[]> paires = new ArrayList<>();
    for (List<Integer> groupe : parMot.values()) {
      for (int x = 0; x < groupe.size(); x++) {
        for (int y = x + 1; y < groupe.size(); y++) {
          int i = groupe.get(x);
          int j = groupe.get(y);
          if (!vues.add((long) i * 1_000_000 + j)) continue;
          if (Ressemblance.comparer(noms.get(i), noms.get(j)) != null) paires.add(new int[] {i, j});
        }
      }
    }
    paires.sort(Comparator.<int[]>comparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
    return paires;
  }

  // ---------- Organisation ----------

  private static List<Point> matieresEnseignantDesactive(List<Matiere> matieresAnnee) {
    return matieresAnnee.stream()
        .filter(m -> m.getProfesseur() != null && !m.getProfesseur().isActif())
        .map(
            m ->
                new Point(
                    "matiere-enseignant-desactive:" + m.getId() + ":" + m.getProfesseurId(), "FORTE",
                    m.getNom() + " (" + m.getClasse().getNom() + ")", "Confiée à " + m.getProfesseur().nomComplet() + ", compte désactivé.",
                    new Action("ouvrir", "/matieres?classe=" + m.getClasseId(), null, null), false))
        .toList();
  }

  private static List<Point> matieresSansEnseignant(List<Matiere> matieresAnnee) {
    return matieresAnnee.stream()
        .filter(m -> m.getProfesseur() == null)
        .map(
            m ->
                new Point(
                    "matiere-sans-enseignant:" + m.getId(), "FORTE", m.getNom() + " (" + m.getClasse().getNom() + ")",
                    "Coefficient " + m.getCoefficient() + ", groupe " + m.getGroupe().getNom() + ".",
                    new Action("ouvrir", "/matieres?classe=" + m.getClasseId(), null, null), false))
        .toList();
  }

  private static List<Point> classesSansPrincipal(List<Classe> classesAnnee) {
    return classesAnnee.stream()
        .filter(c -> c.getProfPrincipal() == null || !c.getProfPrincipal().isActif())
        .map(
            c ->
                new Point(
                    "classe-sans-pp:" + c.getId() + ":" + (c.getProfPrincipal() == null ? "" : c.getProfPrincipal().getId()), "FAIBLE", c.getNom(),
                    c.getProfPrincipal() == null ? "Aucun professeur principal." : c.getProfPrincipal().nomComplet() + ", compte désactivé.",
                    new Action("ouvrir", "/classes", null, null), false))
        .toList();
  }

  // ---------- Fiches d'élèves ----------

  private static List<Point> naissancesProvisoires(List<Inscription> inscrits) {
    return inscrits.stream()
        .filter(i -> i.getEleve().getDateNaissance().getDayOfMonth() == 1 && i.getEleve().getDateNaissance().getMonthValue() == 1)
        .map(
            i ->
                new Point(
                    "naissance:" + i.getEleve().getId() + ":" + i.getEleve().getDateNaissance(), "FAIBLE", i.getEleve().nomComplet() + " (" + i.getClasse().getNom() + ")",
                    "Né(e) le " + JOUR.format(i.getEleve().getDateNaissance()) + " à " + i.getEleve().getLieuNaissance() + ".",
                    new Action("ouvrir", "/eleves/" + i.getEleve().getId(), null, null), false))
        .toList();
  }

  private static List<Point> sansParent(List<Inscription> inscrits) {
    return inscrits.stream()
        .filter(i -> Textes.optionnel(i.getEleve().getParentNom()) == null && Textes.optionnel(i.getEleve().getParentTelephone()) == null)
        .map(
            i ->
                new Point(
                    "sans-parent:" + i.getEleve().getId(), "FAIBLE", i.getEleve().nomComplet() + " (" + i.getClasse().getNom() + ")", "Aucun parent renseigné.",
                    new Action("ouvrir", "/eleves/" + i.getEleve().getId(), null, null), false))
        .toList();
  }

  // ---------- Outils ----------

  private static String cle(String type, String a, String b) {
    return type + ":" + (a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a);
  }

  private static String joindre(String... morceaux) {
    return String.join(", ", java.util.Arrays.stream(morceaux).filter(m -> m != null && !m.isBlank()).toList());
  }

}
