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

/** Téléphone où l'application est installée, avec la version qu'il a signalée en dernier. */
@Entity
@Table(name = "Appareil")
@Getter
@Setter
public class Appareil {

  @Id @Cuid private String id;

  private String installationId;

  /** Dernier compte connecté sur ce téléphone. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "utilisateurId")
  private Utilisateur utilisateur;

  private int numeroVersion;
  private String nomVersion;

  /** Numéro du correctif Shorebird appliqué ; null s'il n'y en a pas. */
  private Integer correctif;

  private String modele;
  private String versionAndroid;

  /** Jeton Firebase Cloud Messaging : adresse des notifications push de ce téléphone. */
  private String jetonPush;

  @Column(updatable = false)
  private Instant premiereVue;

  private Instant derniereVue;

  @PrePersist
  void avantCreation() {
    if (premiereVue == null) premiereVue = Horloge.maintenant();
  }
}
