package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.sanda.apifi.dto.GraphQLRequest;
import dev.sanda.apifi.service.graphql_config.GraphQLRequestExecutor;
import dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages.ApolloMessage;
import dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages.ApolloPayloadMessage;
import graphql.ExecutionResult;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.ApolloMessageFactory.*;

@Slf4j
@Component
@AllArgsConstructor(onConstructor_ = @Autowired)
public class ApolloProtocolHandler extends TextWebSocketHandler implements ApolloSubProtocolCapable {

    private final GraphQLRequestExecutor<WebSocketSession> executor;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            val apolloMessage = getApolloMessage(session, textMessage);
            switch (apolloMessage.getType()) {
                case GRAPHQL_CONNECTION_INIT: handleInit(session); break;
                case GRAPHQL_START: handleStart(session, apolloMessage); break;
                case GRAPHQL_STOP: handleStop(apolloMessage); break;
                case GRAPHQL_CONNECTION_TERMINATE: handleTerminate(session); break;
            }
        } catch (Exception e) {
            fatalError(session, e);
        }
    }

    @SneakyThrows
    private void handleTerminate(WebSocketSession session){
        log.info("Terminating Apollo Protocol GraphQLSubscription on WebSocketSession #" + session.getId());
        session.close();
    }
    private void handleStop(ApolloMessage apolloMessage) {
        val toStop = subscriptions.get(apolloMessage.getId());
        if (toStop != null) {
            log.info("Stopping Apollo Protocol GraphQLSubscription #" + toStop.getApolloId());
            toStop.getSubscriber().getSubscription().cancel();
            subscriptions.remove(apolloMessage.getId());
        }
    }
    private void handleStart(WebSocketSession session, ApolloMessage apolloMessage) {
        log.info("Starting Apollo Protocol GraphQLSubscription in WebSocketSession #" + session.getId() + " with message: " + apolloMessage.toString());
        val request = GraphQLRequest.fromObjectNode(((ApolloPayloadMessage<ObjectNode>) apolloMessage).getPayload());
        val result = executor.executeQuery(request, session);
        if (result.getData() instanceof Publisher)
            handleSubscription(apolloMessage.getId(), result, session);
        else
            handleQueryOrMutation(apolloMessage.getId(), result, session);
    }
    private void handleSubscription(String id, ExecutionResult result, WebSocketSession session) {
        val publisher = (Publisher<ExecutionResult>)result.getData();
        val subscriber = new ApolloSubscriber(id, session);
        publisher.subscribe(subscriber);
        addSubscription(id, subscriber, publisher);
    }
    @SneakyThrows
    private void handleQueryOrMutation(String id, ExecutionResult result, WebSocketSession session) {
        session.sendMessage(ApolloMessageFactory.data(id, result));
        session.sendMessage(ApolloMessageFactory.complete(id));
    }
    @SneakyThrows
    private void handleInit(WebSocketSession session){
        log.info("Initializing Apollo Protocol GraphQLSubscription in WebSocketSession #" + session.getId());
        session.sendMessage(ApolloMessageFactory.connectionAck());
    }
    @SneakyThrows
    private ApolloMessage getApolloMessage(WebSocketSession session, TextMessage message) {
        try {
            return ApolloMessageFactory.from(message);
        } catch (IOException e) {
            session.sendMessage(ApolloMessageFactory.connectionError());
            throw e;
        }
    }
    public static void fatalError(WebSocketSession session, Exception exception) {
        try {
            log.error("Encountered fatal error during session \"" + session.getId() + "\" - closing the session with status 'SESSION_NOT_RELIABLE'");
            log.error("See exception stacktrace: \n", exception);
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (Exception ignored) {}
        log.warn(String.format("WebSocket session %s (%s) closed due to an exception", session.getId(), session.getRemoteAddress()), exception);
    }

    private final Map<String, ApolloSubscription> subscriptions = new ConcurrentHashMap<>();
    private void addSubscription(String apolloId, ApolloSubscriber subscriber, Publisher<ExecutionResult> publisher){
        subscriptions.put(apolloId, new ApolloSubscription(apolloId, subscriber, publisher));
    }
}
