package dev.sanda.apifi.test_utils;

import static dev.sanda.apifi.test_utils.StaticTestUtils.generateTestSubscriber;
import static dev.sanda.apifi.test_utils.StaticTestUtils.getClassMethod;
import static org.springframework.transaction.annotation.Propagation.NEVER;

import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods.TestSubscriberWhenMethod;
import java.lang.reflect.InvocationTargetException;
import lombok.SneakyThrows;
import org.springframework.transaction.annotation.Transactional;

public interface TestGraphQLService<TEntity> {
  @SneakyThrows
  default <TReturn> TReturn invokeEndpoint(String methodName, Object... args) {
    try {
      return (TReturn) getClassMethod(this, methodName).invoke(this, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  /*
   Will throw exception if runAssertions within transaction. This is intentional.
   Subscription tests cannot be runAssertions as single transactions due to the
   multithreaded nature of the internal pub-sub implementation for
   GraphQL subscriptions.
  */
  @Transactional(propagation = NEVER)
  @SneakyThrows
  default TestSubscriberWhenMethod invokeSubscriptionEndpoint(
    String methodName,
    Class targetReturnType,
    Object... args
  ) {
    return generateTestSubscriber(
      this,
      targetReturnType,
      getClassMethod(this, methodName),
      args,
      true
    );
  }

  /*
   Will throw exception if runAssertions within transaction. This is intentional.
   Subscription tests cannot be runAssertions as single transactions due to the
   multithreaded nature of the internal pub-sub implementation for
   GraphQL subscriptions.
  */
  @Transactional(propagation = NEVER)
  @SneakyThrows
  default TestSubscriberWhenMethod invokeCustomSubscriptionEndpoint(
    Object serviceInstance,
    String methodName,
    Class targetReturnType,
    Object... args
  ) {
    return generateTestSubscriber(
      this,
      targetReturnType,
      getClassMethod(serviceInstance, methodName),
      args,
      false
    );
  }
}
