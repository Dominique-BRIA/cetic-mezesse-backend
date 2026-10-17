package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Matiere;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatiereRepository extends JpaRepository<Matiere, String> {

  /** Matières de l'année, éventuellement d'une classe ou d'un professeur. */
  @Query(
      """
      select m from Matiere m join fetch m.classe c join fetch m.groupe g left join fetch m.professeur
      where c.annee.id = :anneeId
        and (:classeId is null or c.id = :classeId)
        and (:professeurId is null or m.professeur.id = :professeurId)
      order by c.niveau, g.ordre, m.ordre
      """)
  List<Matiere> deLAnnee(String anneeId, String classeId, String professeurId);

  /** Ordre du suivi de la saisie : par niveau de classe, puis ordre de la matière. */
  @Query(
      """
      select m from Matiere m join fetch m.classe c left join fetch m.professeur
      where c.annee.id = :anneeId
        and (:matiereId is null or m.id = :matiereId)
        and (:classeId is null or c.id = :classeId)
        and (:professeurId is null or m.professeur.id = :professeurId)
      order by c.niveau, m.ordre
      """)
  List<Matiere> pourSuivi(String anneeId, String matiereId, String classeId, String professeurId);

  /** Tout ce qu'il faut pour calculer les résultats d'une classe. */
  @Query("select m from Matiere m join fetch m.groupe left join fetch m.professeur where m.classe.id = :classeId")
  List<Matiere> deLaClassePourCalcul(String classeId);

  List<Matiere> findByClasse_Id(String classeId);

  @Query(
      """
      select m from Matiere m join fetch m.classe c where c.annee.id = :anneeId and m.professeur.id in :professeurIds
      order by c.niveau
      """)
  List<Matiere> enseigneesPar(String anneeId, Collection<String> professeurIds);

  long countByGroupe_Id(String groupeId);

  long countByGroupe_IdAndClasse_IdNotIn(String groupeId, Collection<String> classeIds);

  /** Noms des classes dont des matières sont dans le groupe, hors des classes données. */
  @Query("select distinct m.classe.nom from Matiere m where m.groupe.id = :groupeId and m.classe.id not in :classeIds")
  List<String> classesHorsListe(String groupeId, Collection<String> classeIds);

  @Query(
      "select new cm.cetic.mezesse.repository.Compte(m.classe.id, count(m)) from Matiere m where m.classe.id in :classeIds group by m.classe.id")
  List<Compte> compterParClasse(Collection<String> classeIds);

  @Query(
      "select new cm.cetic.mezesse.repository.Compte(m.groupe.id, count(m)) from Matiere m where m.groupe.id in :groupeIds group by m.groupe.id")
  List<Compte> compterParGroupe(Collection<String> groupeIds);

  long countByGroupe_IdAndClasse_IdIn(String groupeId, Collection<String> classeIds);
}
