package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.PieceJustificative;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PieceJustificativeRepository extends JpaRepository<PieceJustificative, String> {

  /** Le même fichier peut servir à plusieurs justifications (absences justifiées ensemble). */
  long countByCle(String cle);

  @org.springframework.data.jpa.repository.Query("select p.cle from PieceJustificative p where p.justification.absence.id = :absenceId")
  java.util.List<String> clesDeLAbsence(String absenceId);
}
