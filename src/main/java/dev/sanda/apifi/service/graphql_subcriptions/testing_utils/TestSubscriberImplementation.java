package dev.sanda.apifi.service.graphql_subcriptions.testing_utils;

import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static org.springframework.test.util.AssertionErrors.assertEquals;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods.AssertionLogic;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods.TestSubscriberAssertionMethods;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods.TestSubscriberThenMethods;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods.TestSubscriberWhenMethod;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import graphql.ExecutionResult;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Data
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
public class TestSubscriberImplementation
  implements
    Subscriber<ExecutionResult>,
    TestSubscriber,
    TestSubscriberWhenMethod,
    TestSubscriberAssertionMethods,
    TestSubscriberThenMethods {

  private final DataManager dataManager;
  private final AsyncExecutorService asyncExecutorService;
  private final PlatformTransactionManager transactionManager;
  private final ConfigValues configValues;
  private final ReflectionCache reflectionCache;

  private final Queue<ExecutionResult> results = new LinkedList<>();
  private Subscription subscription;
  private Class targetType;

  @Override
  public void onSubscribe(Subscription subscription) {
    log.info("new Test subscriber created");
    this.subscription = subscription;
    request();
  }

  @Override
  @SneakyThrows
  public synchronized void onNext(ExecutionResult executionResult) {
    results.add(executionResult);
    request();
  }

  @Override
  public void onError(Throwable throwable) {
    throw new RuntimeException(throwable);
  }

  @Override
  public void onComplete() {
    log.info("test subscription completed");
  }

  private <T> Object deserializeExecutionResult(
    ExecutionResult executionResult
  ) {
    assert executionResult != null;
    if (!executionResult.getErrors().isEmpty()) throw new RuntimeException(
      "got errors in execution result data"
    );
    Map<String, T> dataMap = executionResult.getData();
    T actualData = dataMap
      .entrySet()
      .stream()
      .findFirst()
      .orElseThrow(RuntimeException::new)
      .getValue();
    val mapper = nonTransactionalObjectMapper();
    if (actualData instanceof Collection) {
      val asCollection = (Collection) actualData;
      return asCollection
        .stream()
        .map(obj -> mapper.convertValue(obj, targetType))
        .collect(Collectors.toList());
    }
    return mapper.convertValue(actualData, targetType);
  }

  private void request() {
    Subscription subscription = this.subscription;
    if (subscription != null) {
      subscription.request(1);
    }
  }

  @Override
  public <T> TestSubscriberThenMethods expect(T expected) {
    return expect(expected, "");
  }

  @Override
  public <T> TestSubscriberThenMethods expect(
    T expected,
    String assertionMessage
  ) {
    new TransactionTemplate(transactionManager)
      .executeWithoutResult(
        status -> {
          val expectedValues = getExpectedValues(expected);
          val actualValues = getActualValues(expectedValues);
          assertEquals(assertionMessage, expectedValues, actualValues);
        }
      );
    return this;
  }

  @Override
  public <T> TestSubscriberThenMethods runAssertions(
    AssertionLogic<T> assertionLogic
  ) {
    new TransactionTemplate(transactionManager)
      .executeWithoutResult(
        status ->
          assertionLogic.runAssertions(
            (List<T>) getActualValues(new ArrayList<>())
          )
      );
    return this;
  }

  private ArrayList<Object> getActualValues(List<Object> expectedValues) {
    val actualValues = results
      .stream()
      .map(this::deserializeExecutionResult)
      .collect(Collectors.toCollection(ArrayList::new));
    ArrayList preResults;
    if (
      actualValues.size() == 1 &&
      actualValues.get(0) instanceof Collection &&
      ((Collection) actualValues.get(0)).size() == expectedValues.size()
    ) preResults =
      new ArrayList<>((Collection) actualValues.get(0)); else preResults =
      actualValues;
    return tryReloadCollection(preResults, dataManager, reflectionCache);
  }

  private <T> ArrayList<Object> getExpectedValues(T expected) {
    dataManager.setType(targetType);
    return tryReloadCollection(
      expected instanceof Collection
        ? (Collection) expected
        : Collections.singletonList(expected),
      dataManager,
      reflectionCache
    );
  }

  @Override
  public TestSubscriberAssertionMethods when(Runnable scenario) {
    results.clear();
    new TransactionTemplate(transactionManager)
      .executeWithoutResult(transactionStatus -> scenario.run());
    waitForActiveThreads();
    return this;
  }

  @SneakyThrows
  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void waitForActiveThreads() {
    asyncExecutorService.getExecutorService().shutdown();
    asyncExecutorService
      .getExecutorService()
      .awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    asyncExecutorService.setExecutorService(
      generateOptimalScheduledExecutorService()
    );
  }
}
