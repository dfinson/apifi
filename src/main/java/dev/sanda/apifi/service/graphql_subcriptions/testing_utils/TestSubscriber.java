package dev.sanda.apifi.service.graphql_subcriptions.testing_utils;

import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods.TestSubscriberExpectMethods;

public interface TestSubscriber {
  TestSubscriberExpectMethods when(Runnable scenario);
}
