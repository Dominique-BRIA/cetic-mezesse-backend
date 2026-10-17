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
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Journal des décisions du SG (RG-30) : approuvées d'office, le Proviseur peut en désapprouver une
 * pendant 4 semaines ; passé ce délai, elle reste approuvée.
 */
@Entity
@Table(name = "Decision")
@Getter
@Setter
public class Decision {

  @Id @Cuid private String id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private DomaineDecision domaine;

  /** « classe.creer », « eleve.modifier »… */
  private String action;

  private String libelle;

  /** Page de l'application où voir l'objet. */
  private String lien;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "auteurId")
  private Utilisateur auteur;

  @Column(updatable = false)
  private Instant creeLe;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private StatutDecision statut = StatutDecision.APPROUVEE;

  private Instant desapprouveeLe;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "desapprouveeParId")
  private Utilisateur desapprouveePar;

  private String motifDesapprobation;

  /** De quoi défaire l'action si le Proviseur la désapprouve (service AnnulationService). */
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> annulation;

  /** L'action a été défaite à la désapprobation. */
  private boolean annulee;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
