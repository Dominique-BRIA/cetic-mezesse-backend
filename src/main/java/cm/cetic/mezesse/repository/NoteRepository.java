package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.StatutInscription;
import cm.cetic.mezesse.entity.Note;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NoteRepository extends JpaRepository<Note, String> {

  @Query(
      """
      select new cm.cetic.mezesse.repository.NoteCalcul(n.inscription.id, n.matiere.id, s.numero, n.valeur)
      from Note n join n.sequence s
      where n.matiere.classe.id = :classeId
        and n.inscription.statut = :actif
        and s.numero in :numeros
      """)
  List<NoteCalcul> pourCalculSelon(String classeId, Collection<Integer> numeros, StatutInscription actif);

  default List<NoteCalcul> pourCalcul(String classeId, Collection<Integer> numeros) {
    return pourCalculSelon(classeId, numeros, StatutInscription.ACTIF);
  }

  List<Note> findByMatiere_IdAndSequence_Id(String matiereId, String sequenceId);

  Optional<Note> findByInscription_IdAndMatiere_IdAndSequence_Id(String inscriptionId, String matiereId, String sequenceId);

  @Modifying
  @Query("delete from Note n where n.inscription.id = :inscriptionId and n.matiere.id = :matiereId and n.sequence.id = :sequenceId")
  void effacer(String inscriptionId, String matiereId, String sequenceId);

  long countByMatiere_Id(String matiereId);

  long countByInscription_Id(String inscriptionId);

  long countByInscription_IdIn(Collection<String> inscriptionIds);

  long countByInscription_Eleve_Id(String eleveId);

  long countByInscription_Eleve_IdIn(Collection<String> eleveIds);

  long countBySaisiePar_Id(String utilisateurId);

  @Query(
      "select new cm.cetic.mezesse.repository.Compte(n.matiere.id, count(n)) from Note n where n.matiere.id in :matiereIds group by n.matiere.id")
  List<Compte> compterParMatiere(Collection<String> matiereIds);

  /** Notes d'élèves actifs, par matière et par séquence (suivi de la saisie). */
  @Query(
      """
      select n.matiere.id, n.sequence.id, count(n) from Note n
      where n.matiere.id in :matiereIds and n.inscription.statut = :actif
      group by n.matiere.id, n.sequence.id
      """)
  List<Object[]> compterParMatiereEtSequenceSelon(Collection<String> matiereIds, StatutInscription actif);

  default List<Object[]> compterParMatiereEtSequence(Collection<String> matiereIds) {
    return compterParMatiereEtSequenceSelon(matiereIds, StatutInscription.ACTIF);
  }
}
