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
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Une version de l'emploi du temps d'une classe : la semaine type valable à partir de « valableDu »
 * (vide : depuis la rentrée), jusqu'à la version suivante.
 */
@Entity
@Table(name = "EmploiDuTemps")
@Getter
@Setter
public class EmploiDuTemps {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "classeId")
  private Classe classe;

  @Column(name = "classeId", insertable = false, updatable = false)
  private String classeId;

  private LocalDate valableDu;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
