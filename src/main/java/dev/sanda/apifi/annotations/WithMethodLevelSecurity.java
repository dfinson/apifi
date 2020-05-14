package dev.sanda.apifi.annotations;

import dev.sanda.apifi.generator.entity.CRUDEndpoints;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
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
    CRUDEndpoints[] targets();
}
