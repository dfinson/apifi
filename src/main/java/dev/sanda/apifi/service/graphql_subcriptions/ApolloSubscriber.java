package dev.sanda.apifi.service.graphql_subcriptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import graphql.ExecutionResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static dev.sanda.apifi.service.graphql_subcriptions.ApolloProtocolHandler.fatalError;

@Data
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@RequiredArgsConstructor
public class ApolloSubscriber implements Subscriber<ExecutionResult> {

    private final String apolloId;
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    private final WebSocketSession session;
    @JsonSerialize
    @JsonIgnoreProperties(ignoreUnknown = true)
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
        } catch (IOException e) {
            fatalError(session, e);
        }
    }

    @Override
    public void onComplete() {
        try {
            session.sendMessage(ApolloMessageFactory.complete(apolloId));
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
}
