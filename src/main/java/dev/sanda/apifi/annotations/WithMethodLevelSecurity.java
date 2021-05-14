package dev.sanda.apifi.annotations;

import static dev.sanda.apifi.code_generator.entity.CRUDEndpoints._NONE;
import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints.NONE;

import dev.sanda.apifi.code_generator.entity.CRUDEndpoints;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints;
import java.lang.annotation.*;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(WithMethodLevelSecurityAccumulator.class)
public @interface WithMethodLevelSecurity {
  String secured() default "";

  String[] rolesAllowed() default "";

  String preAuthorize() default "";

  String postAuthorize() default "";

  String preFilter() default "";

  String preFilterTarget() default "";

  String postFilter() default "";

  CRUDEndpoints[] crudEndpointTargets() default _NONE;

  SubscriptionEndpoints[] subscriptionEndpointTargets() default NONE;
}
