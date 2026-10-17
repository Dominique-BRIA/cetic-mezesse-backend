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

/** Renvoi d'un élève par le Proviseur, et sa réintégration éventuelle. */
@Entity
@Table(name = "Renvoi")
@Getter
@Setter
public class Renvoi {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inscriptionId")
  private Inscription inscription;

  private LocalDate date;
  private String motif;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "parId")
  private Utilisateur par;

  @Column(updatable = false)
  private Instant creeLe;

  private Instant reintegreLe;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reintegreParId")
  private Utilisateur reintegrePar;

  private String motifReintegration;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
