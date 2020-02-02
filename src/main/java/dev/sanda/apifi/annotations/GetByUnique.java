package dev.sanda.apifi.annotations;

import dev.sanda.datafi.annotations.finders.FindByUnique;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@FindByUnique
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GetByUnique {
}
