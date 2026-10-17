package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Bareme;
import cm.cetic.mezesse.entity.TypeBareme;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BaremeRepository extends JpaRepository<Bareme, String> {

  List<Bareme> findAllByOrderByTypeAscMinDesc();

  List<Bareme> findByType(TypeBareme type);

  /** Suppression immédiate : le nouveau barème réutilise les mêmes libellés (contrainte d'unicité). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Bareme b where b.type = :type")
  void supprimerType(TypeBareme type);
}
