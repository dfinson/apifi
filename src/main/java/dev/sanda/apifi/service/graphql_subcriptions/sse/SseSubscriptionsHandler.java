package dev.sanda.apifi.service.graphql_subcriptions.sse;

import dev.sanda.apifi.dto.GraphQLRequest;
import dev.sanda.apifi.service.graphql_config.GraphQLRequestExecutor;
import dev.sanda.apifi.utils.ConfigValues;
import graphql.ExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SseSubscriptionsHandler {

    private final GraphQLRequestExecutor<HttpServletRequest> requestExecutor;
    private final ConfigValues configValues;

    public SseEmitter handleSubscriptionRequest(GraphQLRequest graphQLRequest, Long timeoutParam, HttpServletRequest httpServletRequest){
        log.info("Parsing subscription request");
        val timeout =
                configValues.getSseTimeoutParamEnabled() && timeoutParam != null
                ? timeoutParam
                : configValues.getSseTimeout();
        val emitter = new SseEmitter(timeout);
        val executionResult = requestExecutor.executeQuery(graphQLRequest, httpServletRequest);
        if(!(executionResult.getData() instanceof Publisher))
            throw new RuntimeException("SSE endpoint is intended for use with subscriptions only");
        handleSubscription(executionResult, emitter);
        return emitter;
    }

    private void handleSubscription(ExecutionResult result, SseEmitter emitter) {
        log.info("handling subscription request");
        val publisher = (Publisher<ExecutionResult>)result.getData();
        val subscriber = new SseSubscriber(emitter);
        publisher.subscribe(subscriber);
        addSubscription(subscriber, publisher);
    }

    private final Map<String, SseSubscription> subscriptions = new ConcurrentHashMap<>();
    private void addSubscription(SseSubscriber subscriber, Publisher<ExecutionResult> publisher){
        val id = UUID.randomUUID().toString();
        subscriptions.put(id, new SseSubscription(id, subscriber, publisher));
    }
}
