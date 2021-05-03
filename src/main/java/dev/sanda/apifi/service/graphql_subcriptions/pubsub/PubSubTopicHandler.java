package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import static dev.sanda.datafi.DatafiStaticUtils.getIdList;

import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import reactor.core.publisher.FluxSink;

@Data
@Slf4j
public class PubSubTopicHandler {

  private final String id;
  private final FluxSink downStreamSubscriber;
  private final DataManager dataManager;
  private final ReflectionCache reflectionCache;
  private final ConfigValues configValues;

  @SneakyThrows
  public void handleDataInTransaction(Object data) {
    log.debug(
      "As its name suggests, this method should be run within a transaction so as to avoid lazy loading exceptions"
    );
    if (isSingleEntity(data)) data = loadSingleEntity(data); else if (
      isEntityCollection(data)
    ) data = loadEntityCollection((Collection) data);
    downStreamSubscriber.next(data);
  }

  @SneakyThrows
  @SuppressWarnings("BusyWait")
  private List loadEntityCollection(Collection data) {
    List loadedData;
    val retryInterval = configValues.getPendingTransactionRetryInterval();
    val maxWaitTime = configValues.getPendingTransactionTimeout();
    var currentDelta = 0L;
    do {
      loadedData = dataManager.findAllById(getIdList(data, reflectionCache));
      if (loadedData.isEmpty()) {
        log.info(
          "Could not reload published data in new " +
          "session due to the relevant transaction not " +
          "having committed yet. Retrying in " +
          retryInterval +
          " milliseconds."
        );
        currentDelta += retryInterval;
        Thread.sleep(retryInterval);
      }
    } while (loadedData.isEmpty() && currentDelta < maxWaitTime);
    return loadedData;
  }

  @SneakyThrows
  private Object loadSingleEntity(Object data) {
    Object loadedData;
    val retryInterval = configValues.getPendingTransactionRetryInterval();
    val maxWaitTime = configValues.getPendingTransactionTimeout();
    var currentDelta = 0L;
    do {
      loadedData =
        dataManager
          .findById(DatafiStaticUtils.getId(data, reflectionCache))
          .orElse(null);
      if (loadedData == null) {
        log.info(
          "Could not reload published data in new " +
          "session due to the relevant transaction not " +
          "having committed yet. Retrying in " +
          retryInterval +
          " milliseconds."
        );
        currentDelta += retryInterval;
        Thread.sleep(retryInterval);
      }
    } while (loadedData == null && currentDelta < maxWaitTime);
    return loadedData;
  }

  private boolean isEntityCollection(Object data) {
    return (
      data instanceof Collection &&
      !((Collection) data).isEmpty() &&
      isSingleEntity(((Collection) data).iterator().next())
    );
  }

  private boolean isSingleEntity(Object data) {
    return (
      !(data instanceof Collection) &&
      reflectionCache
        .getEntitiesCache()
        .containsKey(data.getClass().getSimpleName())
    );
  }

  public void complete() {
    if (!downStreamSubscriber.isCancelled()) downStreamSubscriber.complete();
  }

  public void completeWithError(Throwable error) {
    if (!downStreamSubscriber.isCancelled()) downStreamSubscriber.error(error);
  }
}
