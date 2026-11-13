package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Navigateur où un compte a accepté les notifications push du site. */
@Entity
@Table(name = "JetonWeb")
@Getter
@Setter
public class JetonWeb {

  @Id @Cuid private String id;

  private String jeton;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "utilisateurId")
  private Utilisateur utilisateur;

  /** « Chrome sur Windows »… pour s'y retrouver, à titre indicatif. */
  private String navigateur;

  @Column(updatable = false)
  private Instant creeLe;

  private Instant vuLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
    if (vuLe == null) vuLe = creeLe;
  }
}
