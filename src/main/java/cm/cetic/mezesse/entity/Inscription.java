package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * L'élève dans une classe pour une année. Le renvoi porte sur l'inscription : un élève renvoyé une
 * année peut être réinscrit l'année suivante.
 */
@Entity
@Table(name = "Inscription")
@Getter
@Setter
public class Inscription {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "eleveId")
  private Eleve eleve;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "anneeId")
  private AnneeScolaire annee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "classeId")
  private Classe classe;

  private boolean redoublant;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private StatutInscription statut = StatutInscription.ACTIF;

  @Column(updatable = false)
  private Instant creeLe;

  @OneToMany(mappedBy = "inscription")
  @OrderBy("creeLe DESC")
  private List<Renvoi> renvois = new ArrayList<>();

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }

  public String getEleveId() {
    return eleve == null ? null : eleve.getId();
  }

  public String getAnneeId() {
    return annee == null ? null : annee.getId();
  }

  public String getClasseId() {
    return classe == null ? null : classe.getId();
  }
}
