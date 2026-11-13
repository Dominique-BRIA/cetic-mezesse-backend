package cm.cetic.mezesse.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Une séance réellement programmée un jour donné, tirée de l'emploi du temps puis ajustée : horaire changé,
 * remplaçant, séance annulée (jamais comptée), séance ajoutée, enseignant absent (les élèves absents restent
 * comptés absents).
 */
@Entity
@Table(name = "Seance")
@Getter
@Setter
public class Seance {

  public static final String PREVUE = "PREVUE";
  public static final String ANNULEE = "ANNULEE";

  @Id @Cuid private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ficheId")
  private FicheAppel fiche;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creneauId")
  private Creneau creneau;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "matiereId")
  private Matiere matiere;

  /** Nom de la matière au moment de l'appel : l'historique reste lisible si la matière disparaît. */
  @jakarta.persistence.Column(name = "matiere")
  private String nomMatiere;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "enseignantId")
  private Utilisateur enseignant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "remplacantId")
  private Utilisateur remplacant;

  private LocalTime debut;
  private LocalTime fin;
  private String statut = PREVUE;
  private boolean ajoutee;
  private boolean enseignantAbsent;
  private String motif;

  public boolean estAnnulee() {
    return ANNULEE.equals(statut);
  }

  /** Durée réelle, en minutes. */
  public int duree() {
    return (int) Duration.between(debut, fin).toMinutes();
  }
}
