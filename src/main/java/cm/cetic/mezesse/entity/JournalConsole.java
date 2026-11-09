package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Trace de chaque connexion et de chaque action faite depuis la console technique. */
@Entity
@Table(name = "JournalConsole")
@Getter
@Setter
public class JournalConsole {

  @Id @Cuid private String id;

  @Column(updatable = false)
  private Instant date;

  private String adminEmail;
  private String action;
  private String cible;
  private String detail;
  private String adresse;

  @PrePersist
  void avantCreation() {
    if (date == null) date = Horloge.maintenant();
  }
}
