package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AsyncExecutorService {

    private final ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public synchronized void executeAsync(Runnable runnable){
        executors.execute(runnable);
    }
}
