package be.cnoupoue.snapmemoria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SnapmemoriaApplication {

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(SnapmemoriaApplication.class);
    application.setHeadless(false);
    application.run(args);
  }
}
