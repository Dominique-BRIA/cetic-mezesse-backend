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

  /** Moyenne annuelle qui permet le passage après délibération (null : pas de délibération). */
  private java.math.BigDecimal moyenneDeliberation;

  /** Heures d'absence non justifiées de la période à partir desquelles le tableau d'honneur est refusé (null : pas de limite). */
  private Integer heuresRefusTableauHonneur;

  /** Part des matières composées (%) en dessous de laquelle l'élève n'est pas classé. */
  private java.math.BigDecimal seuilNonClasse = new java.math.BigDecimal("33.33");

  /** Part des matières composées (%) à partir de laquelle les matières non composées sont ignorées (en dessous : 0). */
  private java.math.BigDecimal seuilMatieresIgnorees = new java.math.BigDecimal("66.67");

  /** Ordre de départage de la première place : [{type: GROUPE|DISCIPLINE|ALPHABETIQUE, groupe: nom}] ; null : ordre par défaut. */
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private java.util.List<java.util.Map<String, String>> criteresDepartage;
}
