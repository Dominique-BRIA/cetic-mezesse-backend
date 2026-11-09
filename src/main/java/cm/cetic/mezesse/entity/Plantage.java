package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Plantage remonté par l'application : une ligne par empreinte, téléphone et version. */
@Entity
@Table(name = "Plantage")
@Getter
@Setter
public class Plantage {

  @Id @Cuid private String id;

  private String empreinte;
  private String installationId;

  /** NATIF (Android), FLUTTER ou DART. */
  private String type;

  private String message;
  private String pile;
  private int numeroVersion;
  private String nomVersion;
  private String modele;
  private String versionAndroid;
  private int occurrences = 1;
  private Instant premiereFois;
  private Instant derniereFois;
  private boolean resolu;
}
