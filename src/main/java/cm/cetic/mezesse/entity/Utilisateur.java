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

  // Carrière, imprimée sur les attestations de présence effective au poste.
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private Sexe sexe;

  /** Matricule de la fonction publique (1034573P). */
  private String matricule;

  /** PCEG, PLEG, PCET, PLET… */
  private String grade;

  /** « Professeur de mathématiques », « Surveillant Général »… */
  private String fonction;

  /** Prise de service dans l'établissement. */
  private java.time.LocalDate datePriseService;

  private java.time.LocalDate dateEntreeFonctionPublique;

  /** Acte de nomination ou d'affectation : « Décision n° 0123/MINESEC/… du 12/09/2020 ». */
  private String acteNomination;

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
