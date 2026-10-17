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
}
