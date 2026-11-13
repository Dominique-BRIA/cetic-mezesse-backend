package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.FicheAppel;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FicheAppelRepository extends JpaRepository<FicheAppel, String> {

  Optional<FicheAppel> findByClasse_IdAndDate(String classeId, LocalDate date);
}
