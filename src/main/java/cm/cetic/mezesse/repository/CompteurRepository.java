package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Compteur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompteurRepository extends JpaRepository<Compteur, String> {

  /**
   * Incrémente le compteur et renvoie sa nouvelle valeur, en une seule instruction : deux inscriptions
   * simultanées ne peuvent pas recevoir le même numéro.
   */
  @Query(
      value =
          """
          insert into "Compteur" ("cle", "valeur") values (:cle, 1)
          on conflict ("cle") do update set "valeur" = "Compteur"."valeur" + 1
          returning "valeur"
          """,
      nativeQuery = true)
  int incrementer(String cle);
}
