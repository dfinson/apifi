package org.sindaryn.apifi.annotations;

import org.sindaryn.apifi.service.EmbeddedCollectionMetaOperations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows for custom business logic when pre/post mutating the state of the annotated Iterable<> field
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetaOperations {
    Class<? extends EmbeddedCollectionMetaOperations> metaOps();
}
