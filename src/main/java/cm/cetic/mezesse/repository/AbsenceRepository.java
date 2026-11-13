package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Absence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AbsenceRepository extends JpaRepository<Absence, String> {

  /** Absences relevées sur une feuille d'appel. */
  @Query("select a from Absence a join fetch a.seance s join fetch a.inscription i join fetch i.eleve where s.fiche.id = :ficheId")
  List<Absence> deLaFiche(String ficheId);

  List<Absence> findBySeance_Id(String seanceId);

  /** Absences avec leur élève, leur séance et leur feuille (justifications, historique). */
  @Query(
      """
      select a from Absence a join fetch a.inscription i join fetch i.eleve join fetch a.seance s join fetch s.fiche f join fetch f.classe
      where a.id in :ids
      """)
  List<Absence> avecEleve(java.util.Collection<String> ids);
}
