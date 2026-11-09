package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Politique de mise à jour (ligne unique), réglée depuis la console et lue par l'application. */
@Entity
@Table(name = "PolitiqueMiseAJour")
@Getter
@Setter
public class PolitiqueMiseAJour {

  public static final int ID = 1;

  @Id private int id = ID;

  /** Plus aucune nouvelle APK n'est téléchargée. */
  private boolean suspendue;

  /** Numéros de version à ne pas installer, séparés par des virgules. */
  private String versionsBloquees = "";

  /** S'ajoute au minimum de version.json : le plus élevé des deux s'applique. */
  private int minimum;

  private String message;
  private Instant majLe;
  private String majPar;
}
