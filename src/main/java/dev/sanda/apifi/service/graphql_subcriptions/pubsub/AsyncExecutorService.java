package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class AsyncExecutorService {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public synchronized ScheduledFuture scheduleAsyncTask(Runnable runnable, long delayIntervalMs, boolean repeat){
        if(repeat)
            return executorService.scheduleWithFixedDelay(runnable, delayIntervalMs, delayIntervalMs, TimeUnit.MILLISECONDS);
        else 
            return executorService.schedule(runnable, delayIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    public synchronized void executeAsync(Runnable runnable){
        executorService.execute(runnable);
    }
}
