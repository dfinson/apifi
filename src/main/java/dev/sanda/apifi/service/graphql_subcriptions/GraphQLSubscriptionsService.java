package dev.sanda.apifi.service.graphql_subcriptions;

import java.util.Collection;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public interface GraphQLSubscriptionsService<T> {
  <E> Flux<E> generatePublisher(String topic);
  <E> Flux<E> generatePublisher(
    String topic,
    FluxSink.OverflowStrategy backPressureStrategy
  );
  <E> Flux<E> generatePublisher(List<String> topics);
  <E> Flux<E> generatePublisher(
    List<String> topics,
    FluxSink.OverflowStrategy backPressureStrategy
  );
  void publishToTopic(String topic, T payload);
  void publishToTopic(String topic, Collection<T> payload);
}
