package dev.sanda.apifi.annotations;

import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows for annotations based, full integration of spring security in the autogenerated api.
 * The names are fairly self explanatory given familiarity with spring
 * security annotations.
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithServiceLevelSecurity {
    String secured() default "";
    String[] rolesAllowed() default "";
    String preAuthorize() default "";
    String postAuthorize() default "";
    String preFilter() default "";
    String preFilterTarget() default "";
    String postFilter() default "";
}
