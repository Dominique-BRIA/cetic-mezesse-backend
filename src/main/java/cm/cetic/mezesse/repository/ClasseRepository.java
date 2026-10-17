package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Classe;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClasseRepository extends JpaRepository<Classe, String> {

  @Query(
      """
      select c from Classe c left join fetch c.specialite s left join fetch c.profPrincipal
      where c.annee.id = :anneeId order by c.niveau, s.nom, c.numero
      """)
  List<Classe> deLAnnee(String anneeId);

  /** Classes où le professeur enseigne au moins une matière. */
  @Query(
      """
      select c from Classe c left join fetch c.specialite s left join fetch c.profPrincipal
      where c.annee.id = :anneeId
        and exists (select m.id from Matiere m where m.classe = c and m.professeur.id = :professeurId)
      order by c.niveau, s.nom, c.numero
      """)
  List<Classe> deLAnneePourProfesseur(String anneeId, String professeurId);

  List<Classe> findByAnnee_IdOrderByNiveauAscNomAsc(String anneeId);

  @Query("select c from Classe c where c.id in :ids order by c.niveau, c.nom")
  List<Classe> parIdsTriees(Collection<String> ids);

  /** Classes d'un même niveau et d'une même spécialité dans l'année : elles partagent les lettres A, B… */
  @Query(
      """
      select c from Classe c where c.annee.id = :anneeId and c.niveau = :niveau
        and ((:specialiteId is null and c.specialite is null) or c.specialite.id = :specialiteId)
      order by c.numero, c.nom
      """)
  List<Classe> duGroupe(String anneeId, int niveau, String specialiteId);

  @Query(
      """
      select coalesce(max(c.numero), 0) from Classe c where c.annee.id = :anneeId and c.niveau = :niveau
        and ((:specialiteId is null and c.specialite is null) or c.specialite.id = :specialiteId)
      """)
  int numeroMax(String anneeId, int niveau, String specialiteId);

  /** Couples (année, niveau) des classes d'une spécialité. */
  @Query("select distinct c.annee.id, c.niveau from Classe c where c.specialite.id = :specialiteId")
  List<Object[]> groupesDeLaSpecialite(String specialiteId);

  long countBySpecialite_Id(String specialiteId);

  long countByIdAndAnnee_Id(String id, String anneeId);

  long countByAnnee_Id(String anneeId);

  @Query("select new cm.cetic.mezesse.repository.Compte(c.annee.id, count(c)) from Classe c group by c.annee.id")
  List<Compte> compterParAnnee();

  @Query(
      "select new cm.cetic.mezesse.repository.Compte(c.specialite.id, count(c)) from Classe c where c.specialite is not null group by c.specialite.id")
  List<Compte> compterParSpecialite();

  @Query("select c from Classe c where c.annee.id = :anneeId and c.profPrincipal.id in :professeurIds")
  List<Classe> principalesDe(String anneeId, Collection<String> professeurIds);
}
