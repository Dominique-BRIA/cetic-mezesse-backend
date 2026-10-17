package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compétence visée, saisie par le professeur pour sa matière à chaque séquence. */
@Entity
@Table(name = "CompetenceVisee")
@IdClass(CompetenceVisee.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompetenceVisee {

  @Id private String matiereId;
  @Id private String sequenceId;
  private String texte;

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Cle implements Serializable {
    private String matiereId;
    private String sequenceId;
  }
}
