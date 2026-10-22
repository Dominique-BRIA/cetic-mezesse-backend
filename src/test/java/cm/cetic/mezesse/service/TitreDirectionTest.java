package cm.cetic.mezesse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Sexe;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Titre imprimé sur les cartes, attestations et certificats (RG-33). */
class TitreDirectionTest {

  static Utilisateur directeur(Sexe sexe, boolean actif) {
    Utilisateur u = new Utilisateur();
    u.setRole(Role.PROVISEUR);
    u.setSexe(sexe);
    u.setActif(actif);
    return u;
  }

  static TitreDirection avec(Utilisateur... comptes) {
    UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    when(utilisateurs.findByRoleOrderByNomAscPrenomAsc(Role.PROVISEUR)).thenReturn(List.of(comptes));
    return new TitreDirection(utilisateurs);
  }

  @Test
  void civilite_non_renseignee_donne_le_directeur() {
    assertThat(avec(directeur(null, true)).avecArticleEnFonction()).isEqualTo("Le Directeur");
  }

  @Test
  void madame_donne_la_directrice() {
    assertThat(avec(directeur(Sexe.F, true)).avecArticleEnFonction()).isEqualTo("La Directrice");
  }

  @Test
  void seul_le_compte_actif_compte() {
    assertThat(avec(directeur(Sexe.M, false), directeur(Sexe.F, true)).titreEnFonction()).isEqualTo("Directrice");
    assertThat(avec(directeur(Sexe.F, false)).titreEnFonction()).isEqualTo("Directeur");
  }
}
