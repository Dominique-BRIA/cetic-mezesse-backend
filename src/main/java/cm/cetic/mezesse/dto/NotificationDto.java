package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Notification;
import java.time.Instant;
import java.util.List;

public final class NotificationDto {

  private NotificationDto() {}

  public record Vue(
      String id, String destinataireId, String titre, String message, String lien, boolean lue, Instant creeLe) {

    public static Vue de(Notification n) {
      return new Vue(n.getId(), n.getDestinataire().getId(), n.getTitre(), n.getMessage(), n.getLien(), n.isLue(), n.getCreeLe());
    }
  }

  public record Liste(List<Vue> notifications, long nonLues) {}
}
