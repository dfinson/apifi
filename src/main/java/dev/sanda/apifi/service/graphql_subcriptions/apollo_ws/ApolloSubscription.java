package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.sanda.apifi.service.graphql_subcriptions.GraphQLSubscriptionInstance;
import graphql.ExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

@Data
@AllArgsConstructor
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApolloSubscription implements GraphQLSubscriptionInstance {

  private String apolloId;
  private ApolloSubscriber subscriber;
  private Publisher<ExecutionResult> publisher;

  @Override
  public String getId() {
    return apolloId;
  }

  @Override
  public void setId(String id) {
    this.apolloId = id;
  }

  @Override
  public void setSubscriber(Subscriber<ExecutionResult> subscriber) {
    this.subscriber = (ApolloSubscriber) subscriber;
  }

  @Override
  public void cancel() {
    subscriber.getSubscription().cancel();
  }
}
