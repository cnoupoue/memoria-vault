package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.web.ApiException;
import org.springframework.http.HttpStatus;

public class OriginalFileOpenException extends ApiException {

  public OriginalFileOpenException(String message) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, "ORIGINAL_FILE_OPEN_FAILED", message);
  }
}
