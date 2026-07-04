package be.cnoupoue.memoriavault;

import be.cnoupoue.memoriavault.browser.ExistingInstanceStartupListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MemoriaVaultApplication {

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(MemoriaVaultApplication.class);
    application.addListeners(new ExistingInstanceStartupListener());
    application.setHeadless(false);
    application.run(args);
  }
}
