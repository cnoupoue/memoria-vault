package be.cnoupoue.snapmemoria.source;

import java.awt.AWTError;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import org.springframework.stereotype.Component;

@Component
public class DesktopNativeFolderPicker implements NativeFolderPicker {

  @Override
  public Optional<Path> selectFolder() {
    if (GraphicsEnvironment.isHeadless()) {
      throw new FolderPickerUnavailableException();
    }

    try {
      if (isMacOs()) {
        return selectFolderWithFileDialog();
      }

      return selectFolderWithJFileChooser();
    } catch (FolderPickerUnavailableException exception) {
      throw exception;
    } catch (AWTError error) {
      throw new FolderPickerUnavailableException();
    } catch (RuntimeException exception) {
      throw new FolderPickerUnavailableException();
    }
  }

  private boolean isMacOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
  }

  private Optional<Path> selectFolderWithFileDialog() {
    String previousDirectoryMode = System.getProperty("apple.awt.fileDialogForDirectories");

    System.setProperty("apple.awt.fileDialogForDirectories", "true");

    FileDialog dialog =
        new FileDialog((Frame) null, "Choose exported archive folder", FileDialog.LOAD);

    try {
      dialog.setDirectory(System.getProperty("user.home"));
      dialog.setVisible(true);

      String directory = dialog.getDirectory();
      String file = dialog.getFile();

      if (directory == null || file == null) {
        return Optional.empty();
      }

      return Optional.of(Path.of(directory, file));
    } finally {
      dialog.dispose();

      if (previousDirectoryMode == null) {
        System.clearProperty("apple.awt.fileDialogForDirectories");
      } else {
        System.setProperty("apple.awt.fileDialogForDirectories", previousDirectoryMode);
      }
    }
  }

  private Optional<Path> selectFolderWithJFileChooser() {
    AtomicReference<Optional<Path>> selectedPath = new AtomicReference<>(Optional.empty());
    AtomicReference<RuntimeException> failure = new AtomicReference<>();

    try {
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              JFileChooser chooser = new JFileChooser();
              chooser.setDialogTitle("Choose exported archive folder");
              chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
              chooser.setAcceptAllFileFilterUsed(false);

              int result = chooser.showOpenDialog(null);

              if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                selectedPath.set(Optional.of(chooser.getSelectedFile().toPath()));
              }
            } catch (RuntimeException exception) {
              failure.set(exception);
            }
          });
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new FolderPickerUnavailableException();
    } catch (InvocationTargetException exception) {
      throw new FolderPickerUnavailableException();
    }

    if (failure.get() != null) {
      throw new FolderPickerUnavailableException();
    }

    return selectedPath.get();
  }
}
