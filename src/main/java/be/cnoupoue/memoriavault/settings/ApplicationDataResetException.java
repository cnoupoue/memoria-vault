package be.cnoupoue.memoriavault.settings;

import be.cnoupoue.memoriavault.web.ApiException;
import org.springframework.http.HttpStatus;

public class ApplicationDataResetException extends ApiException {

  public ApplicationDataResetException(String code, String message) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
  }

  public ApplicationDataResetException(String code, String message, Throwable cause) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, code, message, cause);
  }
}
