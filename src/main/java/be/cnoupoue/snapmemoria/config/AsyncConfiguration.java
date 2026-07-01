package be.cnoupoue.snapmemoria.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

  @Bean(name = "memoryScanExecutor")
  public Executor memoryScanExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    /*
     * One scan at a time is intentional:
     * scanning two large USB sources simultaneously would make both slow.
     */
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(2);

    executor.setThreadNamePrefix("memory-scan-");
    executor.initialize();

    return executor;
  }
}
