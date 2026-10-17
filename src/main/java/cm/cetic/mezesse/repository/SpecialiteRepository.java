package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Specialite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialiteRepository extends JpaRepository<Specialite, String> {

  List<Specialite> findAllByOrderByNomAsc();
}
