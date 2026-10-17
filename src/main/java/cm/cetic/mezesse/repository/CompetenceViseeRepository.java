package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.CompetenceVisee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompetenceViseeRepository extends JpaRepository<CompetenceVisee, CompetenceVisee.Cle> {

  /** Compétences visées des matières d'une classe pour une séquence de l'année. */
  @Query(
      """
      select c from CompetenceVisee c
      where c.matiereId in (select m.id from Matiere m where m.classe.id = :classeId)
        and c.sequenceId in (select s.id from Sequence s where s.annee.id = :anneeId and s.numero = :numero)
      """)
  List<CompetenceVisee> deLaClasse(String classeId, String anneeId, int numero);
}
