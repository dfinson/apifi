package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import graphql.ExecutionResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Data
@Slf4j
@RequiredArgsConstructor
public class ApolloSubscriber implements Subscriber<ExecutionResult> {

    private final String apolloId;
    private final WebSocketSession session;
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        request();
    }

    @Override
    public void onNext(ExecutionResult executionResult) {
        try {
            if (executionResult.getErrors().isEmpty())
                session.sendMessage(ApolloMessageFactory.data(apolloId, executionResult));
            else
                session.sendMessage(ApolloMessageFactory.error(apolloId, executionResult.getErrors()));
        } catch (IOException e) {
            fatalError(session, e);
        }
        request();
    }

    @Override
    public void onError(Throwable error) {
        try {
            session.sendMessage(ApolloMessageFactory.error(apolloId, error));
            subscription.cancel();
            session.close(CloseStatus.PROTOCOL_ERROR);
        } catch (IOException e) {
            fatalError(session, e);
        }
    }



    @Override
    public void onComplete() {
        try {
            if(session.isOpen())
                session.sendMessage(ApolloMessageFactory.complete(apolloId));
            else
                log.warn("Tried completing session \"" + session.getId() + "\" but the session was already closed. Did you force shutdown?");
            subscription.cancel();
            session.close(CloseStatus.NORMAL);
        } catch (IOException e) {
            fatalError(session, e);
        }
    }

    private void request() {
        Subscription subscription = this.subscription;
        if (subscription != null) {
            subscription.request(1);
        }
    }

    private static void fatalError(WebSocketSession session, Exception exception) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (Exception ignored) {}
        log.warn(String.format("WebSocket session %s (%s) closed due to an exception", session.getId(), session.getRemoteAddress()), exception);
    }
}
