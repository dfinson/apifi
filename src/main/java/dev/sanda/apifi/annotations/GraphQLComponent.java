package dev.sanda.apifi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GraphQL operations defined within
 * Spring managed beans annotated with this annotation
 * will be included within the runtime GraphQL schema.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphQLComponent {
}
