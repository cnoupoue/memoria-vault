package be.cnoupoue.memoriavault.source;

import be.cnoupoue.memoriavault.web.ApiException;
import org.springframework.http.HttpStatus;

public class SourceUnavailableException extends ApiException {

  public static final String CODE = "SOURCE_UNAVAILABLE";
  public static final String MESSAGE = "The configured source folder is currently unavailable.";

  public SourceUnavailableException() {
    super(HttpStatus.CONFLICT, CODE, MESSAGE);
  }
}
