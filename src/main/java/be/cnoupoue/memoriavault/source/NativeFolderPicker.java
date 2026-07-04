package be.cnoupoue.memoriavault.source;

import java.nio.file.Path;
import java.util.Optional;

public interface NativeFolderPicker {

  Optional<Path> selectFolder();
}
