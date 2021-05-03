package dev.sanda.apifi.service.api_logic;

import static dev.sanda.datafi.DatafiStaticUtils.getId;

import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.apifi.service.graphql_config.GraphQLInstanceFactory;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DependsOn("ApiLogic")
public abstract class BaseCrudService<T> {

  @Autowired
  protected ReflectionCache reflectionCache;

  @Autowired
  protected AsyncExecutorService asyncExecutorService;

  @Autowired
  protected GraphQLInstanceFactory graphQLInstanceFactory;

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
    if (graphQLInstanceFactory.getHasSubscriptions()) runAsync(runnable);
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
