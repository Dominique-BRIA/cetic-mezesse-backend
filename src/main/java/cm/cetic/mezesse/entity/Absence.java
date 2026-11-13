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

/** Un élève absent à une séance. Tout élève sans absence est présent. */
@Entity
@Table(name = "Absence")
@Getter
@Setter
public class Absence {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seanceId")
  private Seance seance;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inscriptionId")
  private Inscription inscription;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parId")
  private Utilisateur par;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
