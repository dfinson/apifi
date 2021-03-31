package dev.sanda.apifi.service.graphql_subcriptions;

import graphql.ExecutionResult;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public interface GraphQLSubscriptionInstance {

    // id
    String getId();
    void setId(String id);

    // subscriber
    Subscriber<ExecutionResult> getSubscriber();
    void setSubscriber(Subscriber<ExecutionResult> subscriber);

    // publisher
    Publisher<ExecutionResult> getPublisher();
    void setPublisher(Publisher<ExecutionResult> publisher);

    // cancel subscription
    void cancel();
}
