package cm.cetic.mezesse.entity;

import cm.cetic.mezesse.util.Horloge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Compte de la console technique : le développeur, hors des comptes de l'école. Double authentification obligatoire. */
@Entity
@Table(name = "AdminConsole")
@Getter
@Setter
public class AdminConsole {

  @Id @Cuid private String id;

  private String email;
  private String nom;

  @Column(name = "motDePasse")
  private String motDePasse;

  /** Secret de l'application d'authentification, en base 32 ; créé à la première connexion. */
  private String totpSecret;

  /** Vrai une fois le premier code confirmé. */
  private boolean totpActif;

  /** Dernier pas de 30 s accepté : un code déjà utilisé ne resert pas. */
  private long totpDernierPas;

  private boolean actif = true;
  private int echecs;
  private Instant verrouilleJusqua;
  private int versionSession;

  @Column(updatable = false)
  private Instant creeLe;

  @PrePersist
  void avantCreation() {
    if (creeLe == null) creeLe = Horloge.maintenant();
  }
}
