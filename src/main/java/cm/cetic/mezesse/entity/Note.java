package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Note sur 20 (deux décimales) d'un élève, dans une matière, pour une séquence. */
@Entity
@Table(name = "Note")
@Getter
@Setter
public class Note {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inscriptionId")
  private Inscription inscription;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "matiereId")
  private Matiere matiere;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sequenceId")
  private Sequence sequence;

  private BigDecimal valeur;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "saisieParId")
  private Utilisateur saisiePar;

  private Instant majLe;

  @PrePersist
  @PreUpdate
  void horodater() {
    majLe = Horloge.maintenant();
  }
}
