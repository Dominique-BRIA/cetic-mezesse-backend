package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, String> {

  List<Notification> findTop30ByDestinataire_IdOrderByCreeLeDesc(String destinataireId);

  long countByDestinataire_IdAndLueFalse(String destinataireId);

  @Modifying
  @Query("update Notification n set n.lue = true where n.destinataire.id = :destinataireId and n.lue = false")
  void toutMarquerLu(String destinataireId);

  @Modifying
  @Query("update Notification n set n.lue = true where n.id = :id and n.destinataire.id = :destinataireId")
  void marquerLue(String id, String destinataireId);
}
