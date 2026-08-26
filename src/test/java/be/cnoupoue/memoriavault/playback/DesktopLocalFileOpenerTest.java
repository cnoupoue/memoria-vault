package be.cnoupoue.memoriavault.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.platform.PlatformService;
import be.cnoupoue.memoriavault.platform.PlatformType;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DesktopLocalFileOpenerTest {

  @Mock private PlatformService platformService;

  @Test
  void revealsFileInFinderOnMacos() {
    CapturingProcessStarter processStarter = new CapturingProcessStarter(0);
    when(platformService.getPlatformType()).thenReturn(PlatformType.MACOS);

    new DesktopLocalFileOpener(platformService, processStarter).reveal(Path.of("/tmp/photo.jpg"));

    assertThat(processStarter.commands)
        .containsExactly(List.of("/usr/bin/open", "-R", "/tmp/photo.jpg"));
  }

  @Test
  void revealsFileInExplorerOnWindows() {
    CapturingProcessStarter processStarter = new CapturingProcessStarter(0);
    when(platformService.getPlatformType()).thenReturn(PlatformType.WINDOWS);

    new DesktopLocalFileOpener(platformService, processStarter)
        .reveal(Path.of("C:\\Users\\cameron\\photo.jpg"));

    assertThat(processStarter.commands)
        .containsExactly(List.of("explorer.exe", "/select,C:\\Users\\cameron\\photo.jpg"));
  }

  @Test
  void reportsUnsupportedPlatformForReveal() {
    when(platformService.getPlatformType()).thenReturn(PlatformType.LINUX);

    assertThatThrownBy(
            () ->
                new DesktopLocalFileOpener(platformService, new CapturingProcessStarter(0))
                    .reveal(Path.of("/tmp/photo.jpg")))
        .isInstanceOf(OriginalFileActionException.class)
        .hasMessage("Opening the file location is unavailable on this platform.");
  }

  @Test
  void reportsLaunchFailure() {
    when(platformService.getPlatformType()).thenReturn(PlatformType.MACOS);

    assertThatThrownBy(
            () ->
                new DesktopLocalFileOpener(platformService, new CapturingProcessStarter(1))
                    .reveal(Path.of("/tmp/photo.jpg")))
        .isInstanceOf(OriginalFileActionException.class)
        .hasMessage("The original file location could not be opened.");
  }

  private static final class CapturingProcessStarter
      implements DesktopLocalFileOpener.ProcessStarter {

    private final int exitCode;
    private final List<List<String>> commands = new ArrayList<>();

    private CapturingProcessStarter(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public Process start(List<String> command) {
      commands.add(command);
      return new CompletedProcess(exitCode);
    }
  }

  private static final class CompletedProcess extends Process {

    private final int exitCode;

    private CompletedProcess(int exitCode) {
      this.exitCode = exitCode;
    }

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return exitCode;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {}
  }
}
