package dev.sanda.apifi.annotations;

import dev.sanda.apifi.generator.entity.ForeignKeyCollectionResolverType;
import dev.sanda.apifi.service.EmbeddedCollectionApiHooks;
import dev.sanda.apifi.service.NullEmbeddedCollectionApiHooks;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static dev.sanda.apifi.generator.entity.ForeignKeyCollectionResolverType.ALL;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EmbeddedCollectionApi {
    Class<? extends EmbeddedCollectionApiHooks> apiHooks() default NullEmbeddedCollectionApiHooks.class;
    boolean associatePreExistingOnly() default false;
    ForeignKeyCollectionResolverType[] resolvers() default ALL;
}
