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

/** Feuille d'appel d'une classe pour un jour, créée quand le SG enregistre l'appel ; ensuite, corrections historisées. */
@Entity
@Table(name = "FicheAppel")
@Getter
@Setter
public class FicheAppel {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "classeId")
  private Classe classe;

  private LocalDate date;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sequenceId")
  private Sequence sequence;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creeParId")
  private Utilisateur creePar;

  @Column(updatable = false)
  private Instant creeLe;

  private Instant modifieLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
