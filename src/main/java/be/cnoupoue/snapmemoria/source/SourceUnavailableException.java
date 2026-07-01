package be.cnoupoue.snapmemoria.source;

import be.cnoupoue.snapmemoria.web.ApiException;
import org.springframework.http.HttpStatus;

public class SourceUnavailableException extends ApiException {

  public static final String CODE = "SOURCE_UNAVAILABLE";
  public static final String MESSAGE = "The configured source folder is currently unavailable.";

  public SourceUnavailableException() {
    super(HttpStatus.CONFLICT, CODE, MESSAGE);
  }
}
