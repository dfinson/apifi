package dev.sanda.apifi.service.graphql_subcriptions;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubMessagingService;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubTopicHandler;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

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

    public void registerTopicSubscriber(String topic, FluxSink downStreamSubscriber, DataManager dataManager){
        pubSubMessagingService.registerTopicHandler(
                topic,
                new PubSubTopicHandler(UUID.randomUUID().toString(), downStreamSubscriber, dataManager, reflectionCache, configValues)
        );
    }

    public void removeTopicSubscriber(String topic, FluxSink downStreamSubscriber){
        pubSubMessagingService.removeTopicHandler(topic, downStreamSubscriber);
    }

    public void publishToTopic(String topic, Object payload){
        pubSubMessagingService.publishToTopic(topic, payload);
    }
}
