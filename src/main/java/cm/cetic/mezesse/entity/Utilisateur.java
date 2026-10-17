package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Compte de connexion : Proviseur, Surveillant Général ou professeur. */
@Entity
@Table(name = "Utilisateur")
@Getter
@Setter
public class Utilisateur {

  @Id @Cuid private String id;

  private String nom;
  private String prenom;
  private String telephone;
  private String email;

  @Column(name = "motDePasse")
  private String motDePasse;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private Role role;

  private boolean actif = true;
  private boolean doitChangerMotDePasse;

  /** Ce qu'enseigne le professeur, à titre indicatif. */
  private String discipline;

  @Column(updatable = false)
  private Instant creeLe;

  private Instant majLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
    majLe = Horloge.maintenant();
  }

  @PreUpdate
  void avantModification() {
    majLe = Horloge.maintenant();
  }

  public String nomComplet() {
    return prenom + " " + nom;
  }
}
