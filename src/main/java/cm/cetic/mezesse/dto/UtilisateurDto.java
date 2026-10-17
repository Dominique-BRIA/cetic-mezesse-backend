package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Role;
import cm.cetic.mezesse.entity.Utilisateur;
import cm.cetic.mezesse.util.Textes;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Comptes : connexion, profil, gestion des enseignants par le SG. */
public final class UtilisateurDto {

  private UtilisateurDto() {}

  static final String TELEPHONE = "^\\+?\\d{8,15}$";
  static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  /** Ce que voit l'utilisateur de son compte (jamais l'empreinte du mot de passe). */
  public record Profil(
      String id,
      String nom,
      String prenom,
      String telephone,
      String email,
      Role role,
      boolean actif,
      boolean doitChangerMotDePasse,
      String discipline) {

    public static Profil de(Utilisateur u) {
      return new Profil(
          u.getId(), u.getNom(), u.getPrenom(), u.getTelephone(), u.getEmail(), u.getRole(), u.isActif(),
          u.isDoitChangerMotDePasse(), u.getDiscipline());
    }
  }

  public record ProfilComplet(
      String id,
      String nom,
      String prenom,
      String telephone,
      String email,
      Role role,
      boolean actif,
      boolean doitChangerMotDePasse,
      String discipline,
      Instant creeLe,
      Instant majLe) {

    public static ProfilComplet de(Utilisateur u) {
      return new ProfilComplet(
          u.getId(), u.getNom(), u.getPrenom(), u.getTelephone(), u.getEmail(), u.getRole(), u.isActif(),
          u.isDoitChangerMotDePasse(), u.getDiscipline(), u.getCreeLe(), u.getMajLe());
    }
  }

  public record Connexion(@NotBlank(message = "obligatoire") String identifiant, @NotBlank(message = "obligatoire") @JsonDeserialize(using = StringDeserializer.class) String motDePasse) {
    public Connexion {
      identifiant = Textes.nettoyer(identifiant);
    }
  }

  public record ReponseConnexion(String jeton, ProfilComplet utilisateur) {}

  /** Espace profil : chacun modifie ses propres informations. */
  public record ModificationProfil(
      @NotBlank(message = "obligatoire") @Size(max = 60, message = "60 caractères au plus") String nom,
      @NotBlank(message = "obligatoire") @Size(max = 80, message = "80 caractères au plus") String prenom,
      @Pattern(regexp = TELEPHONE, message = "numéro de téléphone invalide") String telephone,
      @Email(regexp = EMAIL, message = "adresse e-mail invalide") String email,
      @Size(max = 80, message = "80 caractères au plus") String discipline,
      @JsonDeserialize(using = StringDeserializer.class) String motDePasse) {

    public ModificationProfil {
      nom = Textes.nettoyer(nom);
      prenom = Textes.nettoyer(prenom);
      telephone = Textes.telephone(telephone);
      email = Textes.email(email);
      discipline = Textes.optionnel(discipline);
    }
  }

  public record ChangementMotDePasse(
      @NotBlank(message = "obligatoire") @JsonDeserialize(using = StringDeserializer.class) String ancien,
      @NotBlank(message = "obligatoire") @Size(min = 8, max = 100, message = "au moins 8 caractères") @JsonDeserialize(using = StringDeserializer.class) String nouveau) {}

  /** Compte d'enseignant créé ou modifié par le SG. */
  public record Enseignant(
      @NotBlank(message = "obligatoire") @Size(max = 60, message = "60 caractères au plus") String nom,
      @NotBlank(message = "obligatoire") @Size(max = 80, message = "80 caractères au plus") String prenom,
      @Pattern(regexp = TELEPHONE, message = "numéro de téléphone invalide") String telephone,
      @Email(regexp = EMAIL, message = "adresse e-mail invalide") String email,
      @Size(max = 80, message = "80 caractères au plus") String discipline,
      Boolean actif) {

    public Enseignant {
      nom = Textes.nettoyer(nom);
      prenom = Textes.nettoyer(prenom);
      telephone = Textes.telephone(telephone);
      email = Textes.email(email);
      discipline = Textes.optionnel(discipline);
      if (actif == null) actif = true;
    }
  }

  /** Ligne de la liste des comptes : profil, matières et classes principales de l'année. */
  public record Ligne(
      String id,
      String nom,
      String prenom,
      String telephone,
      String email,
      Role role,
      boolean actif,
      boolean doitChangerMotDePasse,
      String discipline,
      Instant creeLe,
      List<MatiereEnseignee> matieres,
      List<Reference> classesPrincipales) {}

  public record MatiereEnseignee(String id, String nom, int coefficient, Reference classe) {}

  public record Reference(String id, String nom) {}
}
