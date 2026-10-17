package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Matiere")
@Getter
@Setter
public class Matiere {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "classeId")
  private Classe classe;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "groupeId")
  private GroupeMatiere groupe;

  private String nom;
  private String nomEn;
  private int coefficient;
  private int ordre = 1;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "professeurId")
  private Utilisateur professeur;

  public String getClasseId() {
    return classe == null ? null : classe.getId();
  }

  public String getGroupeId() {
    return groupe == null ? null : groupe.getId();
  }

  public String getProfesseurId() {
    return professeur == null ? null : professeur.getId();
  }
}
