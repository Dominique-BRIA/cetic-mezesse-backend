package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Compteur nommé (matricules). Incrémenté atomiquement par PostgreSQL (CompteurRepository). */
@Entity
@Table(name = "Compteur")
@Getter
@Setter
public class Compteur {

  @Id private String cle;
  private int valeur;
}
