package dev.sanda.apifi.service.graphql_subcriptions.sse;

import dev.sanda.apifi.service.graphql_subcriptions.GraphQLSubscriptionInstance;
import graphql.ExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

@Data
@AllArgsConstructor
public class SseSubscription implements GraphQLSubscriptionInstance {

  private String id;
  private SseSubscriber subscriber;
  private Publisher<ExecutionResult> publisher;

  @Override
  public void setSubscriber(Subscriber<ExecutionResult> subscriber) {
    this.subscriber = (SseSubscriber) subscriber;
  }

  @Override
  public void cancel() {}
}
