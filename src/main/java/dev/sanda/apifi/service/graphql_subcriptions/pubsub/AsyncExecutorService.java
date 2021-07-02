package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import static dev.sanda.apifi.utils.ApifiStaticUtils.generateOptimalScheduledExecutorService;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

@Service
public class AsyncExecutorService {

  @Getter
  @Setter
  private ScheduledExecutorService executorService = generateOptimalScheduledExecutorService();

  public synchronized ScheduledFuture scheduleAsyncTask(
    Runnable runnable,
    long delayIntervalMs,
    boolean repeat
  ) {
    if (repeat) return executorService.scheduleWithFixedDelay(
      runnable,
      delayIntervalMs,
      delayIntervalMs,
      TimeUnit.MILLISECONDS
    ); else return executorService.schedule(
      runnable,
      delayIntervalMs,
      TimeUnit.MILLISECONDS
    );
  }

  public synchronized void executeAsync(Runnable runnable) {
    executorService.execute(runnable);
  }
}
