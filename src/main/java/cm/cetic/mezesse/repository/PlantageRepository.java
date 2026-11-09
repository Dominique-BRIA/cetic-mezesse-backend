package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Plantage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlantageRepository extends JpaRepository<Plantage, String> {

  Optional<Plantage> findByEmpreinteAndInstallationIdAndNumeroVersion(String empreinte, String installationId, int numeroVersion);

  List<Plantage> findByEmpreinteOrderByDerniereFoisDesc(String empreinte);

  List<Plantage> findAllByOrderByDerniereFoisDesc();

  @Modifying
  @Query("update Plantage p set p.resolu = :resolu where p.empreinte = :empreinte")
  int marquer(String empreinte, boolean resolu);

  @Modifying
  @Query("delete from Plantage p where p.derniereFois < :limite")
  void supprimerAvant(Instant limite);
}
