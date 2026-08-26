package be.cnoupoue.memoriavault.memory;

import java.io.IOException;
import java.nio.file.Path;

public interface OriginalMediaFileDeleter {

  void delete(Path path) throws IOException;
}
