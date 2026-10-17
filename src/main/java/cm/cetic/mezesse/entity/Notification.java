package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Notification affichée dans l'application (RG-32) : décision du SG désapprouvée, séquence ouverte
 * à la saisie pour les professeurs concernés.
 */
@Entity
@Table(name = "Notification")
@Getter
@Setter
public class Notification {

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "destinataireId")
  private Utilisateur destinataire;

  private String titre;
  private String message;

  /** Page à ouvrir. */
  private String lien;

  private boolean lue;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
