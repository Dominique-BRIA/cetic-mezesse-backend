package cm.cetic.mezesse.service;

import cm.cetic.mezesse.document.AttestationPdf;
import cm.cetic.mezesse.dto.AttestationDto.Vue;
import cm.cetic.mezesse.dto.DocumentDto.Fichier;
import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.entity.Attestation;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import cm.cetic.mezesse.repository.AttestationRepository;
import cm.cetic.mezesse.repository.CompteurRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Textes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attestations de présence effective au poste : le Proviseur les délivre aux personnels de
 * l'établissement. Chacune reçoit un numéro dans l'année scolaire et reste au registre ; les
 * informations imprimées sont gardées telles qu'à la délivrance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttestationService {

  private final AttestationRepository attestations;
  private final UtilisateurRepository utilisateurs;
  private final AnneeScolaireRepository annees;
  private final CompteurRepository compteurs;
  private final EtablissementService etablissement;

  public List<Vue> registre() {
    return attestations.registre().stream().map(Vue::de).toList();
  }

  @Transactional
  public Vue delivrer(UtilisateurConnecte moi, String utilisateurId) {
    Utilisateur u = utilisateurs.findById(utilisateurId).orElseThrow(() -> ApiException.introuvable("Compte"));
    if (u.getRole() == Role.PROVISEUR) throw ApiException.invalide("L'attestation du Proviseur est délivrée par sa hiérarchie.");
    List<String> manquants = new ArrayList<>();
    if (u.getMatricule() == null) manquants.add("matricule");
    if (u.getGrade() == null) manquants.add("grade");
    if (u.getFonction() == null) manquants.add("fonction");
    if (u.getDatePriseService() == null) manquants.add("date de prise de service");
    if (!manquants.isEmpty()) {
      throw ApiException.invalide("Complétez d'abord la carrière de " + u.nomComplet() + " : " + String.join(", ", manquants) + ".");
    }
    AnneeScolaire annee =
        annees.findFirstByActiveTrue().orElseThrow(() -> ApiException.conflit("Aucune année scolaire active : le Surveillant Général doit en créer une."));
    Etablissement etab = etablissement.requis();
    Utilisateur proviseur = utilisateurs.findById(moi.id()).orElseThrow();
    int numero = compteurs.incrementer("attestation-" + annee.getLibelle());

    Attestation a = new Attestation();
    a.setNumero(numero);
    a.setAnneeLibelle(annee.getLibelle());
    a.setReference(String.format(Locale.ROOT, "N° %03d/%s/%s/P", numero, annee.getLibelle(), Textes.capitales(etab.getSigle())));
    a.setUtilisateur(u);
    a.setBeneficiaire(Textes.majuscules(u.getNom()) + " " + u.getPrenom());
    a.setSexe(u.getSexe());
    a.setMatricule(u.getMatricule());
    a.setGrade(u.getGrade());
    a.setFonction(u.getFonction());
    a.setDatePriseService(u.getDatePriseService());
    a.setActeNomination(u.getActeNomination());
    a.setSignataire(proviseur.getPrenom() + " " + Textes.majuscules(proviseur.getNom()));
    a.setPar(proviseur);
    return Vue.de(attestations.saveAndFlush(a));
  }

  /** Le PDF de l'attestation, identique à chaque impression. */
  public Fichier pdf(String id) {
    Attestation a = attestations.findById(id).orElseThrow(() -> ApiException.introuvable("Attestation"));
    Etablissement etab = etablissement.requis();
    byte[] contenu = AttestationPdf.generer(etab, a, a.getPar().getSexe());
    return new Fichier(contenu, "attestation-presence-" + Textes.nomFichier(a.getBeneficiaire()) + "-" + a.getNumero(), 0);
  }
}
