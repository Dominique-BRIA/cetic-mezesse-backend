package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.MotifJustification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotifJustificationRepository extends JpaRepository<MotifJustification, String> {

  List<MotifJustification> findAllByOrderByOrdreAscLibelleAsc();

  boolean existsByLibelleIgnoreCaseAndIdNot(String libelle, String id);
}
