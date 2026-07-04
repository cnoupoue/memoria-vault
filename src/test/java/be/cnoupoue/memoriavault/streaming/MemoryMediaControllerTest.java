package be.cnoupoue.memoriavault.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class MemoryMediaControllerTest {

  @Test
  void unexpectedStreamExceptionsAreLoggedWithSafeCategory(CapturedOutput output) {
    MemoryMediaController controller =
        new MemoryMediaController(new MissingResourceMediaService(), new MediaMimeTypeResolver());
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/memories/media-1/media");
    request.addHeader("Range", "bytes=0-1");

    assertThatThrownBy(() -> controller.getMainMedia("media-1", request))
        .isInstanceOf(MediaStreamingException.class)
        .satisfies(
            exception ->
                assertThat(((MediaStreamingException) exception).getCode())
                    .isEqualTo("MEDIA_READ_FAILED"));

    assertThat(output.getOut())
        .contains("media_stream_failure")
        .contains("mediaId=media-1")
        .contains("rangeRequest=true")
        .contains("category=MEDIA_READ_FAILED")
        .contains("exceptionType=FileNotFoundException")
        .doesNotContain("/Users/")
        .doesNotContain("private.mov");
  }

  private static class MissingResourceMediaService extends MemoryMediaService {

    private MissingResourceMediaService() {
      super(null, null, null);
    }

    @Override
    public FileSystemResource getMainMedia(String memoryId) {
      return new FileSystemResource(Path.of("private.mov"));
    }
  }
}
