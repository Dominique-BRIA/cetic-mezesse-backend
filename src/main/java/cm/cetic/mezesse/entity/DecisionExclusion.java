package cm.cetic.mezesse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Décision du Directeur (ou de la Directrice) pour un élève qui a atteint les heures d'exclusion : exclusion
 * temporaire (nombre de jours ouvrés), exclusion définitive (pour toujours), ou sursis.
 */
@Entity
@Table(name = "DecisionExclusion")
@Getter
@Setter
public class DecisionExclusion {

  public enum Type {
    TEMPORAIRE, DEFINITIVE, SURSIS
  }

  @Id @Cuid private String id;

  private String inscriptionId;

  @Enumerated(EnumType.STRING)
  private Type type;

  /** Exclusion temporaire : nombre de jours ouvrés. */
  private Integer jours;

  /** Début de l'exclusion, ou jour du sursis. */
  private LocalDate date;

  /** Numéro de la séquence dont les bulletins portent la décision (la dernière séquence ouverte). */
  private int sequence;

  private String motif;

  /** Heures non justifiées (en minutes) au moment de la décision. */
  private int minutes;

  /** Renvoi créé par la décision (exclusion temporaire ou définitive). */
  private String renvoiId;

  private String parId;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = cm.cetic.mezesse.util.Horloge.maintenant();
  }
}
