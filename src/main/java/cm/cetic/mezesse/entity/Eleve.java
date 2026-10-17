package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "Eleve")
@Getter
@Setter
public class Eleve {

  @Id @Cuid private String id;

  /** Généré par l'application : CTM26-0007. */
  private String matricule;

  /** Recopié depuis cartescolaire.cm. */
  private String matriculeNational;

  private String nom;
  private String prenoms;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private Sexe sexe;

  private LocalDate dateNaissance;
  private String lieuNaissance;
  private String photoCle;
  private String parentNom;
  private String parentTelephone;
  private String parentAdresse;

  @Column(updatable = false)
  private Instant creeLe;

  private Instant majLe;

  @OneToMany(mappedBy = "eleve")
  private List<Inscription> inscriptions = new ArrayList<>();

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
    return nom + " " + prenoms;
  }
}
