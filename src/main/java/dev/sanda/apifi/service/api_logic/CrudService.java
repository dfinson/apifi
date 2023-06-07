package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.graphql_config.GraphQLSubscriptionSupport;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static dev.sanda.datafi.DatafiStaticUtils.getId;
import static dev.sanda.datafi.DatafiStaticUtils.throwEntityNotFoundException;

@Component
@Scope("prototype")
public class CrudService<T> extends BaseCrudService<T> {

  @Autowired
  public CrudService(
    ReflectionCache reflectionCache,
    AsyncExecutorService asyncExecutorService,
    GraphQLSubscriptionSupport graphQLSubscriptionSupport
  ) {
    super(reflectionCache, asyncExecutorService, graphQLSubscriptionSupport);
  }

  public T getByIdImpl(Object id) {
    if (apiHooks != null) apiHooks.preGetById(id, dataManager);
    T result = dataManager.findById(id).orElse(null);
    if (result == null) throwEntityNotFoundException(
      dataManager.getClazzSimpleName(),
      id
    );
    if (apiHooks != null) apiHooks.postGetById(result, dataManager);
    logInfo("getById: Got {} by id #{}", dataManager.getClazzSimpleName(), id);
    return result;
  }

  public T apiFindByUniqueImpl(String fieldName, Object fieldValue) {
    if (apiHooks != null) apiHooks.preApiFindByUnique(
      fieldName,
      fieldValue,
      dataManager
    );
    T result = dataManager.findByUnique(fieldName, fieldValue).orElse(null);
    if (result == null) throwEntityNotFoundException(
      dataManager.getClazzSimpleName(),
      fieldValue
    );
    if (apiHooks != null) apiHooks.postApiFindByUnique(
      fieldName,
      fieldValue,
      result,
      dataManager
    );
    logInfo(
      "apiFindByUnique: Found {} with id {} by {} == {}",
      dataManager.getClazzSimpleName(),
      getId(result, reflectionCache),
      fieldName,
      fieldValue
    );
    return result;
  }

  public T createImpl(T input) {
    if (apiHooks != null) apiHooks.preCreate(input, dataManager);
    val result = dataManager.save(input);
    if (apiHooks != null) apiHooks.postCreate(input, result, dataManager);
    logInfo(
      "create: Created {} with id #{}",
      dataManager.getClazzSimpleName(),
      getId(result, reflectionCache)
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onCreateEvent(Collections.singletonList(result))
    );
    return result;
  }

  public T updateImpl(T input) {
    if (input == null) {
      throw new IllegalArgumentException(
        String.format(
          "Illegal attempt to update %s instance with null input",
          entityName
        )
      );
    }
    val id = getId(input, reflectionCache);
    T toUpdate = getByIdImpl(id);
    if (toUpdate == null) throw_entityNotFound(input, reflectionCache);
    if (apiHooks != null) apiHooks.preUpdate(input, toUpdate, dataManager);
    dataManager.cascadeUpdate(toUpdate, input);
    val result = dataManager.save(toUpdate);
    if (apiHooks != null) apiHooks.postUpdate(
      input,
      toUpdate,
      result,
      dataManager
    );
    logInfo(
      "update: Updated {} with id #{}",
      dataManager.getClazzSimpleName(),
      getId(result, reflectionCache)
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onUpdateEvent(Collections.singletonList(result))
    );
    return result;
  }

  public T deleteImpl(T input) {
    val id = getId(input, reflectionCache);
    T toDelete = getByIdImpl(id);
    if (apiHooks != null) apiHooks.preDelete(input, toDelete, dataManager);
    dataManager.deleteById(id);
    if (apiHooks != null) apiHooks.postDelete(input, toDelete, dataManager);
    logInfo(
      "delete: deleted {} with id #{}",
      dataManager.getClazzSimpleName(),
      id
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onDeleteEvent(
        Collections.singletonList(toDelete)
      )
    );
    return toDelete;
  }

  public <A extends Archivable> T archiveImpl(A input) {
    val id = getId(input, reflectionCache);
    T toArchive = getByIdImpl(id);
    if (toArchive == null) throw_entityNotFound(input, reflectionCache);
    if (apiHooks != null) apiHooks.preArchive(
      (T) input,
      toArchive,
      dataManager
    );
    input.setIsArchived(true);
    val result = dataManager.save(toArchive);
    if (apiHooks != null) apiHooks.postArchive((T) input, result, dataManager);
    logInfo(
      "archive: Archived {} with id: {}",
      dataManager.getClazzSimpleName(),
      id
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onArchiveEvent(
        Collections.singletonList(result)
      )
    );
    return result;
  }

  public <A extends Archivable> T deArchiveImpl(A input) {
    val id = getId(input, reflectionCache);
    T toArchive = getByIdImpl(id);
    if (toArchive == null) throw_entityNotFound(input, reflectionCache);
    if (apiHooks != null) apiHooks.preDeArchive(
      (T) input,
      toArchive,
      dataManager
    );
    input.setIsArchived(false);
    val result = dataManager.save(toArchive);
    if (apiHooks != null) apiHooks.postDeArchive(
      (T) input,
      result,
      dataManager
    );
    logInfo(
      "deArchive: De-Archived {} with id: {}",
      dataManager.getClazzSimpleName(),
      id
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onDeArchiveEvent(
        Collections.singletonList(result)
      )
    );
    return result;
  }
}
