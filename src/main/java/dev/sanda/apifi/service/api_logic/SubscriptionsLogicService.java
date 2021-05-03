package dev.sanda.apifi.service.api_logic;

import static dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints.*;
import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints.*;
import static dev.sanda.datafi.DatafiStaticUtils.getId;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;

import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.apifi.service.api_hooks.EntityCollectionApiHooks;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionsService;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Service
@Scope("prototype")
public class SubscriptionsLogicService<T> {

  @Autowired
  private SubscriptionsService subscriptionsService;

  @Autowired
  private ReflectionCache reflectionCache;

  private DataManager<T> dataManager;
  private ApiHooks<T> apiHooks;
  private String entityName;
  private String idFieldName;

  public void init(DataManager<T> dataManager, ApiHooks<T> apiHooks) {
    this.dataManager = dataManager;
    this.apiHooks = apiHooks;
    this.entityName = dataManager.getClazzSimpleName();
    this.idFieldName =
      reflectionCache.getEntitiesCache().get(entityName).getIdField().getName();
  }

  private <E> Flux<E> generatePublisher(
    List<String> topics,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return Flux.create(
      subscriber -> {
        val id = UUID.randomUUID().toString();
        subscriber.onDispose(
          () ->
            topics.forEach(
              topic -> subscriptionsService.removeTopicSubscriber(topic, id)
            )
        );
        topics.forEach(
          topic ->
            subscriptionsService.registerTopicSubscriber(
              topic,
              id,
              subscriber,
              dataManager
            )
        );
      },
      backPressureStrategy
    );
  }

  private List<String> parseInputTopics(
    List<T> input,
    String subscriptionEndpoints
  ) {
    return input
      .stream()
      .map(
        obj -> {
          val id = getId(obj, reflectionCache);
          dataManager
            .findById(id)
            .orElseThrow(
              () ->
                new RuntimeException(
                  String.format("Cannot find %s by id %s", entityName, id)
                )
            );
          return String.format(
            "%s(%s=%s)/%s",
            entityName,
            idFieldName,
            id,
            subscriptionEndpoints
          );
        }
      )
      .collect(Collectors.toList());
  }

  public Flux<List<T>> onCreateSubscription(
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    val topic = String.format("%s/Create", toPlural(entityName));
    return generatePublisher(
      Collections.singletonList(topic),
      backPressureStrategy
    );
  }

  public void onCreateEvent(List<T> payload) {
    val topic = String.format("%s/Create", toPlural(entityName));
    if (apiHooks != null) apiHooks.preOnCreate(payload, dataManager, topic);
    subscriptionsService.publishToTopic(topic, payload);
    if (apiHooks != null) apiHooks.postOnCreate(payload, dataManager, topic);
  }

  public Flux<T> onUpdateSubscription(
    List<T> toObserve,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseInputTopics(toObserve, ON_UPDATE.getStringValue()),
      backPressureStrategy
    );
  }

  public void onUpdateEvent(List<T> payload) {
    payload.forEach(
      obj -> {
        val topic = parseInputTopics(
          Collections.singletonList(obj),
          ON_UPDATE.getStringValue()
        )
          .get(0);
        if (apiHooks != null) apiHooks.preOnUpdate(obj, dataManager, topic);
        subscriptionsService.publishToTopic(topic, obj);
        if (apiHooks != null) apiHooks.postOnUpdate(obj, dataManager, topic);
      }
    );
  }

  public Flux<T> onDeleteSubscription(
    List<T> toObserve,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseInputTopics(toObserve, ON_DELETE.getStringValue()),
      backPressureStrategy
    );
  }

  public void onDeleteEvent(List<T> deleted) {
    deleted.forEach(
      obj -> {
        val topic = parseInputTopics(
          Collections.singletonList(obj),
          ON_DELETE.getStringValue()
        )
          .get(0);
        if (apiHooks != null) apiHooks.preOnDelete(obj, dataManager, topic);
        subscriptionsService.publishToTopic(topic, obj);
        if (apiHooks != null) apiHooks.postOnDelete(obj, dataManager, topic);
      }
    );
  }

  public Flux<T> onArchiveSubscription(
    List<T> toObserve,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseInputTopics(toObserve, ON_ARCHIVE.getStringValue()),
      backPressureStrategy
    );
  }

  public void onArchiveEvent(List<T> archived) {
    archived.forEach(
      obj -> {
        val topic = parseInputTopics(
          Collections.singletonList(obj),
          ON_ARCHIVE.getStringValue()
        )
          .get(0);
        if (apiHooks != null) apiHooks.preOnArchive(obj, dataManager, topic);
        subscriptionsService.publishToTopic(topic, obj);
        if (apiHooks != null) apiHooks.postOnArchive(obj, dataManager, topic);
      }
    );
  }

  public Flux<T> onDeArchiveSubscription(
    List<T> toObserve,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseInputTopics(toObserve, ON_DE_ARCHIVE.getStringValue()),
      backPressureStrategy
    );
  }

  public void onDeArchiveEvent(List<T> deArchived) {
    deArchived.forEach(
      obj -> {
        val topic = parseInputTopics(
          Collections.singletonList(obj),
          ON_DE_ARCHIVE.getStringValue()
        )
          .get(0);
        if (apiHooks != null) apiHooks.preOnDeArchive(obj, dataManager, topic);
        subscriptionsService.publishToTopic(topic, obj);
        if (apiHooks != null) apiHooks.postOnDeArchive(obj, dataManager, topic);
      }
    );
  }

  // entity collection API subscriptions

  private List<String> parseEntityCollectionTopic(
    T owner,
    String collectionName,
    String subscriptionEndpoint
  ) {
    return Collections.singletonList(
      entityName +
      "(" +
      idFieldName +
      "=" +
      getId(owner, reflectionCache) +
      ")." +
      collectionName +
      "/" +
      subscriptionEndpoint
    );
  }

  public <TCollection> Flux<List<TCollection>> onAssociateWithSubscription(
    T owner,
    String collectionFieldName,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseEntityCollectionTopic(
        owner,
        collectionFieldName,
        ON_ASSOCIATE_WITH.getStringValue()
      ),
      backPressureStrategy
    );
  }

  public <TCollection> void onAssociateWithEvent(
    T owner,
    String collectionFieldName,
    List<TCollection> payload,
    DataManager<TCollection> collectionDataManager,
    EntityCollectionApiHooks<TCollection, T> entityCollectionApiHooks
  ) {
    val topic = parseEntityCollectionTopic(
      owner,
      collectionFieldName,
      ON_ASSOCIATE_WITH.getStringValue()
    )
      .get(0);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.preOnAssociate(
      payload,
      dataManager,
      collectionDataManager,
      topic
    );
    subscriptionsService.publishToTopic(topic, payload);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postOnAssociate(
      payload,
      dataManager,
      collectionDataManager,
      topic
    );
  }

  public <TCollection> Flux<List<TCollection>> onUpdateInSubscription(
    T owner,
    String collectionFieldName,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseEntityCollectionTopic(
        owner,
        collectionFieldName,
        ON_UPDATE_IN.getStringValue()
      ),
      backPressureStrategy
    );
  }

  public <TCollection> void onUpdateInEvent(
    T owner,
    String collectionFieldName,
    List<TCollection> payload,
    DataManager<TCollection> collectionDataManager,
    EntityCollectionApiHooks<TCollection, T> entityCollectionApiHooks
  ) {
    val topic = parseEntityCollectionTopic(
      owner,
      collectionFieldName,
      ON_UPDATE_IN.getStringValue()
    )
      .get(0);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.preOnUpdateIn(
      payload,
      dataManager,
      collectionDataManager,
      topic
    );
    subscriptionsService.publishToTopic(topic, payload);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postOnUpdateIn(
      payload,
      dataManager,
      collectionDataManager,
      topic
    );
  }

  public <TCollection> Flux<List<TCollection>> onRemoveFromSubscription(
    T owner,
    String collectionFieldName,
    FluxSink.OverflowStrategy backPressureStrategy
  ) {
    return generatePublisher(
      parseEntityCollectionTopic(
        owner,
        collectionFieldName,
        ON_REMOVE_FROM.getStringValue()
      ),
      backPressureStrategy
    );
  }

  public <TCollection> void onRemoveFromEvent(
    T owner,
    String collectionFieldName,
    List<TCollection> payload,
    DataManager<TCollection> collectionDataManager,
    EntityCollectionApiHooks<TCollection, T> entityCollectionApiHooks
  ) {
    val topic = parseEntityCollectionTopic(
      owner,
      collectionFieldName,
      ON_REMOVE_FROM.getStringValue()
    )
      .get(0);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.preOnRemoveFrom(
      payload,
      dataManager,
      collectionDataManager,
      topic
    );
    subscriptionsService.publishToTopic(topic, payload);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postOnRemoveFrom(
      payload,
      dataManager,
      collectionDataManager,
      topic
    );
  }
}
