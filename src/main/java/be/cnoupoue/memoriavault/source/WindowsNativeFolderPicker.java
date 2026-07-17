package be.cnoupoue.memoriavault.source;

import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;

public class WindowsNativeFolderPicker implements NativeFolderPicker {

  @Override
  public Optional<Path> selectFolder() {
    if (GraphicsEnvironment.isHeadless()) {
      throw new FolderPickerUnavailableException();
    }

    try {
      return selectFolderWithJFileChooser();
    } catch (FolderPickerUnavailableException exception) {
      throw exception;
    } catch (AWTError | RuntimeException exception) {
      throw new FolderPickerUnavailableException();
    }
  }

  private Optional<Path> selectFolderWithJFileChooser() {
    try {
      // Try to use system look-and-feel for native Windows appearance
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      // Fall back to default look-and-feel if system L&F is not available
    }

    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setVisible(false);

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
    fileChooser.setDialogTitle("Choose exported archive folder");

    try {
      int result = fileChooser.showOpenDialog(frame);

      if (result != JFileChooser.APPROVE_OPTION) {
        return Optional.empty();
      }

      File selectedFile = fileChooser.getSelectedFile();

      if (selectedFile == null) {
        return Optional.empty();
      }

      return Optional.of(selectedFile.toPath());
    } finally {
      frame.dispose();
    }
  }
}
