package dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods;

import java.util.List;

@FunctionalInterface
public interface AssertionLogic<T> {
  void runAssertions(List<T> actualValues);
}
