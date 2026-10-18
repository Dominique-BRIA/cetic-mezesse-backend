package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Une seule ligne (id = 1) : ce qui s'imprime en tête des bulletins et des listes. */
@Entity
@Table(name = "Etablissement")
@Getter
@Setter
public class Etablissement {

  public static final int ID = 1;

  @Id private Integer id = ID;

  private String nomFr;
  private String nomEn;
  private String sigle;
  private String regionFr;
  private String regionEn;
  private String departementFr;
  private String departementEn;
  private String arrondissement;
  private String localite;
  private String code;
  private String boitePostale;
  private String telephone;
  private String prefixeMatricule = "CTM";

  /** Sigle anglais (GTC) : « GTC MEZESSE » dans la colonne anglaise de l'en-tête. */
  private String sigleEn;

  /** Moyenne minimale pour figurer au tableau d'honneur (réglable par le Proviseur et le SG). */
  private java.math.BigDecimal seuilTableauHonneur = java.math.BigDecimal.valueOf(12);
}
