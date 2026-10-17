package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Séquence d'évaluation : 6 par année, 2 par trimestre. */
@Entity
@Table(name = "Sequence")
@Getter
@Setter
public class Sequence {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "anneeId")
  private AnneeScolaire annee;

  /** 1 à 6. */
  private int numero;

  /** 1 à 3. */
  private int trimestre;

  private boolean saisieOuverte;

  public String getAnneeId() {
    return annee == null ? null : annee.getId();
  }
}
