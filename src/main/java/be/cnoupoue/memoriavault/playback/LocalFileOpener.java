package be.cnoupoue.memoriavault.playback;

import java.nio.file.Path;

public interface LocalFileOpener {

  void open(Path path);

  void reveal(Path path);

  void share(Path path);

  boolean supportsNativeShare();
}
