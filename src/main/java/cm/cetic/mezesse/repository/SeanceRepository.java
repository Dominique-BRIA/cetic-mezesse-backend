package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Seance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeanceRepository extends JpaRepository<Seance, String> {

  /** Séances d'une feuille, dans l'ordre de la journée, avec leurs enseignants. */
  @Query(
      """
      select s from Seance s left join fetch s.matiere left join fetch s.enseignant left join fetch s.remplacant
      where s.fiche.id = :ficheId order by s.debut, s.fin
      """)
  List<Seance> deLaFiche(String ficheId);
}
