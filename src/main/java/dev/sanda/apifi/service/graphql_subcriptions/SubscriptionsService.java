package dev.sanda.apifi.service.graphql_subcriptions;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubMessagingService;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubTopicHandler;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class SubscriptionsService {

    @Autowired
    private PubSubMessagingService pubSubMessagingService;
    @Autowired
    private ReflectionCache reflectionCache;
    @Autowired
    private ConfigValues configValues;

    private final Set<String> localTopicHandlers = new HashSet<>();

    private void registerTopic(String topic){
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

    public void registerSubscriber(String topic, FluxSink downStreamSubscriber, DataManager dataManager){
        if(!pubSubMessagingService.isRegisteredTopic(topic))
            registerTopic(topic);
        val messageHandler = getPubSubTopicHandler(downStreamSubscriber, dataManager);
        pubSubMessagingService.registerTopicHandler(topic, messageHandler);
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

    private PubSubTopicHandler getPubSubTopicHandler(FluxSink downStreamSubscriber, DataManager dataManager) {
        synchronized (this.localTopicHandlers){
            val id = UUID.randomUUID().toString();
            localTopicHandlers.add(id);
            return new PubSubTopicHandler(id, downStreamSubscriber, dataManager, reflectionCache, configValues);
        }
    }
}
