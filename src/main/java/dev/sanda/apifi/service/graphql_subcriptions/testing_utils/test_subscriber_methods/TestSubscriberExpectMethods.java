package dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods;

public interface TestSubscriberExpectMethods {
  <T> TestSubscriberThenMethods expect(T expected);
  <T> TestSubscriberThenMethods expect(T expected, String message);
}
