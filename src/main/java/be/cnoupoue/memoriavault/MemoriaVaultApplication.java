package be.cnoupoue.memoriavault;

import be.cnoupoue.memoriavault.browser.ExistingInstanceStartupListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MemoriaVaultApplication extends Application {

  private ConfigurableApplicationContext springContext;

  /** Initializes the Spring Boot context in the background before the JavaFX UI starts. */
  @Override
  public void init() {
    SpringApplication application = new SpringApplication(MemoriaVaultApplication.class);
    application.addListeners(new ExistingInstanceStartupListener());

    // Explicitly disable headless mode to allow native Windows AWT/JavaFX UI creation
    application.setHeadless(false);

    // Start the Spring Boot backend server
    this.springContext = application.run(getParameters().getRaw().toArray(new String[0]));
  }

  /** Configures and displays the native Windows desktop frame embedding the frontend web layout. */
  @Override
  public void start(Stage primaryStage) {
    WebView webView = new WebView();

    // Fetch the dynamically configured port or fallback to the standard port
    String port = springContext.getEnvironment().getProperty("server.port", "8080");
    webView.getEngine().load("http://localhost:" + port);

    // Setup the main interface container scene
    Scene scene = new Scene(webView, 1200, 800);
    primaryStage.setScene(scene);
    primaryStage.setTitle("Memoria Vault");

    // Load the application icon for the Windows taskbar representation
    try {
      primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icon.png")));
    } catch (Exception e) {
      // Fallback silently if the resource stream path slightly differs
    }

    // Handle graceful application shutdown when the window close button (X) is clicked
    primaryStage.setOnCloseRequest(
        event -> {
          Platform.exit();
          System.exit(0);
        });

    primaryStage.show();
  }

  /** Shuts down the Spring Boot engine cleanly when the JavaFX platform terminates. */
  @Override
  public void stop() {
    if (springContext != null) {
      springContext.close();
    }
    Platform.exit();
  }

  /**
   * Main entry point routing execution control directly over to the JavaFX application lifecycle.
   */
  public static void main(String[] args) {
    Application.launch(MemoriaVaultApplication.class, args);
  }
}
