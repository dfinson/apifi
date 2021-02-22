package dev.sanda.apifi.annotations;

import dev.sanda.apifi.code_generator.entity.CRUDEndpoints;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithCRUDEndpoints {
    CRUDEndpoints[] value();
}
