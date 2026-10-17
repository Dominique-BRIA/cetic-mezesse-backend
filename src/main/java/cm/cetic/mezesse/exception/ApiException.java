package cm.cetic.mezesse.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Erreur métier renvoyée au client sous la forme { "message": … } avec le statut HTTP voulu. */
@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus statut;

  /** Champs ajoutés au corps de la réponse, à côté du message (par exemple la liste des erreurs d'un lot). */
  private final Map<String, Object> details;

  public ApiException(HttpStatus statut, String message) {
    this(statut, message, Map.of());
  }

  public ApiException(HttpStatus statut, String message, Map<String, Object> details) {
    super(message);
    this.statut = statut;
    this.details = details;
  }

  public static ApiException introuvable(String quoi) {
    return new ApiException(HttpStatus.NOT_FOUND, quoi + " introuvable.");
  }

  public static ApiException interdit() {
    return interdit("Vous n'avez pas accès à cette ressource.");
  }

  public static ApiException interdit(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, message);
  }

  public static ApiException conflit(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }

  public static ApiException invalide(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException nonConnecte(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, message);
  }
}
