package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.AnneeScolaire;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnneeScolaireRepository extends JpaRepository<AnneeScolaire, String> {

  Optional<AnneeScolaire> findFirstByActiveTrue();

  Optional<AnneeScolaire> findFirstByActiveTrueAndIdNot(String id);

  List<AnneeScolaire> findAllByOrderByLibelleDesc();

  /** Une seule année est en cours : toutes les autres sont désactivées. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update AnneeScolaire a set a.active = false where a.id <> :id")
  void desactiverSauf(String id);
}
