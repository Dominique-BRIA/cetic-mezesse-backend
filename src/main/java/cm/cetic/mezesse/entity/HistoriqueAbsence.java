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
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Une opération du module des absences, avec l'ancienne et la nouvelle valeur. Jamais modifiée. */
@Entity
@Table(name = "HistoriqueAbsence")
@Getter
@Setter
public class HistoriqueAbsence {

  @Id @Cuid private String id;

  @Column(name = "ficheId")
  private String ficheId;

  @Column(name = "classeId")
  private String classeId;

  /** Absence concernée (justifications) : l'historique d'une absence se retrouve ainsi. */
  @Column(name = "absenceId")
  private String absenceId;

  private LocalDate date;
  private String action;
  private String libelle;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> avant;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> apres;

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
