package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Motif de justification d'une absence (Maladie, Rendez-vous médical…), réglable par le SG. */
@Entity
@Table(name = "MotifJustification")
@Getter
@Setter
public class MotifJustification {

  @Id @Cuid private String id;

  private String libelle;
  private int ordre;
  private boolean actif = true;
}
