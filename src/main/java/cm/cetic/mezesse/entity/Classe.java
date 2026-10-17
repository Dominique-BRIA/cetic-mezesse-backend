package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Le nom est calculé (RG-29) : niveau + spécialité + lettre A, B… quand le niveau et la spécialité
 * comptent plusieurs classes dans l'année. Il est stocké pour les listes et les PDF.
 */
@Entity
@Table(name = "Classe")
@Getter
@Setter
public class Classe {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "anneeId")
  private AnneeScolaire annee;

  /** « 1re année Mécanique A ». */
  private String nom;

  private String nomEn;

  /** 1 à 7. */
  private int niveau;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "specialiteId")
  private Specialite specialite;

  /** Ordre dans le niveau et la spécialité : donne la lettre. */
  private int numero = 1;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "profPrincipalId")
  private Utilisateur profPrincipal;

  @ManyToMany(mappedBy = "classes")
  private Set<GroupeMatiere> groupes = new LinkedHashSet<>();

  @OneToMany(mappedBy = "classe")
  private List<Matiere> matieres = new ArrayList<>();

  public String getAnneeId() {
    return annee == null ? null : annee.getId();
  }

  public String getSpecialiteId() {
    return specialite == null ? null : specialite.getId();
  }

  public String getProfPrincipalId() {
    return profPrincipal == null ? null : profPrincipal.getId();
  }
}
