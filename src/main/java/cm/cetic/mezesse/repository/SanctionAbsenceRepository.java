package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.SanctionAbsence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SanctionAbsenceRepository extends JpaRepository<SanctionAbsence, String> {

  List<SanctionAbsence> findAllByOrderByMinutesAsc();

  /** Suppression immédiate : la nouvelle liste réutilise les mêmes heures (contrainte d'unicité). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from SanctionAbsence s")
  void toutSupprimer();
}
