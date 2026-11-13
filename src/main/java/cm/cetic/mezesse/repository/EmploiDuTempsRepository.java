package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.EmploiDuTemps;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmploiDuTempsRepository extends JpaRepository<EmploiDuTemps, String> {

  /** Versions d'une classe, de la plus ancienne (« depuis la rentrée ») à la plus récente. */
  @Query("select e from EmploiDuTemps e where e.classe.id = :classeId order by e.valableDu asc nulls first")
  List<EmploiDuTemps> deLaClasse(String classeId);

  /** Versions de toutes les classes de l'année : pour repérer les enseignants attendus à deux endroits à la fois. */
  @Query("select e from EmploiDuTemps e join fetch e.classe c where c.annee.id = :anneeId order by e.valableDu asc nulls first")
  List<EmploiDuTemps> deLAnnee(String anneeId);
}
