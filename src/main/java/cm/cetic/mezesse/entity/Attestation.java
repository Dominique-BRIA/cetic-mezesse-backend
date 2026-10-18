package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Attestation de présence effective au poste, délivrée par le Proviseur. Les informations imprimées
 * sont gardées telles qu'à la délivrance : une réimpression donne le même document.
 */
@Entity
@Table(name = "Attestation")
@Getter
@Setter
public class Attestation {

  @Id @Cuid private String id;

  /** « N° 007/2026-2027/CETIC/P ». */
  private String reference;

  /** Rang dans l'année scolaire. */
  private int numero;

  private String anneeLibelle;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "utilisateurId")
  private Utilisateur utilisateur;

  /** « ELANGA ELANGA Roméo Claude ». */
  private String beneficiaire;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private Sexe sexe;

  private String matricule;
  private String grade;
  private String fonction;
  private LocalDate datePriseService;
  private String acteNomination;

  /** Nom du Proviseur qui signe. */
  private String signataire;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "parId")
  private Utilisateur par;

  @Column(updatable = false)
  private Instant delivreeLe;

  @PrePersist
  void avantCreation() {
    if (delivreeLe == null) delivreeLe = Horloge.maintenant();
  }
}
