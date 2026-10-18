package cm.cetic.mezesse.dto;

import cm.cetic.mezesse.entity.Bareme;
import cm.cetic.mezesse.entity.Etablissement;
import cm.cetic.mezesse.entity.TypeBareme;
import cm.cetic.mezesse.util.Textes;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Informations de l'établissement (en-têtes des documents) et barèmes des mentions. */
public final class EtablissementDto {

  private EtablissementDto() {}

  public record Vue(
      Integer id,
      String nomFr,
      String nomEn,
      String sigle,
      String regionFr,
      String regionEn,
      String departementFr,
      String departementEn,
      String arrondissement,
      String localite,
      String code,
      String boitePostale,
      String telephone,
      String prefixeMatricule,
      String sigleEn,
      double seuilTableauHonneur) {

    public static Vue de(Etablissement e) {
      return e == null
          ? null
          : new Vue(
              e.getId(), e.getNomFr(), e.getNomEn(), e.getSigle(), e.getRegionFr(), e.getRegionEn(), e.getDepartementFr(),
              e.getDepartementEn(), e.getArrondissement(), e.getLocalite(), e.getCode(), e.getBoitePostale(), e.getTelephone(),
              e.getPrefixeMatricule(), e.getSigleEn(), e.getSeuilTableauHonneur().doubleValue());
    }
  }

  public record Modification(
      @NotBlank(message = "obligatoire") @Size(max = 200, message = "200 caractères au plus") String nomFr,
      @NotBlank(message = "obligatoire") @Size(max = 200, message = "200 caractères au plus") String nomEn,
      @NotBlank(message = "obligatoire") @Size(max = 30, message = "30 caractères au plus") String sigle,
      @NotBlank(message = "obligatoire") @Size(max = 120, message = "120 caractères au plus") String regionFr,
      @NotBlank(message = "obligatoire") @Size(max = 120, message = "120 caractères au plus") String regionEn,
      @NotBlank(message = "obligatoire") @Size(max = 120, message = "120 caractères au plus") String departementFr,
      @NotBlank(message = "obligatoire") @Size(max = 120, message = "120 caractères au plus") String departementEn,
      @NotBlank(message = "obligatoire") @Size(max = 120, message = "120 caractères au plus") String arrondissement,
      @NotBlank(message = "obligatoire") @Size(max = 120, message = "120 caractères au plus") String localite,
      @NotBlank(message = "obligatoire") @Size(max = 60, message = "60 caractères au plus") String code,
      @Size(max = 60, message = "60 caractères au plus") String boitePostale,
      @Size(max = 30, message = "30 caractères au plus") String telephone,
      @NotNull(message = "obligatoire") @Pattern(regexp = "^[A-Z0-9]{2,6}$", message = "2 à 6 lettres ou chiffres majuscules")
          String prefixeMatricule,
      @Size(max = 30, message = "30 caractères au plus") String sigleEn) {

    public Modification {
      nomFr = Textes.nettoyer(nomFr);
      nomEn = Textes.nettoyer(nomEn);
      sigle = Textes.nettoyer(sigle);
      regionFr = Textes.nettoyer(regionFr);
      regionEn = Textes.nettoyer(regionEn);
      departementFr = Textes.nettoyer(departementFr);
      departementEn = Textes.nettoyer(departementEn);
      arrondissement = Textes.nettoyer(arrondissement);
      localite = Textes.nettoyer(localite);
      code = Textes.nettoyer(code);
      boitePostale = Textes.optionnel(boitePostale);
      telephone = Textes.optionnel(telephone);
      prefixeMatricule = Textes.nettoyer(prefixeMatricule);
      sigleEn = Textes.optionnel(sigleEn);
    }
  }

  /** Moyenne minimale pour figurer au tableau d'honneur. */
  public record SeuilTableauHonneur(
      @NotNull(message = "obligatoire")
          @DecimalMin(value = "0", message = "entre 0 et 20")
          @DecimalMax(value = "20", message = "entre 0 et 20")
          @Digits(integer = 2, fraction = 2, message = "deux décimales au plus")
          Double seuil) {}

  public record BaremeVue(String id, TypeBareme type, String libelle, String libelleLong, double min) {

    public static BaremeVue de(Bareme b) {
      return new BaremeVue(b.getId(), b.getType(), b.getLibelle(), b.getLibelleLong(), b.getMin().doubleValue());
    }
  }

  /** Une mention du barème et sa borne basse (incluse). */
  public record Seuil(
      @NotBlank(message = "obligatoire") @Size(max = 10, message = "10 caractères au plus") String libelle,
      @Size(max = 80, message = "80 caractères au plus") String libelleLong,
      @NotNull(message = "obligatoire")
          @DecimalMin(value = "0", message = "entre 0 et 20")
          @DecimalMax(value = "20", message = "entre 0 et 20")
          Double min) {

    public Seuil {
      libelle = Textes.nettoyer(libelle);
      libelleLong = Textes.optionnel(libelleLong);
    }
  }
}
