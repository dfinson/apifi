package dev.sanda.apifi.service.graphql_subcriptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SubscriptionsRegistry {

    private final Map<String, Set<FluxSink>> topicSubscribers = new ConcurrentHashMap<>();

    public void registerTopic(String topic){
        synchronized (topicSubscribers){
            if(topicSubscribers.containsKey(topic))
                throw new RuntimeException(
                        String.format(
                                "Topic \"%s\" is already registered with %d subscribers",
                                topic, topicSubscribers.get(topic).size()
                        )
                );
            topicSubscribers.put(topic, new HashSet<>());
        }
    }

    public boolean isRegisteredTopic(String topic){
        return topicSubscribers.containsKey(topic);
    }

    @PreDestroy
    public void cancelAll(){
        if(!topicSubscribers.isEmpty())
            log.info("canceling " + topicSubscribers.size() + " subscription topic" + (topicSubscribers.size() > 1 ? "s" : ""));
        cancelAll(null);
    }
    public synchronized void cancelAll(Throwable error){
        topicSubscribers.forEach((topic, subscribers) -> {
            log.info("canceling topic: \"" + topic + "\"");
            subscribers.forEach(downStreamSubscriber -> {
                if(error != null) {
                    downStreamSubscriber.error(error);
                } else {
                    downStreamSubscriber.complete();
                }
            });
        });
        topicSubscribers.keySet().forEach(topicSubscribers::remove);
    }

    public void cancelTopic(String topic){
        cancelTopic(topic, null);
    }
    public void cancelTopic(String topic, Throwable error){
        synchronized (topicSubscribers.get(topic)){
            if(topicSubscribers.containsKey(topic)){
                if(error != null)
                    topicSubscribers.get(topic).forEach(fluxSink -> fluxSink.error(error));
                else
                    topicSubscribers.get(topic).forEach(FluxSink::complete);
                topicSubscribers.remove(topic);
            }
        }
    }

    public void registerTopicSubscriber(String topic, FluxSink downStreamSubscriber){
        if(!topicSubscribers.containsKey(topic))
            registerTopic(topic);
        synchronized (topicSubscribers.get(topic)){
            topicSubscribers.get(topic).add(downStreamSubscriber);
        }
    }
    public void removeTopicSubscriber(String topic, FluxSink downStreamSubscriber){
        if(topicSubscribers.containsKey(topic))
            synchronized (topicSubscribers.get(topic)) {
                topicSubscribers.get(topic).remove(downStreamSubscriber);
            }
    }

    public void publishToTopic(String topic, Object payload){
        if(!topicSubscribers.containsKey(topic))
            throw new RuntimeException(String.format("Cannot publish to topic \"%s\", no such topic is registered", topic));
        synchronized (topicSubscribers.get(topic)){
            topicSubscribers.get(topic).forEach(downStreamSubscriber -> downStreamSubscriber.next(payload));
        }
    }
}
