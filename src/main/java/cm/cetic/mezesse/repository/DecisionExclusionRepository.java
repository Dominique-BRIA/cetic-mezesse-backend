package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.DecisionExclusion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionExclusionRepository extends JpaRepository<DecisionExclusion, String> {

  List<DecisionExclusion> findByInscriptionIdInOrderByCreeLeAsc(Collection<String> inscriptionIds);

  /** Parmi ces élèves, ceux qui ont été exclus définitivement (une année ou une autre). */
  @org.springframework.data.jpa.repository.Query(
      value =
          "SELECT DISTINCT i.\"eleveId\" FROM \"DecisionExclusion\" d JOIN \"Inscription\" i ON i.id = d.\"inscriptionId\""
              + " WHERE d.type = 'DEFINITIVE' AND i.\"eleveId\" IN (:eleveIds)",
      nativeQuery = true)
  List<String> elevesExclusDefinitivement(Collection<String> eleveIds);
}
