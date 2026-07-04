package be.cnoupoue.memoriavault.source;

import be.cnoupoue.memoriavault.web.ApiException;
import org.springframework.http.HttpStatus;

public class FolderPickerUnavailableException extends ApiException {

  public static final String CODE = "FOLDER_PICKER_UNAVAILABLE";
  public static final String MESSAGE =
      "Folder selection is unavailable in this environment. Enter the folder path manually.";

  public FolderPickerUnavailableException() {
    super(HttpStatus.CONFLICT, CODE, MESSAGE);
  }
}
