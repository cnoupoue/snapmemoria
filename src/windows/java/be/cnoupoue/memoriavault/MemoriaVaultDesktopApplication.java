package be.cnoupoue.memoriavault;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;

public class MemoriaVaultDesktopApplication extends Application {

  private ConfigurableApplicationContext springContext;

  static void launchDesktop(String[] args) {
    Application.launch(MemoriaVaultDesktopApplication.class, args);
  }

  @Override
  public void init() {
    this.springContext =
        MemoriaVaultApplication.startBackend(
            getParameters().getRaw().toArray(new String[0]), false, true);
  }

  @Override
  public void start(Stage primaryStage) {
    WebView webView = new WebView();
    String port = springContext.getEnvironment().getProperty("server.port", "8080");
    webView.getEngine().load("http://localhost:" + port);

    Scene scene = new Scene(webView, 1200, 800);
    primaryStage.setScene(scene);
    primaryStage.setTitle("Memoria Vault");

    try {
      primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icon.png")));
    } catch (RuntimeException ignored) {
      // Missing icons should not prevent the desktop shell from starting.
    }

    primaryStage.setOnCloseRequest(
        event -> {
          Platform.exit();
          System.exit(0);
        });

    primaryStage.show();
  }

  @Override
  public void stop() throws Exception {
    if (springContext != null) {
      springContext.close();
    }
    Platform.exit();
  }
}
