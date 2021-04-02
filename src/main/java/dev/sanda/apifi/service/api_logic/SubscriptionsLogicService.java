package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionsService;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints.*;
import static dev.sanda.datafi.DatafiStaticUtils.getId;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER;

@Service
@Scope("prototype")
public class SubscriptionsLogicService<T>{


    @Autowired
    private SubscriptionsService subscriptionsService;
    @Autowired
    private ReflectionCache reflectionCache;

    private DataManager<T> dataManager;
    private String entityName;
    private String idFieldName;

    public void init(DataManager<T> dataManager){
        this.dataManager = dataManager;
        this.entityName = dataManager.getClazzSimpleName();
        this.idFieldName = reflectionCache.getEntitiesCache().get(entityName).getIdField().getName();
    }

    public final static FluxSink.OverflowStrategy DEFAULT_OVERFLOW_STRATEGY = BUFFER;




    private <E> Flux<E> generatePublisher(List<String> topics, FluxSink.OverflowStrategy backPressureStrategy){
        return Flux.create(
                subscriber -> {
                    subscriber.onDispose(
                            () -> topics.forEach(topic -> subscriptionsService.removeSubscriber(topic, subscriber))
                    );
                    topics.forEach(topic -> subscriptionsService.registerSubscriber(topic, subscriber, dataManager));
                }
        , backPressureStrategy);
    }

    private List<String> parseInputTopics(List<T> input, SubscriptionEndpoints subscriptionEndpoints){
        return   input
                .stream()
                .map(obj -> {
                    val id = getId(obj, reflectionCache);
                    dataManager.findById(id).orElseThrow(() -> new RuntimeException(String.format("Cannot find %s by id %s", entityName, id)));
                    return String.format("%s(%s=%s)/%s", entityName, idFieldName, id, subscriptionEndpoints.getStringValue());
                })
                .collect(Collectors.toList());
    }

    public Flux<List<T>> onCreateSubscription(FluxSink.OverflowStrategy backPressureStrategy){
        val topic = String.format("%s/Create", toPlural(entityName));
        return generatePublisher(Collections.singletonList(topic), backPressureStrategy);
    }
    public void onCreateEvent(List<T> payload){
        val topic = String.format("%s/Create", toPlural(entityName));
        if(subscriptionsService.isRegisteredTopic(topic))
            subscriptionsService.publish(topic, payload);
    }

    public Flux<T> onUpdateSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return generatePublisher(parseInputTopics(toObserve, ON_UPDATE), backPressureStrategy);
    }
    public void onUpdateEvent(List<T> payload){
        payload.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_UPDATE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }

    public Flux<T> onDeleteSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return generatePublisher(parseInputTopics(toObserve, ON_DELETE), backPressureStrategy);
    }
    public void onDeleteEvent(List<T> deleted){
        deleted.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_DELETE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }

    public Flux<T> onArchiveSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return generatePublisher(parseInputTopics(toObserve, ON_ARCHIVE), backPressureStrategy);
    }

    public void onArchiveEvent(List<T> archived){
        archived.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_ARCHIVE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }

    public Flux<T> onDeArchiveSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return generatePublisher(parseInputTopics(toObserve, ON_DE_ARCHIVE), backPressureStrategy);
    }
    public void onDeArchiveEvent(List<T> deArchived){
        deArchived.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_DE_ARCHIVE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }
}
