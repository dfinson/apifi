package dev.sanda.apifi.service.graphql_subcriptions;

import graphql.ExecutionResult;
import org.reactivestreams.Publisher;

public interface SubscriptionsHandler {
    void addSubscription(String apolloId, ApolloSubscriber subscriber, Publisher<ExecutionResult> publisher);
    ApolloSubscription getSubscription(String apolloId);
    void removeSubscription(String apolloId);
}
