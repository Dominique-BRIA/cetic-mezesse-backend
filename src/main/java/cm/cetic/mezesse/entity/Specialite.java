package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Spécialité créée par le SG (« Mécanique », « Électricité ») ; elle sert d'une année sur l'autre. */
@Entity
@Table(name = "Specialite")
@Getter
@Setter
public class Specialite {

  @Id @Cuid private String id;

  private String nom;
  private String nomEn;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
