package be.cnoupoue.snapmemoria.source;

import be.cnoupoue.snapmemoria.web.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidFolderSelectionException extends ApiException {

  public InvalidFolderSelectionException() {
    super(
        HttpStatus.CONFLICT,
        "FOLDER_SELECTION_INVALID",
        "Selected folder is unavailable. Choose a readable folder or enter the path manually.");
  }
}
