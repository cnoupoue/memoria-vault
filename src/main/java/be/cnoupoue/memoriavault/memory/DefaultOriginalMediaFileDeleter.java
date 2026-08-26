package be.cnoupoue.memoriavault.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class DefaultOriginalMediaFileDeleter implements OriginalMediaFileDeleter {

  @Override
  public void delete(Path path) throws IOException {
    Files.delete(path);
  }
}
