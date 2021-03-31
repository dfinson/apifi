package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import reactor.core.publisher.FluxSink;

import java.util.Collection;
import java.util.Set;

public interface PubSubMessagingService {
    boolean isRegisteredTopic(String topic);
    void registerTopic(String topic);
    void cancelTopic(String topic);
    void publishToTopic(String topic, Object payload);
    void registerTopicHandler(String topic, PubSubTopicHandler messageHandler);
    Collection<PubSubTopicHandler> topicListeners(String topic);
    void removeTopicHandler(String topic, FluxSink downStreamSubscriber);
    boolean hasRegisteredTopics();
    Set<String> allTopics();
}
