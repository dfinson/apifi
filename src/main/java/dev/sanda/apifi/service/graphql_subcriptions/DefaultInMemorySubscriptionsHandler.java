package dev.sanda.apifi.service.graphql_subcriptions;

import graphql.ExecutionResult;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnMissingBean(SubscriptionsHandler.class)
public class DefaultInMemorySubscriptionsHandler implements SubscriptionsHandler {
    private final Map<String, ApolloSubscription> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void addSubscription(String apolloId, ApolloSubscriber subscriber, Publisher<ExecutionResult> publisher){
        subscriptions.put(apolloId, new ApolloSubscription(apolloId, subscriber, publisher));
    }

    @Override
    public ApolloSubscription getSubscription(String apolloId){
        return subscriptions.get(apolloId);
    }

    @Override
    public void removeSubscription(String apolloId) {
        subscriptions.remove(apolloId);
    }
}
