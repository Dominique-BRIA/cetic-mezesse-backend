package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "AnneeScolaire")
@Getter
@Setter
public class AnneeScolaire {

  @Id @Cuid private String id;

  /** « 2026-2027 ». */
  private String libelle;

  private boolean active;

  @Column(updatable = false)
  private Instant creeLe;

  @OneToMany(mappedBy = "annee", cascade = CascadeType.PERSIST)
  @OrderBy("numero ASC")
  private List<Sequence> sequences = new ArrayList<>();

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
