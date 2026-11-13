package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Justification acceptée d'une absence : l'absence passe de NON_JUSTIFIEE à JUSTIFIEE, sans disparaître. */
@Entity
@Table(name = "Justification")
@Getter
@Setter
public class Justification {

  @Id @Cuid private String id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "absenceId")
  private Absence absence;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "motifId")
  private MotifJustification motif;

  private String description;
  private LocalDate dateReception;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parId")
  private Utilisateur par;

  @Column(updatable = false)
  private Instant creeLe;

  private Instant modifieLe;

  @OneToMany(mappedBy = "justification", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("creeLe")
  private List<PieceJustificative> pieces = new ArrayList<>();

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
