package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionType;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionsService;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionType.*;
import static dev.sanda.datafi.DatafiStaticUtils.getId;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER;

@Service
@Scope("prototype")
public class SubscriptionsLogicService<T> extends BaseCrudService<T> {


    @Autowired
    private SubscriptionsService subscriptionsService;

    public final static FluxSink.OverflowStrategy DEFAULT_OVERFLOW_STRATEGY = BUFFER;


    private <E> Flux<E> generatePublisher(List<String> topics, FluxSink.OverflowStrategy backPressure){
        return Flux.create(
                subscriber -> {
                    subscriber.onDispose(
                            () -> topics.forEach(topic -> subscriptionsService.removeSubscriber(topic, subscriber))
                    );
                    topics.forEach(topic -> subscriptionsService.registerSubscriber(topic, subscriber));
                }
        , backPressure);
    }

    private List<String> parseInputTopics(List<T> input, SubscriptionType subscriptionType){
        return   input
                .stream()
                .map(obj -> {
                    val id = getId(obj, reflectionCache);
                    dataManager.findById(id).orElseThrow(() -> new RuntimeException(String.format("Cannot find %s by id %s", entityName, id)));
                    return String.format("%s(%s=%s)/%s", entityName, idFieldName, id, subscriptionType.getStringValue());
                })
                .collect(Collectors.toList());
    }

    public Flux<List<T>> onCreateSubscription(FluxSink.OverflowStrategy backPressure){
        val topic = String.format("%s/Create", toPlural(entityName));
        return generatePublisher(Collections.singletonList(topic), backPressure);
    }
    public void onCreateEvent(List<T> payload){
        val topic = String.format("%s/Create", toPlural(entityName));
        if(subscriptionsService.isRegisteredTopic(topic))
            subscriptionsService.publish(topic, payload);
    }

    public Flux<T> onUpdateSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressure){
        return generatePublisher(parseInputTopics(toObserve, ON_UPDATE), backPressure);
    }
    public void onUpdateEvent(List<T> payload){
        payload.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_UPDATE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }

    public Flux<T> onDeleteSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressure){
        return generatePublisher(parseInputTopics(toObserve, ON_DELETE), backPressure);
    }
    public void onDeleteEvent(List<T> deleted){
        deleted.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_DELETE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }

    public Flux<T> onArchiveSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressure){
        return generatePublisher(parseInputTopics(toObserve, ON_ARCHIVE), backPressure);
    }

    public void onArchiveEvent(List<T> archived){
        archived.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_ARCHIVE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }

    public Flux<T> onDeArchiveSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressure){
        return generatePublisher(parseInputTopics(toObserve, ON_DE_ARCHIVE), backPressure);
    }
    public void onDeArchiveEvent(List<T> deArchived){
        deArchived.forEach(obj -> {
            val topic = parseInputTopics(Collections.singletonList(obj), ON_DE_ARCHIVE).get(0);
            if(subscriptionsService.isRegisteredTopic(topic))
                subscriptionsService.publish(topic, obj);
        });
    }
}
