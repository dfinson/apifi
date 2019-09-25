package org.sindaryn.apifi.annotations;

import org.sindaryn.apifi.service.ApiMetaOperations;
import org.sindaryn.apifi.service.EmbeddedCollectionMetaOperations;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiLevelMetaOperations {
    @AliasFor("value")
    Class<? extends ApiMetaOperations> metaOps();
    Class<? extends ApiMetaOperations> value();
}
