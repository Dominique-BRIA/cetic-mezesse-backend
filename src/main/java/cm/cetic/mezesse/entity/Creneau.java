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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** Un créneau de la semaine type : jour (1 = lundi … 6 = samedi), heures de début et de fin, matière. */
@Entity
@Table(name = "Creneau")
@Getter
@Setter
public class Creneau {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "emploiId")
  private EmploiDuTemps emploi;

  @Column(name = "emploiId", insertable = false, updatable = false)
  private String emploiId;

  /** La matière donne la classe et l'enseignant. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "matiereId")
  private Matiere matiere;

  @Column(name = "matiereId", insertable = false, updatable = false)
  private String matiereId;

  private int jour;
  private LocalTime debut;
  private LocalTime fin;

  @Column(updatable = false)
  private Instant creeLe;

  /** Durée réelle, en minutes. */
  public int duree() {
    return (int) Duration.between(debut, fin).toMinutes();
  }

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
