package dev.sanda.apifi.service.graphql_subcriptions.testing_utils;

import dev.sanda.apifi.service.graphql_subcriptions.GraphQLSubscriptionInstance;
import graphql.ExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

@Getter
@Setter
@AllArgsConstructor
public class TestSubscription implements GraphQLSubscriptionInstance {

  private String id;
  private TestSubscriberImplementation subscriber;
  private Publisher<ExecutionResult> publisher;

  @Override
  public void setSubscriber(Subscriber<ExecutionResult> subscriber) {
    this.subscriber = (TestSubscriberImplementation) subscriber;
  }

  @Override
  public void cancel() {}
}
