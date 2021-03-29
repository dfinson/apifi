package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import graphql.ExecutionResult;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ApolloSubscriptionsHandler {
    private final Map<String, ApolloSubscription> subscriptions = new ConcurrentHashMap<>();

    public void addSubscription(String apolloId, ApolloSubscriber subscriber, Publisher<ExecutionResult> publisher){
        subscriptions.put(apolloId, new ApolloSubscription(apolloId, subscriber, publisher));
    }

    public ApolloSubscription getSubscription(String apolloId){
        return subscriptions.get(apolloId);
    }

    public void removeSubscription(String apolloId) {
        subscriptions.remove(apolloId);
    }
}
