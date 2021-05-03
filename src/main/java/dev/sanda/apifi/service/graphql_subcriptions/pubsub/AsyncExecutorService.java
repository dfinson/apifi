package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class AsyncExecutorService {

  private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(
    Runtime.getRuntime().availableProcessors()
  );

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
