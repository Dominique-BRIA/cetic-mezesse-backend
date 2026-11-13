package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.JetonWeb;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JetonWebRepository extends JpaRepository<JetonWeb, String> {

  Optional<JetonWeb> findByJeton(String jeton);

  /** [id, jeton] des navigateurs des comptes donnés. */
  @Query("select j.id, j.jeton from JetonWeb j where j.utilisateur.id in :utilisateurIds")
  List<Object[]> jetonsDe(Collection<String> utilisateurIds);

  /** Jeton refusé par Firebase (navigateur désabonné) : il n'est plus utilisé. */
  @Modifying
  @Transactional
  @Query("delete from JetonWeb j where j.id = :id")
  int oublier(String id);

  /** Déconnexion : le navigateur ne reçoit plus les notifications de ce compte. */
  @Modifying
  @Query("delete from JetonWeb j where j.jeton = :jeton and j.utilisateur.id = :utilisateurId")
  int retirer(String jeton, String utilisateurId);
}
