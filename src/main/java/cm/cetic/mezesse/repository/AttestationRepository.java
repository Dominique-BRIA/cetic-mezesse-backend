package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Attestation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttestationRepository extends JpaRepository<Attestation, String> {

  /** Registre : les plus récentes d'abord. */
  @Query("select a from Attestation a join fetch a.par order by a.delivreeLe desc")
  List<Attestation> registre();
}
