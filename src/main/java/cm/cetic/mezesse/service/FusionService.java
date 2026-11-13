package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.FusionDto.Annee;
import cm.cetic.mezesse.dto.FusionDto.ApercuEleves;
import cm.cetic.mezesse.dto.FusionDto.ApercuEnseignants;
import cm.cetic.mezesse.dto.FusionDto.Champ;
import cm.cetic.mezesse.dto.FusionDto.DemandeEleves;
import cm.cetic.mezesse.dto.FusionDto.DemandeEnseignants;
import cm.cetic.mezesse.dto.FusionDto.Deplacement;
import cm.cetic.mezesse.dto.FusionDto.Fiche;
import cm.cetic.mezesse.dto.FusionDto.Resultat;
import cm.cetic.mezesse.entity.DomaineDecision;
import cm.cetic.mezesse.entity.Eleve;
import cm.cetic.mezesse.entity.Inscription;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.EleveRepository;
import cm.cetic.mezesse.repository.InscriptionRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Donnees;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fusion de deux fiches en double. La fiche gardée reçoit tout ce qui était rattaché à l'autre
 * (matières, notes, inscriptions…) et, pour chaque information qui diffère, la valeur choisie. L'autre
 * fiche n'est pas supprimée : elle est désactivée et marquée « fusionnée », et la décision garde la
 * liste exacte de ce qui a bougé, pour que le Proviseur puisse tout défaire en la désapprouvant.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FusionService {

  private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private static final Map<String, String> CHAMPS_ENSEIGNANT = libelles(
      "nom", "Nom", "prenom", "Prénom(s)", "telephone", "Téléphone", "email", "E-mail", "discipline", "Discipline", "sexe", "Sexe",
      "matricule", "Matricule", "grade", "Grade", "fonction", "Fonction", "datePriseService", "Prise de service",
      "dateEntreeFonctionPublique", "Entrée dans la fonction publique", "acteNomination", "Acte de nomination");

  private static final Map<String, String> CHAMPS_ELEVE = libelles(
      "nom", "Nom", "prenoms", "Prénoms", "sexe", "Sexe", "dateNaissance", "Date de naissance", "lieuNaissance", "Lieu de naissance",
      "matriculeNational", "Matricule national", "parentNom", "Parent", "parentTelephone", "Téléphone du parent",
      "parentAdresse", "Adresse du parent", "photoCle", "Photo");

  /** Colonnes qui désignent un compte, reportées sur le compte gardé : table, colonne, libellé. */
  private static final List<String[]> REFERENCES_COMPTE = List.of(
      new String[] {"Matiere", "professeurId", "matière(s) enseignée(s)"},
      new String[] {"Classe", "profPrincipalId", "classe(s) dont il est professeur principal"},
      new String[] {"Note", "saisieParId", "note(s) saisie(s)"},
      new String[] {"Attestation", "utilisateurId", "attestation(s) de présence"},
      new String[] {"Attestation", "parId", "attestation(s) délivrée(s)"},
      new String[] {"Renvoi", "parId", "renvoi(s) prononcé(s)"},
      new String[] {"Renvoi", "reintegreParId", "réintégration(s)"},
      new String[] {"Appareil", "utilisateurId", "téléphone(s) enregistré(s)"},
      new String[] {"Notification", "destinataireId", "notification(s)"},
      new String[] {"Seance", "enseignantId", "séance(s) sur les feuilles d'appel"},
      new String[] {"Seance", "remplacantId", "remplacement(s)"});

  private final UtilisateurRepository utilisateurs;
  private final EleveRepository eleves;
  private final InscriptionRepository inscriptions;
  private final JournalDecisions journal;
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate sql;
  private final EntityManager em;

  // ---------- Enseignants ----------

  public ApercuEnseignants apercuEnseignants(String gardeId, String autreId) {
    Utilisateur g = compte(gardeId);
    Utilisateur a = compte(autreId);
    verifier(g, a);
    List<Deplacement> deplacements = new ArrayList<>();
    for (String[] r : REFERENCES_COMPTE) {
      Long n = jdbc.queryForObject("SELECT COUNT(*) FROM \"" + r[0] + "\" WHERE \"" + r[1] + "\" = ?", Long.class, a.getId());
      if (n != null && n > 0) deplacements.add(new Deplacement(r[2], n));
    }
    List<String> avertissements = new ArrayList<>();
    avertissements.add("Le compte de " + a.nomComplet() + " sera désactivé : la personne se connectera avec le compte gardé et son mot de passe.");
    if (!a.isDoitChangerMotDePasse() && g.isDoitChangerMotDePasse()) {
      avertissements.add("Le mot de passe personnel est sur le compte absorbé ; le compte gardé a encore le mot de passe par défaut.");
    }
    return new ApercuEnseignants(fiche(g), fiche(a), differences(UtilisateurService.champs(g), UtilisateurService.champs(a), CHAMPS_ENSEIGNANT), deplacements, avertissements);
  }

  @Transactional
  public Resultat fusionnerEnseignants(UtilisateurConnecte moi, DemandeEnseignants d) {
    Utilisateur g = compte(d.garde());
    Utilisateur a = compte(d.autre());
    verifier(g, a);
    String nomG = g.nomComplet();
    String nomA = a.nomComplet();
    Map<String, Object> gardeAvant = UtilisateurService.champs(g);
    Map<String, Object> autreAvant = UtilisateurService.champs(a);
    Map<String, Object> retenus = retenus(gardeAvant, autreAvant, CHAMPS_ENSEIGNANT, d.choix());

    // Téléphone, e-mail et matricule sont uniques : le compte absorbé les libère avant que le gardé les prenne.
    if (retenus.containsKey("telephone")) a.setTelephone(null);
    if (retenus.containsKey("email")) a.setEmail(null);
    if (retenus.containsKey("matricule")) a.setMatricule(null);
    a.setActif(false);
    a.setFusionneDansId(g.getId());
    a.fermerSessions();
    utilisateurs.saveAndFlush(a);
    appliquer(g, retenus);
    utilisateurs.saveAndFlush(g);
    Map<String, Object> gardeApres = UtilisateurService.champs(g);
    em.flush();

    Map<String, Object> deplaces = new LinkedHashMap<>();
    List<String> resume = new ArrayList<>();
    for (String[] r : REFERENCES_COMPTE) {
      List<String> ids =
          jdbc.queryForList("UPDATE \"" + r[0] + "\" SET \"" + r[1] + "\" = ? WHERE \"" + r[1] + "\" = ? RETURNING id", String.class, g.getId(), a.getId());
      deplaces.put(r[0] + "." + r[1], ids);
      if (!ids.isEmpty()) resume.add(ids.size() + " " + r[2]);
    }
    em.clear();

    journal.consigner(
        moi, DomaineDecision.ORGANISATION, "enseignant.fusionner",
        "Fusion du compte de " + nomA + " dans celui de " + nomG + (resume.isEmpty() ? "" : " : " + String.join(", ", resume)), "/enseignants",
        Donnees.objet("gardeId", d.garde(), "autreId", d.autre(), "gardeAvant", gardeAvant, "gardeApres", gardeApres, "autreAvant", autreAvant, "deplaces", deplaces));
    return new Resultat(
        "Comptes réunis : " + nomG + " garde " + (resume.isEmpty() ? "ses informations" : String.join(", ", resume) + " de l'autre compte") + ". Le compte de " + nomA + " est désactivé.",
        d.garde());
  }

  /** Défait une fusion d'enseignants (désapprobation du Proviseur). */
  @Transactional(propagation = Propagation.MANDATORY)
  public String defaireEnseignants(Map<String, Object> donnees) {
    String gardeId = texte(donnees.get("gardeId"));
    String autreId = texte(donnees.get("autreId"));
    Utilisateur autre = utilisateurs.findById(autreId).orElseThrow(() -> ApiException.conflit("Le compte absorbé n'existe plus."));
    if (!gardeId.equals(autre.getFusionneDansId())) throw ApiException.conflit("Ce compte n'est plus fusionné : rien à défaire.");
    Utilisateur garde = utilisateurs.findById(gardeId).orElseThrow(() -> ApiException.conflit("Le compte gardé n'existe plus."));

    // Le compte gardé rend d'abord ce qu'il avait pris (téléphone, e-mail : uniques), puis l'autre les retrouve.
    appliquer(garde, AnnulationService.aRestaurer(UtilisateurService.champs(garde), carte(donnees.get("gardeAvant")), carte(donnees.get("gardeApres"))));
    utilisateurs.saveAndFlush(garde);
    Map<String, Object> autreAvant = carte(donnees.get("autreAvant"));
    List<String> nonRendus = new ArrayList<>();
    if (autre.getTelephone() == null && autreAvant.get("telephone") != null) {
      if (libre("telephone", texte(autreAvant.get("telephone")))) autre.setTelephone(texte(autreAvant.get("telephone")));
      else nonRendus.add("téléphone");
    }
    if (autre.getEmail() == null && autreAvant.get("email") != null) {
      if (libre("email", texte(autreAvant.get("email")))) autre.setEmail(texte(autreAvant.get("email")));
      else nonRendus.add("e-mail");
    }
    if (autre.getMatricule() == null && autreAvant.get("matricule") != null) {
      if (libre("matricule", texte(autreAvant.get("matricule")))) autre.setMatricule(texte(autreAvant.get("matricule")));
      else nonRendus.add("matricule");
    }
    autre.setActif(Boolean.TRUE.equals(autreAvant.get("actif")));
    autre.setFusionneDansId(null);
    utilisateurs.saveAndFlush(autre);
    em.flush();

    carte(donnees.get("deplaces")).forEach(
        (cle, ids) -> {
          List<String> liste = textes(ids);
          if (liste.isEmpty()) return;
          String[] tc = cle.split("\\.");
          sql.update(
              "UPDATE \"" + tc[0] + "\" SET \"" + tc[1] + "\" = :autre WHERE id IN (:ids) AND \"" + tc[1] + "\" = :garde",
              Map.of("autre", autreId, "garde", gardeId, "ids", liste));
        });
    String nom = autre.nomComplet();
    em.clear();
    return "Les deux comptes sont de nouveau séparés : " + nom + " retrouve son compte, ses matières et ses notes"
        + (nonRendus.isEmpty() ? "." : " (" + String.join(", ", nonRendus) + " déjà repris par un autre compte, à ressaisir).");
  }

  private boolean libre(String colonne, String valeur) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM \"Utilisateur\" WHERE \"" + colonne + "\" = ?", Long.class, valeur);
    return n == null || n == 0;
  }

  private Utilisateur compte(String id) {
    return utilisateurs.findById(id).orElseThrow(() -> ApiException.introuvable("Compte"));
  }

  private static void verifier(Utilisateur g, Utilisateur a) {
    if (g.getId().equals(a.getId())) throw ApiException.invalide("Choisissez deux comptes différents.");
    if (g.getRole() != Role.PROFESSEUR || a.getRole() != Role.PROFESSEUR) throw ApiException.interdit("Seuls deux comptes d'enseignants peuvent être fusionnés.");
    if (g.getFusionneDansId() != null || a.getFusionneDansId() != null) throw ApiException.conflit("L'un de ces comptes a déjà été fusionné dans un autre.");
  }

  private static Fiche fiche(Utilisateur u) {
    return new Fiche(u.getId(), u.nomComplet(), joindre(u.getDiscipline(), u.getTelephone(), u.isActif() ? null : "compte désactivé", "créé le " + JOUR.format(u.getCreeLe().atZone(java.time.ZoneOffset.UTC))));
  }

  private static void appliquer(Utilisateur u, Map<String, Object> data) {
    if (data.containsKey("nom")) u.setNom(texte(data.get("nom")));
    if (data.containsKey("prenom")) u.setPrenom(texte(data.get("prenom")));
    if (data.containsKey("telephone")) u.setTelephone(texte(data.get("telephone")));
    if (data.containsKey("email")) u.setEmail(texte(data.get("email")));
    if (data.containsKey("discipline")) u.setDiscipline(texte(data.get("discipline")));
    if (data.containsKey("sexe")) u.setSexe(data.get("sexe") == null ? null : Sexe.valueOf(texte(data.get("sexe"))));
    if (data.containsKey("matricule")) u.setMatricule(texte(data.get("matricule")));
    if (data.containsKey("grade")) u.setGrade(texte(data.get("grade")));
    if (data.containsKey("fonction")) u.setFonction(texte(data.get("fonction")));
    if (data.containsKey("datePriseService")) u.setDatePriseService(date(data.get("datePriseService")));
    if (data.containsKey("dateEntreeFonctionPublique")) u.setDateEntreeFonctionPublique(date(data.get("dateEntreeFonctionPublique")));
    if (data.containsKey("acteNomination")) u.setActeNomination(texte(data.get("acteNomination")));
  }

  // ---------- Élèves ----------

  private record Plan(Inscription autre, Inscription garde, long notesAutre, long conflits) {
    String sort() {
      if (garde == null) return "déplacée";
      return garde.getClasse().getId().equals(autre.getClasse().getId()) ? "réunie" : "impossible";
    }
  }

  private List<Plan> plan(Eleve g, Eleve a) {
    List<Plan> plan = new ArrayList<>();
    for (Inscription ia : inscriptions.findByEleve_IdOrderByCreeLe(a.getId())) {
      Inscription ig = inscriptions.findByEleve_IdAndAnnee_Id(g.getId(), ia.getAnnee().getId()).orElse(null);
      Long notesAutre = jdbc.queryForObject("SELECT COUNT(*) FROM \"Note\" WHERE \"inscriptionId\" = ?", Long.class, ia.getId());
      Long conflits =
          ig == null
              ? 0L
              : jdbc.queryForObject(
                  """
                  SELECT COUNT(*) FROM "Note" n WHERE n."inscriptionId" = ? AND EXISTS (
                    SELECT 1 FROM "Note" m WHERE m."inscriptionId" = ? AND m."matiereId" = n."matiereId" AND m."sequenceId" = n."sequenceId")
                  """,
                  Long.class, ia.getId(), ig.getId());
      plan.add(new Plan(ia, ig, notesAutre == null ? 0 : notesAutre, conflits == null ? 0 : conflits));
    }
    return plan;
  }

  public ApercuEleves apercuEleves(String gardeId, String autreId) {
    Eleve g = eleve(gardeId);
    Eleve a = eleve(autreId);
    verifier(g, a);
    List<Plan> plan = plan(g, a);
    List<Annee> annees =
        plan.stream()
            .map(p -> new Annee(p.autre().getAnnee().getLibelle(), p.garde() == null ? null : p.garde().getClasse().getNom(), p.autre().getClasse().getNom(), p.notesAutre(), p.conflits(), p.sort()))
            .toList();
    List<String> avertissements = new ArrayList<>();
    for (Plan p : plan) {
      if (p.sort().equals("impossible")) {
        avertissements.add(
            "En " + p.autre().getAnnee().getLibelle() + ", les deux fiches sont dans deux classes différentes (" + p.garde().getClasse().getNom() + " et "
                + p.autre().getClasse().getNom() + ") : mettez-les d'abord dans la même classe.");
      }
    }
    long conflits = plan.stream().mapToLong(Plan::conflits).sum();
    if (conflits > 0) avertissements.add(conflits + " note(s) existent sur les deux fiches pour la même matière et la même séquence : choisissez lesquelles garder.");
    avertissements.add("La fiche de " + a.nomComplet() + " (" + a.getMatricule() + ") disparaîtra des listes ; le matricule gardé est " + g.getMatricule() + ".");
    return new ApercuEleves(
        fiche(g), fiche(a), differences(champs(g), champs(a), CHAMPS_ELEVE), annees, conflits, avertissements,
        plan.stream().noneMatch(p -> p.sort().equals("impossible")));
  }

  @Transactional
  public Resultat fusionnerEleves(UtilisateurConnecte moi, DemandeEleves d) {
    Eleve g = eleve(d.garde());
    Eleve a = eleve(d.autre());
    verifier(g, a);
    List<Plan> plan = plan(g, a);
    for (Plan p : plan) {
      if (p.sort().equals("impossible")) {
        throw ApiException.conflit(
            "En " + p.autre().getAnnee().getLibelle() + ", les deux fiches sont dans deux classes différentes : mettez-les d'abord dans la même classe.");
      }
    }
    String nomG = g.nomComplet() + " (" + g.getMatricule() + ")";
    String nomA = a.nomComplet() + " (" + a.getMatricule() + ")";
    Map<String, Object> gardeAvant = champs(g);
    Map<String, Object> autreAvant = champs(a);
    Map<String, Object> retenus = retenus(gardeAvant, autreAvant, CHAMPS_ELEVE, d.choix());
    if (retenus.containsKey("matriculeNational")) a.setMatriculeNational(null);
    a.setFusionneDansId(g.getId());
    eleves.saveAndFlush(a);
    appliquer(g, retenus);
    eleves.saveAndFlush(g);
    Map<String, Object> gardeApres = champs(g);
    List<String[]> parcours = plan.stream().map(p -> new String[] {p.autre().getId(), p.garde() == null ? null : p.garde().getId()}).toList();
    em.flush();

    boolean notesDeLAutre = "autre".equals(d.notes());
    List<String> inscriptionsDeplacees = new ArrayList<>();
    List<Map<String, Object>> reunions = new ArrayList<>();
    int reprises = 0;
    int remplacees = 0;
    for (String[] p : parcours) {
      String ia = p[0];
      String ig = p[1];
      if (ig == null) {
        jdbc.update("UPDATE \"Inscription\" SET \"eleveId\" = ? WHERE id = ?", g.getId(), ia);
        inscriptionsDeplacees.add(ia);
        continue;
      }
      Map<String, Object> inscription =
          jdbc.queryForMap("SELECT id, \"eleveId\", \"anneeId\", \"classeId\", redoublant, statut::text AS statut, \"creeLe\"::text AS \"creeLe\" FROM \"Inscription\" WHERE id = ?", ia);
      Map<String, Map<String, Object>> notesGarde = new HashMap<>();
      for (Map<String, Object> n : notes(ig)) notesGarde.put(n.get("matiereId") + "|" + n.get("sequenceId"), n);
      List<String> deplacees = new ArrayList<>();
      List<Map<String, Object>> supprimees = new ArrayList<>();
      List<Map<String, Object>> ecrasees = new ArrayList<>();
      for (Map<String, Object> n : notes(ia)) {
        Map<String, Object> ng = notesGarde.get(n.get("matiereId") + "|" + n.get("sequenceId"));
        if (ng == null) {
          deplacees.add(texte(n.get("id")));
          continue;
        }
        if (notesDeLAutre) {
          ecrasees.add(ng);
          jdbc.update(
              "UPDATE \"Note\" SET valeur = ?::numeric, \"saisieParId\" = ?, \"majLe\" = ?::timestamp WHERE id = ?",
              n.get("valeur"), n.get("saisieParId"), n.get("majLe"), ng.get("id"));
          remplacees++;
        }
        supprimees.add(n);
        jdbc.update("DELETE FROM \"Note\" WHERE id = ?", n.get("id"));
      }
      if (!deplacees.isEmpty()) sql.update("UPDATE \"Note\" SET \"inscriptionId\" = :ig WHERE id IN (:ids)", Map.of("ig", ig, "ids", deplacees));
      reprises += deplacees.size();
      List<String> renvois = jdbc.queryForList("UPDATE \"Renvoi\" SET \"inscriptionId\" = ? WHERE \"inscriptionId\" = ? RETURNING id", String.class, ig, ia);
      // Absences : reportées sur la fiche gardée ; si les deux fiches étaient absentes à la même séance, une seule reste.
      List<String> absencesDeplacees = jdbc.queryForList(
          """
          UPDATE "Absence" a SET "inscriptionId" = ? WHERE a."inscriptionId" = ?
            AND NOT EXISTS (SELECT 1 FROM "Absence" b WHERE b."inscriptionId" = ? AND b."seanceId" = a."seanceId") RETURNING a.id
          """,
          String.class, ig, ia, ig);
      List<Map<String, Object>> absencesDoublees =
          jdbc.queryForList("SELECT id, \"seanceId\", \"parId\", \"creeLe\"::text AS \"creeLe\" FROM \"Absence\" WHERE \"inscriptionId\" = ?", ia);
      jdbc.update("DELETE FROM \"Inscription\" WHERE id = ?", ia);
      reunions.add(Donnees.objet(
          "inscription", inscription, "vers", ig, "notes", deplacees, "renvois", renvois, "supprimees", supprimees, "ecrasees", ecrasees,
          "absences", absencesDeplacees, "absencesDoublees", absencesDoublees));
    }
    em.clear();

    List<String> resume = new ArrayList<>();
    if (!inscriptionsDeplacees.isEmpty()) resume.add(inscriptionsDeplacees.size() + " inscription(s) reprise(s)");
    if (reprises > 0) resume.add(reprises + " note(s) reprise(s)");
    if (remplacees > 0) resume.add(remplacees + " note(s) remplacée(s)");
    journal.consigner(
        moi, DomaineDecision.SCOLARITE, "eleve.fusionner",
        "Fusion de la fiche de " + nomA + " dans celle de " + nomG + (resume.isEmpty() ? "" : " : " + String.join(", ", resume)), "/eleves/" + d.garde(),
        Donnees.objet(
            "gardeId", d.garde(), "autreId", d.autre(), "gardeAvant", gardeAvant, "gardeApres", gardeApres, "autreAvant", autreAvant,
            "inscriptionsDeplacees", inscriptionsDeplacees, "reunions", reunions));
    return new Resultat("Fiches réunies : " + nomG + " garde tout" + (resume.isEmpty() ? "" : " (" + String.join(", ", resume) + ")") + ".", d.garde());
  }

  /** Défait une fusion d'élèves (désapprobation du Proviseur). */
  @Transactional(propagation = Propagation.MANDATORY)
  public String defaireEleves(Map<String, Object> donnees) {
    String gardeId = texte(donnees.get("gardeId"));
    String autreId = texte(donnees.get("autreId"));
    Eleve autre = eleves.findById(autreId).orElseThrow(() -> ApiException.conflit("La fiche absorbée n'existe plus."));
    if (!gardeId.equals(autre.getFusionneDansId())) throw ApiException.conflit("Cette fiche n'est plus fusionnée : rien à défaire.");
    Eleve garde = eleves.findById(gardeId).orElseThrow(() -> ApiException.conflit("La fiche gardée n'existe plus."));

    appliquer(garde, AnnulationService.aRestaurer(champs(garde), carte(donnees.get("gardeAvant")), carte(donnees.get("gardeApres"))));
    eleves.saveAndFlush(garde);
    Map<String, Object> autreAvant = carte(donnees.get("autreAvant"));
    String mn = texte(autreAvant.get("matriculeNational"));
    if (autre.getMatriculeNational() == null && mn != null) {
      Long pris = jdbc.queryForObject("SELECT COUNT(*) FROM \"Eleve\" WHERE \"matriculeNational\" = ?", Long.class, mn);
      if (pris == null || pris == 0) autre.setMatriculeNational(mn);
    }
    autre.setFusionneDansId(null);
    eleves.saveAndFlush(autre);
    em.flush();

    List<Object> reunions = new ArrayList<>(liste(donnees.get("reunions")));
    Collections.reverse(reunions);
    for (Object o : reunions) {
      Map<String, Object> r = carte(o);
      Map<String, Object> ins = carte(r.get("inscription"));
      String ia = texte(ins.get("id"));
      String ig = texte(r.get("vers"));
      jdbc.update(
          "INSERT INTO \"Inscription\" (id, \"eleveId\", \"anneeId\", \"classeId\", redoublant, statut, \"creeLe\") VALUES (?, ?, ?, ?, ?, ?::\"StatutInscription\", ?::timestamp)",
          ia, autreId, ins.get("anneeId"), ins.get("classeId"), Boolean.TRUE.equals(ins.get("redoublant")), ins.get("statut"), ins.get("creeLe"));
      List<String> notes = textes(r.get("notes"));
      if (!notes.isEmpty()) {
        sql.update("UPDATE \"Note\" SET \"inscriptionId\" = :ia WHERE id IN (:ids) AND \"inscriptionId\" = :ig", Map.of("ia", ia, "ig", ig, "ids", notes));
      }
      List<String> renvois = textes(r.get("renvois"));
      if (!renvois.isEmpty()) {
        sql.update("UPDATE \"Renvoi\" SET \"inscriptionId\" = :ia WHERE id IN (:ids) AND \"inscriptionId\" = :ig", Map.of("ia", ia, "ig", ig, "ids", renvois));
      }
      List<String> abs = textes(r.get("absences"));
      if (!abs.isEmpty()) {
        sql.update("UPDATE \"Absence\" SET \"inscriptionId\" = :ia WHERE id IN (:ids) AND \"inscriptionId\" = :ig", Map.of("ia", ia, "ig", ig, "ids", abs));
      }
      for (Object ab : liste(r.get("absencesDoublees"))) {
        Map<String, Object> a = carte(ab);
        jdbc.update(
            """
            INSERT INTO "Absence" (id, "seanceId", "inscriptionId", "parId", "creeLe")
            SELECT ?, ?, ?, ?, ?::timestamp WHERE EXISTS (SELECT 1 FROM "Seance" WHERE id = ?) ON CONFLICT DO NOTHING
            """,
            a.get("id"), a.get("seanceId"), ia, a.get("parId"), a.get("creeLe"), a.get("seanceId"));
      }
      for (Object s : liste(r.get("supprimees"))) {
        Map<String, Object> n = carte(s);
        jdbc.update(
            "INSERT INTO \"Note\" (id, \"inscriptionId\", \"matiereId\", \"sequenceId\", valeur, \"saisieParId\", \"majLe\") VALUES (?, ?, ?, ?, ?::numeric, ?, ?::timestamp)",
            n.get("id"), ia, n.get("matiereId"), n.get("sequenceId"), n.get("valeur"), n.get("saisieParId"), n.get("majLe"));
      }
      for (Object e : liste(r.get("ecrasees"))) {
        Map<String, Object> n = carte(e);
        jdbc.update(
            "UPDATE \"Note\" SET valeur = ?::numeric, \"saisieParId\" = ?, \"majLe\" = ?::timestamp WHERE id = ?",
            n.get("valeur"), n.get("saisieParId"), n.get("majLe"), n.get("id"));
      }
    }
    List<String> deplacees = textes(donnees.get("inscriptionsDeplacees"));
    if (!deplacees.isEmpty()) {
      sql.update("UPDATE \"Inscription\" SET \"eleveId\" = :autre WHERE id IN (:ids) AND \"eleveId\" = :garde", Map.of("autre", autreId, "garde", gardeId, "ids", deplacees));
    }
    String nom = autre.nomComplet();
    em.clear();
    return "Les deux fiches sont de nouveau séparées : " + nom + " retrouve son inscription et ses notes.";
  }

  private List<Map<String, Object>> notes(String inscriptionId) {
    return jdbc.queryForList(
        "SELECT id, \"matiereId\", \"sequenceId\", valeur::text AS valeur, \"saisieParId\", \"majLe\"::text AS \"majLe\" FROM \"Note\" WHERE \"inscriptionId\" = ?",
        inscriptionId);
  }

  private Eleve eleve(String id) {
    return eleves.findById(id).orElseThrow(() -> ApiException.introuvable("Élève"));
  }

  private static void verifier(Eleve g, Eleve a) {
    if (g.getId().equals(a.getId())) throw ApiException.invalide("Choisissez deux fiches différentes.");
    if (g.getFusionneDansId() != null || a.getFusionneDansId() != null) throw ApiException.conflit("L'une de ces fiches a déjà été fusionnée dans une autre.");
  }

  static Map<String, Object> champs(Eleve e) {
    Map<String, Object> m = new LinkedHashMap<>(EleveService.fiche(e));
    m.put("photoCle", e.getPhotoCle());
    return m;
  }

  private static Fiche fiche(Eleve e) {
    return new Fiche(e.getId(), e.nomComplet(), joindre(e.getMatricule(), "né(e) le " + JOUR.format(e.getDateNaissance()), e.getLieuNaissance()));
  }

  private static void appliquer(Eleve e, Map<String, Object> data) {
    if (data.containsKey("nom")) e.setNom(texte(data.get("nom")));
    if (data.containsKey("prenoms")) e.setPrenoms(texte(data.get("prenoms")));
    if (data.containsKey("sexe")) e.setSexe(Sexe.valueOf(texte(data.get("sexe"))));
    if (data.containsKey("dateNaissance")) e.setDateNaissance(date(data.get("dateNaissance")));
    if (data.containsKey("lieuNaissance")) e.setLieuNaissance(texte(data.get("lieuNaissance")));
    if (data.containsKey("matriculeNational")) e.setMatriculeNational(texte(data.get("matriculeNational")));
    if (data.containsKey("parentNom")) e.setParentNom(texte(data.get("parentNom")));
    if (data.containsKey("parentTelephone")) e.setParentTelephone(texte(data.get("parentTelephone")));
    if (data.containsKey("parentAdresse")) e.setParentAdresse(texte(data.get("parentAdresse")));
    if (data.containsKey("photoCle")) e.setPhotoCle(texte(data.get("photoCle")));
  }

  // ---------- Choix des informations ----------

  static List<Champ> differences(Map<String, Object> garde, Map<String, Object> autre, Map<String, String> libelles) {
    List<Champ> r = new ArrayList<>();
    libelles.forEach(
        (cle, libelle) -> {
          Object g = garde.get(cle);
          Object a = autre.get(cle);
          if (AnnulationService.egal(g, a)) return;
          r.add(new Champ(cle, libelle, affichage(cle, g), affichage(cle, a), vide(g) ? "autre" : "garde"));
        });
    return r;
  }

  /** Valeurs de l'autre fiche retenues pour la fiche gardée, selon les choix (ou le choix par défaut). */
  static Map<String, Object> retenus(Map<String, Object> garde, Map<String, Object> autre, Map<String, String> libelles, Map<String, String> choix) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (String cle : libelles.keySet()) {
      Object g = garde.get(cle);
      Object a = autre.get(cle);
      if (AnnulationService.egal(g, a)) continue;
      String c = choix == null ? null : choix.get(cle);
      if (c != null && !c.equals("garde") && !c.equals("autre")) throw ApiException.invalide("choix." + cle + " : garde ou autre");
      if (Objects.requireNonNullElse(c, vide(g) ? "autre" : "garde").equals("autre")) r.put(cle, a);
    }
    return r;
  }

  private static boolean vide(Object v) {
    return v == null || v.toString().isBlank();
  }

  private static String affichage(String cle, Object v) {
    if (vide(v)) return "—";
    if (cle.equals("photoCle")) return "photo enregistrée";
    if (cle.equals("sexe")) return "M".equals(v.toString()) ? "Masculin" : "Féminin";
    String s = v.toString();
    if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) return JOUR.format(LocalDate.parse(s.substring(0, 10)));
    return s;
  }

  private static Map<String, String> libelles(String... clesLibelles) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < clesLibelles.length; i += 2) m.put(clesLibelles[i], clesLibelles[i + 1]);
    return m;
  }

  private static String joindre(String... morceaux) {
    return String.join(", ", java.util.stream.Stream.of(morceaux).filter(m -> m != null && !m.isBlank()).toList());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> carte(Object v) {
    return v == null ? new LinkedHashMap<>() : new LinkedHashMap<>((Map<String, Object>) v);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> liste(Object v) {
    return v == null ? List.of() : (List<Object>) v;
  }

  private static List<String> textes(Object v) {
    return liste(v).stream().map(o -> o == null ? null : o.toString()).toList();
  }

  private static String texte(Object v) {
    return v == null ? null : v.toString();
  }

  private static LocalDate date(Object v) {
    String s = texte(v);
    return s == null ? null : LocalDate.parse(s.substring(0, 10));
  }
}
