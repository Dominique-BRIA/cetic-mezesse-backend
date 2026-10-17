package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Decision;
import cm.cetic.mezesse.entity.StatutDecision;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DecisionRepository extends JpaRepository<Decision, String>, JpaSpecificationExecutor<Decision> {

  long countByStatutAndCreeLeGreaterThanEqual(StatutDecision statut, Instant depuis);

  long countByStatut(StatutDecision statut);
}
