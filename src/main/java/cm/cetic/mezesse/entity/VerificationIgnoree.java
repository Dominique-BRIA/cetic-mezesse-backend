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

/** Point de la page « À vérifier » jugé normal par la direction : il n'est plus signalé. */
@Entity
@Table(name = "VerificationIgnoree")
@Getter
@Setter
public class VerificationIgnoree {

  /** « doublon-enseignant:id1:id2 », « naissance:idEleve »… */
  @Id private String cle;

  private String libelle;

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
