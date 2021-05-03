package dev.sanda.apifi.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RUNTIME)
public @interface WithSubscriptionEndpoints {
  SubscriptionEndpoints[] value();
}
