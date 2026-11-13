package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Creneau;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreneauRepository extends JpaRepository<Creneau, String> {

  /** Créneaux de versions d'emploi du temps, avec leur matière, sa classe et son enseignant. */
  @Query(
      """
      select c from Creneau c join fetch c.matiere m join fetch m.classe left join fetch m.professeur
      where c.emploi.id in :emploiIds
      order by c.jour, c.debut
      """)
  List<Creneau> desVersions(Collection<String> emploiIds);

  long countByEmploi_Id(String emploiId);
}
