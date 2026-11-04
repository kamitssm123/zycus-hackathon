package com.slotmind.config;

import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean("rescheduleExecutor")
  public Executor rescheduleExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("agent-");
    // MDC does not cross a thread boundary, so without this the requestId that ties a UI error to
    // its server log (D-04) vanishes on exactly the path that is hardest to debug.
    executor.setTaskDecorator(
        runnable -> {
          Map<String, String> context = MDC.getCopyOfContextMap();
          return () -> {
            if (context != null) {
              MDC.setContextMap(context);
            }
            try {
              runnable.run();
            } finally {
              MDC.clear();
            }
          };
        });
    executor.initialize();
    return executor;
  }
}
