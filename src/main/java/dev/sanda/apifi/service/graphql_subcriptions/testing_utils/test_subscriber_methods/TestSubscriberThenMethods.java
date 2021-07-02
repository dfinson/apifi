package dev.sanda.apifi.service.graphql_subcriptions.testing_utils.test_subscriber_methods;

import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.TestSubscriber;

public interface TestSubscriberThenMethods {
  default TestSubscriber then() {
    return (TestSubscriber) this;
  }

  default TestSubscriber then(Runnable preScenarioSetup) {
    preScenarioSetup.run();
    return then();
  }
}
