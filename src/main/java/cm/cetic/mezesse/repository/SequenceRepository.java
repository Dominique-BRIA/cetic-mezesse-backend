package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Sequence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SequenceRepository extends JpaRepository<Sequence, String> {

  List<Sequence> findByAnnee_IdOrderByNumeroAsc(String anneeId);

  List<Sequence> findByAnnee_IdInOrderByNumeroAsc(List<String> anneeIds);
}
