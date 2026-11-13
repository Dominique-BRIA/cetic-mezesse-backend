package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Justification;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JustificationRepository extends JpaRepository<Justification, String> {

  /** Justifications de plusieurs absences, avec leur motif, leur auteur et leurs pièces. */
  @Query(
      """
      select distinct j from Justification j join fetch j.motif left join fetch j.par left join fetch j.pieces
      where j.absence.id in :absenceIds
      """)
  List<Justification> desAbsences(Collection<String> absenceIds);

  /** Absences justifiées d'une feuille d'appel : séance et inscription. */
  @Query("select j.absence.seance.id, j.absence.inscription.id from Justification j where j.absence.seance.fiche.id = :ficheId")
  List<Object[]> casesJustifiees(String ficheId);
}
