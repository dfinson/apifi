package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

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
    public boolean isRegisteredTopic(String topic) {
        return topicHandlers.containsKey(topic);
    }

    @Override
    public void registerTopic(String topic) {
        topicHandlers.put(topic, new HashSet<>());
    }

    @Override
    public void cancelTopic(String topic) {
        if(topicHandlers.containsKey(topic)){
            synchronized (topicHandlers.get(topic)){
                topicHandlers.get(topic).forEach(PubSubTopicHandler::complete);
                topicHandlers.remove(topic);
            }
        }
    }

    @Override
    @Transactional
    public void publishToTopic(String topic, Object payload) {
        if(isRegisteredTopic(topic)){
            synchronized (topicHandlers.get(topic)){
                topicHandlers.get(topic).forEach(handler -> handler.handleDataInTransaction(payload));
            }
        }
    }

    @Override
    public void registerTopicHandler(String topic, PubSubTopicHandler handler) {
        if(!isRegisteredTopic(topic))
            registerTopic(topic);
        synchronized (topicHandlers.get(topic)){
            topicHandlers.get(topic).add(handler);
        }
    }

    @Override
    public Collection<PubSubTopicHandler> topicListeners(String topic) {
        return topicHandlers.get(topic);
    }

    @Override
    public void removeTopicHandler(String topic, FluxSink downStreamSubscriber) {
        if(isRegisteredTopic(topic)){
            synchronized (topicHandlers.get(topic)){
                topicHandlers.get(topic).removeIf(handler -> handler.getDownStreamSubscriber().equals(downStreamSubscriber));
            }
        }
    }

    @Override
    public boolean hasRegisteredTopics() {
        return !topicHandlers.isEmpty();
    }

    @Override
    public Set<String> allTopics() {
        return topicHandlers.keySet();
    }
}
