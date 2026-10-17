package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Groupe de matières ; il peut servir à plusieurs classes de la même année. */
@Entity
@Table(name = "GroupeMatiere")
@Getter
@Setter
public class GroupeMatiere {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "anneeId")
  private AnneeScolaire annee;

  private String nom;
  private String nomEn;
  private int ordre = 1;

  /** Table de liaison créée par Prisma : A = classe, B = groupe. */
  @ManyToMany
  @JoinTable(
      name = "_ClasseToGroupeMatiere",
      joinColumns = @JoinColumn(name = "B"),
      inverseJoinColumns = @JoinColumn(name = "A"))
  private Set<Classe> classes = new LinkedHashSet<>();

  public String getAnneeId() {
    return annee == null ? null : annee.getId();
  }
}
