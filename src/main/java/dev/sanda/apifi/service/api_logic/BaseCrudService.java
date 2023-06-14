package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.apifi.service.graphql_config.GraphQLSubscriptionSupport;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static dev.sanda.datafi.DatafiStaticUtils.getId;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseCrudService<T> {

  protected final ReflectionCache reflectionCache;

  protected final AsyncExecutorService asyncExecutorService;

  protected final GraphQLSubscriptionSupport graphQLSubscriptionSupport;

  protected DataManager<T> dataManager;
  protected ApiHooks<T> apiHooks;
  protected String entityName;
  protected String idFieldName;
  protected SubscriptionsLogicService<T> subscriptionsLogicService;

  public void init(
    DataManager<T> dataManager,
    ApiHooks<T> apiHooks,
    boolean datafiLoggingEnabled,
    SubscriptionsLogicService<T> subscriptionsLogicService
  ) {
    this.dataManager = dataManager;
    this.dataManager.setLoggingEnabled(datafiLoggingEnabled);
    this.apiHooks = apiHooks;
    this.entityName = dataManager.getClazzSimpleName();
    this.idFieldName =
      reflectionCache
        .getEntitiesCache()
        .get(dataManager.getClazzSimpleName())
        .getIdField()
        .getName();
    this.subscriptionsLogicService = subscriptionsLogicService;
  }

  protected void throw_entityNotFound(
    Object input,
    ReflectionCache reflectionCache
  ) {
    final RuntimeException exception = new RuntimeException(
      "Cannot find Entity " +
      input.getClass().getSimpleName() +
      " with id " +
      getId(input, reflectionCache)
    );
    logError(exception.toString());
    throw exception;
  }

  private void runAsync(Runnable runnable) {
    asyncExecutorService.executeAsync(runnable);
  }

  protected void fireSubscriptionEvent(Runnable runnable) {
    if (graphQLSubscriptionSupport.getHasSubscriptions()) runAsync(runnable);
  }

  private void log(String msg, boolean isError, Object... args) {
    runAsync(
      () -> {
        if (isError) log.error(msg, args); else log.info(msg, args);
      }
    );
  }

  protected void logInfo(String msg, Object... args) {
    log(msg, false, args);
  }

  protected void logError(String msg, Object... args) {
    log(msg, true, args);
  }
}
