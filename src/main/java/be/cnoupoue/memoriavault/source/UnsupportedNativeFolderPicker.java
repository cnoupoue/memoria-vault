package be.cnoupoue.memoriavault.source;

import java.nio.file.Path;
import java.util.Optional;

public class UnsupportedNativeFolderPicker implements NativeFolderPicker {

  @Override
  public Optional<Path> selectFolder() {
    throw new FolderPickerUnavailableException();
  }
}
