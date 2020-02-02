package dev.sanda.apifi.annotations;

import dev.sanda.datafi.annotations.finders.FindBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@FindBy
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GetBy {
}
