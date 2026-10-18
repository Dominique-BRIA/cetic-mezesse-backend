package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, String> {

  /** L'identifiant de connexion est un e-mail ou un numéro de téléphone. */
  @Query("select u from Utilisateur u where u.email = :identifiant or u.telephone = :identifiant order by u.creeLe")
  List<Utilisateur> parIdentifiant(String identifiant);

  boolean existsByRole(Role role);

  long countByRoleAndActifTrue(Role role);

  List<Utilisateur> findAllByOrderByRoleAscNomAscPrenomAsc();

  List<Utilisateur> findByRoleOrderByNomAscPrenomAsc(Role role);

  Optional<Utilisateur> findFirstByIdNotAndTelephone(String id, String telephone);

  Optional<Utilisateur> findFirstByIdNotAndEmail(String id, String email);

  Optional<Utilisateur> findFirstByIdNotAndMatricule(String id, String matricule);

  /** Professeurs actifs qui ont au moins une matière dans l'année. */
  @Query(
      """
      select distinct p.id from Matiere m join m.professeur p
      where m.classe.annee.id = :anneeId and p.role = :role and p.actif = true
      """)
  List<String> professeursActifsDeLAnneeSelon(String anneeId, Role role);

  default List<String> professeursActifsDeLAnnee(String anneeId) {
    return professeursActifsDeLAnneeSelon(anneeId, Role.PROFESSEUR);
  }
}
