package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.HistoriqueAbsence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HistoriqueAbsenceRepository extends JpaRepository<HistoriqueAbsence, String> {

  @Query("select h from HistoriqueAbsence h left join fetch h.par where h.ficheId = :ficheId order by h.creeLe desc")
  List<HistoriqueAbsence> deLaFiche(String ficheId);

  @Query("select h from HistoriqueAbsence h left join fetch h.par where h.absenceId = :absenceId order by h.creeLe desc")
  List<HistoriqueAbsence> deLAbsence(String absenceId);
}
