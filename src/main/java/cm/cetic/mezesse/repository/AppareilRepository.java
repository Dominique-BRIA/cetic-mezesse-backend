package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Appareil;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppareilRepository extends JpaRepository<Appareil, String> {

  Optional<Appareil> findByInstallationId(String installationId);

  @Query("select a from Appareil a left join fetch a.utilisateur order by a.derniereVue desc")
  List<Appareil> tousAvecCompte();
}
