package be.cnoupoue.snapmemoria.source;

import java.awt.AWTError;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Optional;

public class MacosNativeFolderPicker implements NativeFolderPicker {

  @Override
  public Optional<Path> selectFolder() {
    if (GraphicsEnvironment.isHeadless()) {
      throw new FolderPickerUnavailableException();
    }

    try {
      return selectFolderWithFileDialog();
    } catch (FolderPickerUnavailableException exception) {
      throw exception;
    } catch (AWTError | RuntimeException exception) {
      throw new FolderPickerUnavailableException();
    }
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
}
