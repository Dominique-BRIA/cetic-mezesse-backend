package cm.cetic.mezesse.service;

import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Titre du chef d'établissement tel qu'il s'imprime et s'affiche. Au CETIC, c'est le Directeur ou la
 * Directrice (« The Principal » en anglais) ; le rôle garde son nom technique, PROVISEUR.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TitreDirection {

  private final UtilisateurRepository utilisateurs;

  public static String titre(Sexe sexe) {
    return sexe == Sexe.F ? "Directrice" : "Directeur";
  }

  /** « Le Directeur », « La Directrice ». */
  public static String avecArticle(Sexe sexe) {
    return sexe == Sexe.F ? "La Directrice" : "Le Directeur";
  }

  /** Sexe du chef d'établissement en fonction (compte actif), null s'il n'est pas renseigné. */
  public Sexe sexeEnFonction() {
    return utilisateurs.findByRoleOrderByNomAscPrenomAsc(Role.PROVISEUR).stream()
        .filter(u -> u.isActif())
        .map(u -> u.getSexe())
        .findFirst()
        .orElse(null);
  }

  public String titreEnFonction() {
    return titre(sexeEnFonction());
  }

  public String avecArticleEnFonction() {
    return avecArticle(sexeEnFonction());
  }
}
