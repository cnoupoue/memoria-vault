package be.cnoupoue.snapmemoria;

import be.cnoupoue.snapmemoria.browser.ExistingInstanceStartupListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SnapmemoriaApplication {

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(SnapmemoriaApplication.class);
    application.addListeners(new ExistingInstanceStartupListener());
    application.setHeadless(false);
    application.run(args);
  }
}
