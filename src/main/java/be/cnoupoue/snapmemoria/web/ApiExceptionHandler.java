package be.cnoupoue.snapmemoria.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
    return buildResponse(exception.getStatus(), exception.getCode(), exception.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
      ResponseStatusException exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    String message =
        exception.getReason() == null
            ? "The request could not be completed."
            : exception.getReason();

    return buildResponse(status, status.name(), message);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
      IllegalArgumentException exception) {
    return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
  }

  private ResponseEntity<ApiErrorResponse> buildResponse(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(new ApiErrorResponse(status.value(), code, message, Instant.now().toString()));
  }
}
