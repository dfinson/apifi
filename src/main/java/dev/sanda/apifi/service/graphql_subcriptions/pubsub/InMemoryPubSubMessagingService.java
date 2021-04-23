package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import reactor.core.publisher.FluxSink;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryPubSubMessagingService implements PubSubMessagingService {

    private final Map<String, Collection<PubSubTopicHandler>> topicHandlers = new ConcurrentHashMap<>();

    @Override
    public Set<String> registeredTopics() {
        return topicHandlers.keySet();
    }

    @Override
    @SneakyThrows
    @Transactional
    public void publishToTopic(String topic, Object payload) {
        if(topicHandlers.containsKey(topic)){
            synchronized (topicHandlers.get(topic)){
                topicHandlers.get(topic).forEach(handler -> handler.handleDataInTransaction(payload));
            }
        }
    }

    @Override
    public void registerTopicHandler(String topic, PubSubTopicHandler handler) {
        topicHandlers.putIfAbsent(topic, new HashSet<>());
        synchronized (topicHandlers.get(topic)){
            topicHandlers.get(topic).add(handler);
        }
    }

    @Override
    public void removeTopicHandler(String topic, FluxSink downStreamSubscriber) {
        try {
            synchronized (topicHandlers.get(topic)){
                topicHandlers.get(topic).removeIf(handler -> handler.getDownStreamSubscriber().equals(downStreamSubscriber));
            }
        }catch (NullPointerException ignored){}
    }
}
