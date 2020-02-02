package dev.sanda.apifi.annotations;

import dev.sanda.apifi.service.EmbeddedCollectionApiHooks;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows for custom business logic when pre/post mutating the state of the annotated Iterable<> field
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiHooks {
    Class<? extends EmbeddedCollectionApiHooks> value();
}
