package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.service.BilanAbsences.Sanction;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Sanction pour absences non justifiées, réglée par le SG : elle s'applique à partir de son nombre de minutes
 * cumulées depuis la rentrée (inclus), jusqu'à la sanction suivante. {@code ligne} : ligne du bloc
 * « Discipline » du bulletin qui la porte ; une sanction d'exclusion est décidée par le Proviseur.
 */
@Entity
@Table(name = "SanctionAbsence")
@Getter
@Setter
public class SanctionAbsence {

  @Id @Cuid private String id;

  private String libelle;

  @Enumerated(EnumType.STRING)
  private Sanction ligne;

  private int minutes;
}
