package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.apifi.utils.ConfigValues;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = @Autowired)
public class KeepAliveScheduler {

    private final ConfigValues configValues;
    private final AsyncExecutorService executorService;
    private final Map<String, ScheduledFuture> sessionScheduledKeepAliveTasks = new ConcurrentHashMap<>();

    public void registerSessionKeepAlive(WebSocketSession session){
        synchronized (sessionScheduledKeepAliveTasks){
            sessionScheduledKeepAliveTasks.put(session.getId(), executorService.scheduleAsyncTask(
                    keepAliveTask(session),
                    configValues.getWsKeepAliveInterval(),
                    true
            ));
        }
    }

    public void cancelSessionKeepAlive(WebSocketSession session){
        try{
            synchronized (sessionScheduledKeepAliveTasks.get(session.getId())){
                sessionScheduledKeepAliveTasks.get(session.getId()).cancel(false);
                sessionScheduledKeepAliveTasks.remove(session.getId());
            }
        }catch(NullPointerException ignored){}
    }

    private Runnable keepAliveTask(WebSocketSession session){
        return () -> {
            if(!sessionScheduledKeepAliveTasks.containsKey(session.getId()))
                return;
            try {
                if (session.isOpen())
                    session.sendMessage(MessagingFactory.keepAlive());
            } catch (IOException exception) {
                fatalError(session, exception);
            }
        };
    }

    private void fatalError(WebSocketSession session, Exception exception) {
        try {
            log.error("Encountered fatal error during session \"" + session.getId() + "\" - closing the session with status 'SESSION_NOT_RELIABLE'");
            log.error("See exception stacktrace: \n", exception);
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (Exception ignored) {}
        log.warn(String.format("WebSocket session %s (%s) closed due to an exception", session.getId(), session.getRemoteAddress()), exception);
    }

}
