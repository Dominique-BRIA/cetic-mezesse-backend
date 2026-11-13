package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Appareil;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AppareilRepository extends JpaRepository<Appareil, String> {

  Optional<Appareil> findByInstallationId(String installationId);

  /** [id de l'appareil, jeton push] des téléphones des comptes donnés. */
  @Query("select a.id, a.jetonPush from Appareil a where a.utilisateur.id in :utilisateurIds and a.jetonPush is not null")
  List<Object[]> jetonsDe(Collection<String> utilisateurIds);

  /** Jeton refusé par Firebase (application désinstallée) : il n'est plus utilisé. */
  @Modifying
  @Transactional
  @Query("update Appareil a set a.jetonPush = null where a.id = :id and a.jetonPush = :jeton")
  int oublierJeton(String id, String jeton);

  @Query("select a from Appareil a left join fetch a.utilisateur order by a.derniereVue desc")
  List<Appareil> tousAvecCompte();
}
