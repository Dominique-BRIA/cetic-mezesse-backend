package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.GroupeMatiere;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupeMatiereRepository extends JpaRepository<GroupeMatiere, String> {

  List<GroupeMatiere> findByAnnee_IdOrderByOrdreAsc(String anneeId);

  /** Le groupe est-il rattaché à la classe ? */
  boolean existsByIdAndClasses_Id(String groupeId, String classeId);
}
