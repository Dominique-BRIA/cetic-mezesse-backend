package cm.cetic.mezesse.dto;

import java.util.List;

/** Impressions : bulletins, cartes scolaires, listes. */
public final class DocumentDto {

  private DocumentDto() {}

  /** Classes entières et élèves choisis un à un (inscriptions). */
  public record Selection(List<String> classeIds, List<String> inscriptionIds, String periode) {
    public Selection {
      classeIds = classeIds == null ? List.of() : classeIds;
      inscriptionIds = inscriptionIds == null ? List.of() : inscriptionIds;
    }
  }

  public record SansPhoto(String inscriptionId, String eleveId, String nom, String prenoms, String matricule, String classe) {}

  /** Vérification avant impression des cartes : la photo est obligatoire. */
  public record Verification(int total, List<SansPhoto> sansPhoto) {}

  /** Fichier produit : contenu, nom (sans extension) et nombre d'élèves écartés (cartes). */
  public record Fichier(byte[] contenu, String nom, int ecartes) {}

  /** Colonne d'un tableau exporté : largeur relative (1 par défaut), alignement « droite » ou « centre », texte en gras. */
  public record Colonne(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String titre, Float largeur, String align, Boolean gras) {}

  /** Une ligne : ses cellules « c », ou un intertitre « groupe » (une classe, un niveau…) ; « gras » pour un total. */
  public record Ligne(@jakarta.validation.constraints.Size(max = 60) List<@jakarta.validation.constraints.Size(max = 2000) String> c, @jakarta.validation.constraints.Size(max = 200) String groupe, Boolean gras) {}

  /**
   * Tableau affiché par une page, à exporter tel quel : « format » pdf ou xlsx ; « paysage » d'office au-delà de huit
   * colonnes ; « signature » : qualité du signataire sous le tableau (PDF) ; « fichier » : nom du fichier.
   */
  public record Tableau(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String titre,
      @jakarta.validation.constraints.Size(max = 300) String sousTitre,
      @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 60) List<@jakarta.validation.Valid Colonne> colonnes,
      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 10000) List<@jakarta.validation.Valid Ligne> lignes,
      Boolean paysage,
      @jakarta.validation.constraints.Size(max = 100) String signature,
      @jakarta.validation.constraints.Pattern(regexp = "pdf|xlsx", message = "pdf ou xlsx") String format,
      @jakarta.validation.constraints.Size(max = 120) String fichier) {}
}
