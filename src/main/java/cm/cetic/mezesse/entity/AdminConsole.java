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

/** Compte de la console technique : le développeur, hors des comptes de l'école. */
@Entity
@Table(name = "AdminConsole")
@Getter
@Setter
public class AdminConsole {

  @Id @Cuid private String id;

  private String email;
  private String nom;

  @Column(name = "motDePasse")
  private String motDePasse;

  private boolean actif = true;
  private int echecs;
  private Instant verrouilleJusqua;
  private int versionSession;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
