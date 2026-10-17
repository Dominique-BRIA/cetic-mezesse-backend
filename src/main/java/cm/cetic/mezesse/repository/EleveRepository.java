package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Eleve;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EleveRepository extends JpaRepository<Eleve, String> {

  List<Eleve> findByIdIn(Collection<String> ids);

  List<Eleve> findByIdInAndPhotoCleIsNotNull(Collection<String> ids);

  /** Suppression directe ; inscriptions, renvois et notes suivent (ON DELETE CASCADE). */
  @Modifying
  @Query("delete from Eleve e where e.id in :ids")
  int supprimerParIds(Collection<String> ids);
}
