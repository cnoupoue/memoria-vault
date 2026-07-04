package be.cnoupoue.memoriavault.streaming;

import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

@Service
public class MediaMimeTypeResolver {

  public MediaType resolve(Resource resource) {
    String filename = resource.getFilename();

    if (filename != null) {
      String lowercaseFilename = filename.toLowerCase(Locale.ROOT);

      if (lowercaseFilename.endsWith(".mp4")) {
        return MediaType.valueOf("video/mp4");
      }

      if (lowercaseFilename.endsWith(".mov")) {
        return MediaType.valueOf("video/quicktime");
      }

      if (lowercaseFilename.endsWith(".webm")) {
        return MediaType.valueOf("video/webm");
      }

      if (lowercaseFilename.endsWith(".m4v")) {
        return MediaType.valueOf("video/x-m4v");
      }
    }

    return MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
  }
}
