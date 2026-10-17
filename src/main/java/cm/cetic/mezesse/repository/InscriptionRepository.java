package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.entity.Inscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InscriptionRepository extends JpaRepository<Inscription, String>, JpaSpecificationExecutor<Inscription> {

  Optional<Inscription> findByEleve_IdAndAnnee_Id(String eleveId, String anneeId);

  /** Élèves actifs d'une classe, dans l'ordre alphabétique. */
  @Query(
      """
      select i from Inscription i join fetch i.eleve e
      where i.classe.id = :classeId and i.statut = :actif
      order by e.nom, e.prenoms
      """)
  List<Inscription> activesDeLaClasseSelon(String classeId, StatutInscription actif);

  default List<Inscription> activesDeLaClasse(String classeId) {
    return activesDeLaClasseSelon(classeId, StatutInscription.ACTIF);
  }

  /** Élèves actifs d'une classe sans note dans une matière pour une séquence. */
  @Query(
      """
      select i from Inscription i join fetch i.eleve e
      where i.classe.id = :classeId and i.statut = :actif
        and not exists (select n.id from Note n where n.inscription = i and n.matiere.id = :matiereId and n.sequence.id = :sequenceId)
      order by e.nom, e.prenoms
      """)
  List<Inscription> sansNoteSelon(String classeId, String matiereId, String sequenceId, StatutInscription actif);

  default List<Inscription> sansNote(String classeId, String matiereId, String sequenceId) {
    return sansNoteSelon(classeId, matiereId, sequenceId, StatutInscription.ACTIF);
  }

  @Query(
      """
      select i.id from Inscription i
      where i.id in :ids and i.classe.id = :classeId and i.statut = :actif
      """)
  List<String> activesParmiSelon(Collection<String> ids, String classeId, StatutInscription actif);

  default List<String> activesParmi(Collection<String> ids, String classeId) {
    return activesParmiSelon(ids, classeId, StatutInscription.ACTIF);
  }

  @Query("select i from Inscription i where i.id in :ids and i.statut = :actif")
  List<Inscription> activesParIdsSelon(Collection<String> ids, StatutInscription actif);

  default List<Inscription> activesParIds(Collection<String> ids) {
    return activesParIdsSelon(ids, StatutInscription.ACTIF);
  }

  /** Élèves actifs des classes entières et des élèves choisis un à un (cartes scolaires). */
  @Query(
      """
      select i from Inscription i join fetch i.eleve e join fetch i.classe c join fetch i.annee
      where i.statut = :actif and (c.id in :classeIds or i.id in :inscriptionIds)
      order by c.niveau, c.nom, e.nom, e.prenoms
      """)
  List<Inscription> selectionSelon(Collection<String> classeIds, Collection<String> inscriptionIds, StatutInscription actif);

  default List<Inscription> selection(Collection<String> classeIds, Collection<String> inscriptionIds) {
    return selectionSelon(classeIds, inscriptionIds, StatutInscription.ACTIF);
  }

  @Query("select i.eleve.id from Inscription i where i.annee.id = :anneeId and i.eleve.id in :eleveIds")
  List<String> elevesDejaInscrits(String anneeId, Collection<String> eleveIds);

  /** Inscriptions d'un élève, de la plus récente année à la plus ancienne. */
  @Query(
      """
      select i from Inscription i join fetch i.classe join fetch i.annee a
      where i.eleve.id = :eleveId order by a.libelle desc
      """)
  List<Inscription> deLEleve(String eleveId);

  long countByClasse_Id(String classeId);

  long countByClasse_IdAndStatut(String classeId, cm.cetic.mezesse.entity.StatutInscription statut);

  long countByAnnee_Id(String anneeId);

  @Query(
      """
      select new cm.cetic.mezesse.repository.Compte(i.classe.id, count(i)) from Inscription i
      where i.classe.id in :classeIds and i.statut = :actif group by i.classe.id
      """)
  List<Compte> activesParClasseSelon(Collection<String> classeIds, StatutInscription actif);

  default List<Compte> activesParClasse(Collection<String> classeIds) {
    return activesParClasseSelon(classeIds, StatutInscription.ACTIF);
  }

  @Query(
      """
      select new cm.cetic.mezesse.repository.Compte(i.classe.id, count(i)) from Inscription i
      where i.annee.id = :anneeId and i.statut = :actif group by i.classe.id
      """)
  List<Compte> activesParClasseDeLAnneeSelon(String anneeId, StatutInscription actif);

  default List<Compte> activesParClasseDeLAnnee(String anneeId) {
    return activesParClasseDeLAnneeSelon(anneeId, StatutInscription.ACTIF);
  }

  @Query("select new cm.cetic.mezesse.repository.Compte(i.annee.id, count(i)) from Inscription i group by i.annee.id")
  List<Compte> compterParAnnee();

  @Query(
      "select new cm.cetic.mezesse.repository.Compte(cast(i.statut as String), count(i)) from Inscription i where i.annee.id = :anneeId group by i.statut")
  List<Compte> compterParStatut(String anneeId);

  @Modifying
  @Query("delete from Inscription i where i.id in :ids")
  int supprimerParIds(Collection<String> ids);
}
