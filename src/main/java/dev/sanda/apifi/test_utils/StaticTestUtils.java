package dev.sanda.apifi.test_utils;

import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.TestSubscriberImplementation;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.TestSubscriptionsHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.FluxSink;

@Slf4j
public class StaticTestUtils {

  public static Method getClassMethod(
    Object serviceInstance,
    String methodName
  ) throws NoSuchMethodException {
    final Method method = Arrays
      .stream(serviceInstance.getClass().getDeclaredMethods())
      .collect(Collectors.toMap(Method::getName, Function.identity()))
      .get(methodName);
    if (method == null) throw new NoSuchMethodException();
    return method;
  }

  @SneakyThrows
  public static <T extends TestGraphQLService> TestSubscriptionsHandler getTestSubscriptionsHandler(
    T serviceInstance
  ) {
    return (TestSubscriptionsHandler) Arrays
      .stream(serviceInstance.getClass().getDeclaredFields())
      .filter(field -> field.getType().equals(TestSubscriptionsHandler.class))
      .peek(field -> field.setAccessible(true))
      .findFirst()
      .orElseThrow(RuntimeException::new)
      .get(serviceInstance);
  }

  public static TestSubscriberImplementation generateTestSubscriber(
    TestGraphQLService serviceInstance,
    Class targetReturnType,
    Method methodToInvoke,
    Object[] args,
    boolean setDefaultBackpressureStrategy
  ) {
    args =
      setDefaultBackpressureStrategy
        ? setBufferBackpressureStrategyIfNotPresent(args)
        : args;
    val subscriber = getTestSubscriptionsHandler(serviceInstance)
      .handleSubscriptionRequest(methodToInvoke, targetReturnType, args);
    subscriber.setTargetType(targetReturnType);
    return subscriber;
  }

  private static Object[] setBufferBackpressureStrategyIfNotPresent(
    Object[] args
  ) {
    Object[] finalArgs = new Object[args.length + 1];
    for (int i = 0, argsLength = args.length; i < argsLength; i++) {
      Object arg = args[i];
      finalArgs[i] = arg;
      if (arg instanceof FluxSink.OverflowStrategy) {
        return args;
      }
    }
    log.warn(
      "You are invoking a GraphQL subscription without explicitly specifying an overflow strategy. " +
      "This value is defaulting to BUFFER, but it is better to explicitly specify an overflow strategy."
    );
    finalArgs[finalArgs.length - 1] = FluxSink.OverflowStrategy.BUFFER;
    return finalArgs;
  }
}
