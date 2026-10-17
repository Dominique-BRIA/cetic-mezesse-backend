package cm.cetic.mezesse.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.RecordComponent;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Met toutes les erreurs sous la forme { "message": … } avec un statut parlant. Les erreurs de
 * validation donnent « champ : message », le premier champ en erreur dans l'ordre du formulaire.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> metier(ApiException e) {
    Map<String, Object> corps = new LinkedHashMap<>();
    corps.put("message", e.getMessage());
    corps.putAll(e.getDetails());
    return ResponseEntity.status(e.getStatut()).body(corps);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
    List<String> ordre = ordreDesChamps(e.getParameter().getParameterType());
    List<FieldError> erreurs =
        e.getBindingResult().getFieldErrors().stream()
            .sorted(Comparator.comparingInt(f -> rang(ordre, f.getField())))
            .toList();
    if (erreurs.isEmpty()) {
      String global = e.getBindingResult().getGlobalErrors().stream().findFirst().map(g -> g.getDefaultMessage()).orElse("Données invalides.");
      return message(HttpStatus.BAD_REQUEST, global);
    }
    FieldError premiere = erreurs.get(0);
    return message(HttpStatus.BAD_REQUEST, chemin(premiere.getField()) + " : " + premiere.getDefaultMessage());
  }

  /** Validation des paramètres eux-mêmes (listes envoyées en corps, paramètres d'URL contraints). */
  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<Map<String, Object>> validationParametres(HandlerMethodValidationException e) {
    for (var resultat : e.getParameterValidationResults()) {
      for (var erreur : resultat.getResolvableErrors()) {
        String champ = erreur instanceof FieldError f ? chemin(f.getField()) : resultat.getMethodParameter().getParameterName();
        return message(HttpStatus.BAD_REQUEST, champ + " : " + erreur.getDefaultMessage());
      }
    }
    return message(HttpStatus.BAD_REQUEST, "Données invalides.");
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Map<String, Object>> contrainte(ConstraintViolationException e) {
    var v = e.getConstraintViolations().iterator().next();
    String chemin = v.getPropertyPath().toString();
    String champ = chemin.contains(".") ? chemin.substring(chemin.lastIndexOf('.') + 1) : chemin;
    return message(HttpStatus.BAD_REQUEST, champ + " : " + v.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, Object>> illisible(HttpMessageNotReadableException e) {
    if (e.getCause() instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException m && !m.getPath().isEmpty()) {
      String champ = m.getPath().stream().map(r -> r.getFieldName() != null ? r.getFieldName() : String.valueOf(r.getIndex())).reduce((a, b) -> a + "." + b).orElse("");
      return message(HttpStatus.BAD_REQUEST, champ + " : valeur invalide");
    }
    return message(HttpStatus.BAD_REQUEST, "Données invalides.");
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<Map<String, Object>> parametreManquant(MissingServletRequestParameterException e) {
    return message(HttpStatus.BAD_REQUEST, e.getParameterName() + " : obligatoire");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Map<String, Object>> parametreInvalide(MethodArgumentTypeMismatchException e) {
    return message(HttpStatus.BAD_REQUEST, e.getName() + " : valeur invalide");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, Object>> tropGros(MaxUploadSizeExceededException e) {
    return message(HttpStatus.BAD_REQUEST, "Fichier trop volumineux : 3 Mo au plus par photo ou fichier.");
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<Map<String, Object>> multipart(MultipartException e) {
    return message(HttpStatus.BAD_REQUEST, "Envoi de fichier invalide.");
  }

  @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
  ResponseEntity<Map<String, Object>> introuvable(RuntimeException e) {
    return message(HttpStatus.NOT_FOUND, "Élément introuvable.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, Object>> integrite(DataIntegrityViolationException e) {
    SQLException sql = causeSql(e);
    String etat = sql == null ? "" : sql.getSQLState();
    if ("23505".equals(etat)) {
      String cible = colonnes(sql.getMessage());
      return message(HttpStatus.CONFLICT, "Cette valeur existe déjà" + (cible.isEmpty() ? "" : " (" + cible + ")") + ".");
    }
    if ("23503".equals(etat)) {
      return message(HttpStatus.CONFLICT, "Suppression impossible : l'élément est encore utilisé.");
    }
    log.error("Contrainte de la base non respectée", e);
    return message(HttpStatus.CONFLICT, "Opération impossible : elle contredit les données existantes.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> refuse(AccessDeniedException e) {
    return message(HttpStatus.FORBIDDEN, "Vous n'avez pas accès à cette ressource.");
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  ResponseEntity<Map<String, Object>> routeInconnue(Exception e) {
    return message(HttpStatus.NOT_FOUND, "Route inconnue.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Map<String, Object>> methode(HttpRequestMethodNotSupportedException e) {
    return message(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non prise en charge sur cette route.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> inattendue(Exception e) {
    log.error("Erreur non prévue", e);
    return message(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur.");
  }

  static ResponseEntity<Map<String, Object>> message(HttpStatus statut, String message) {
    Map<String, Object> corps = new LinkedHashMap<>();
    corps.put("message", message);
    return ResponseEntity.status(statut).body(corps);
  }

  /** Ordre des champs du formulaire : composants d'un record, ou champs des classes parentes puis de la classe. */
  public static List<String> ordreDesChamps(Class<?> type) {
    if (type.isRecord()) return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    java.util.LinkedList<String> champs = new java.util.LinkedList<>();
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      champs.addAll(0, Arrays.stream(c.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList());
    }
    return champs;
  }

  private static int rang(List<String> ordre, String champ) {
    String racine = champ.contains(".") ? champ.substring(0, champ.indexOf('.')) : champ;
    racine = racine.contains("[") ? racine.substring(0, racine.indexOf('[')) : racine;
    int i = ordre.indexOf(racine);
    return i < 0 ? Integer.MAX_VALUE : i;
  }

  private static SQLException causeSql(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof SQLException s) return s;
    }
    return null;
  }

  /**
   * Contrainte d'unicité nommée « Table_colonne1_colonne2_key » (noms donnés par Prisma). Lue dans le
   * message de PostgreSQL, quelle que soit sa langue : « unique constraint "Classe_anneeId_nom_key" » ou
   * « contrainte unique « Classe_anneeId_nom_key » ».
   */
  private static final Pattern CONTRAINTE = Pattern.compile("[\"«]\\s*([A-Za-z0-9]+(?:_[A-Za-z0-9]+)+)_key\\s*[\"»]");

  /** « Classe_anneeId_nom_key » → « anneeId, nom ». */
  private static String colonnes(String message) {
    if (message == null) return "";
    Matcher m = CONTRAINTE.matcher(message);
    if (!m.find()) return "";
    String[] parties = m.group(1).split("_");
    return String.join(", ", Arrays.copyOfRange(parties, 1, parties.length));
  }

  /** « notes[0].valeur » → « notes.0.valeur ». */
  private static String chemin(String champ) {
    return champ.replaceAll("\\[(\\d+)]", ".$1");
  }
}
