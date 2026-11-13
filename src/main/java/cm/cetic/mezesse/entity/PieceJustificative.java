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

/** Pièce justificative (certificat, mot des parents…) : le fichier est dans le stockage, sous « cle ». */
@Entity
@Table(name = "PieceJustificative")
@Getter
@Setter
public class PieceJustificative {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "justificationId")
  private Justification justification;

  private String nomFichier;
  private String cle;
  private String typeMime;
  private int taille;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
