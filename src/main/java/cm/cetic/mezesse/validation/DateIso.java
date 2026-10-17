package cm.cetic.mezesse.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Texte contenant une date réelle au format AAAA-MM-JJ (éventuellement suivie d'une heure). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateIso.Controle.class)
public @interface DateIso {

  String message() default "date invalide";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Controle implements ConstraintValidator<DateIso, String> {
    @Override
    public boolean isValid(String valeur, ConstraintValidatorContext ctx) {
      return valeur == null || lire(valeur) != null;
    }

    /** La date, ou null si le texte n'en est pas une. */
    public static LocalDate lire(String valeur) {
      if (valeur == null || valeur.length() < 10) return null;
      try {
        return LocalDate.parse(valeur.substring(0, 10));
      } catch (DateTimeParseException e) {
        return null;
      }
    }
  }

}
