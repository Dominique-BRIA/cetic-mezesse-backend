package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Renvoi;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RenvoiRepository extends JpaRepository<Renvoi, String> {

  /** Renvois des inscriptions, du plus récent au plus ancien, avec leurs auteurs. */
  @Query(
      """
      select r from Renvoi r join fetch r.par left join fetch r.reintegrePar
      where r.inscription.id in :inscriptionIds order by r.creeLe desc
      """)
  List<Renvoi> desInscriptions(Collection<String> inscriptionIds);

  /** Renvois en cours (pas encore réintégrés) d'une inscription, le plus récent d'abord. */
  List<Renvoi> findByInscription_IdAndReintegreLeIsNullOrderByCreeLeDesc(String inscriptionId);
}
