package dev.sanda.apifi.service.graphql_subcriptions;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubMessagingService;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubTopicHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import javax.annotation.PreDestroy;

@Slf4j
@Component
public class SubscriptionsService {

    @Autowired
    private PubSubMessagingService pubSubMessagingService;

    public void registerTopic(String topic){
        if(isRegisteredTopic(topic))
            throw new RuntimeException(
                String.format(
                        "Topic \"%s\" is already registered with %d subscribers",
                        topic, pubSubMessagingService.topicListeners(topic).size()
                )
        );
        pubSubMessagingService.registerTopic(topic);
    }

    public boolean isRegisteredTopic(String topic){
        return pubSubMessagingService.isRegisteredTopic(topic);
    }

    @PreDestroy
    public void cancelAll(){
        if(pubSubMessagingService.hasRegisteredTopics()) {
            val topicCount = pubSubMessagingService.allTopics().size();
            log.info("canceling " + topicCount + " subscription topic" + (topicCount > 1 ? "s" : ""));
            cancelAll(null);
        }
    }
    public synchronized void cancelAll(Throwable error){
        val topics = pubSubMessagingService.allTopics();
        if(error != null)
            topics.forEach(topic -> cancelTopic(topic, error));
        else
            topics.forEach(this::cancelTopic);
    }

    public void cancelTopic(String topic){
        cancelTopic(topic, null);
    }
    public void cancelTopic(String topic, Throwable error){
        if(!pubSubMessagingService.isRegisteredTopic(topic)) return;
        if(error != null)
            synchronized (pubSubMessagingService.topicListeners(topic)){
                pubSubMessagingService.topicListeners(topic).forEach(handler -> handler.completeWithError(error));
            }
        pubSubMessagingService.cancelTopic(topic);
    }

    public void registerSubscriber(String topic, FluxSink downStreamSubscriber){
        if(!pubSubMessagingService.isRegisteredTopic(topic))
            registerTopic(topic);
        pubSubMessagingService.registerTopicHandler(topic, new PubSubTopicHandler(downStreamSubscriber));
    }
    public void removeSubscriber(String topic, FluxSink downStreamSubscriber){
        if(pubSubMessagingService.isRegisteredTopic(topic))
            pubSubMessagingService.removeTopicHandler(topic, downStreamSubscriber);
    }

    public void publish(String topic, Object payload){
        if(!pubSubMessagingService.isRegisteredTopic(topic))
            throw new RuntimeException(String.format("Cannot publish to topic \"%s\", no such topic is registered", topic));
        if(!pubSubMessagingService.topicListeners(topic).isEmpty())
            pubSubMessagingService.publishToTopic(topic, payload);
    }
}
