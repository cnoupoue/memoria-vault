package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.web.ApiException;
import org.springframework.http.HttpStatus;

public class OriginalFileActionException extends ApiException {

  public OriginalFileActionException(String message) {
    this("ORIGINAL_FILE_ACTION_FAILED", message);
  }

  public OriginalFileActionException(String message, Throwable cause) {
    this("ORIGINAL_FILE_ACTION_FAILED", message, cause);
  }

  public OriginalFileActionException(String code, String message) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
  }

  public OriginalFileActionException(String code, String message, Throwable cause) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, code, message, cause);
  }
}
