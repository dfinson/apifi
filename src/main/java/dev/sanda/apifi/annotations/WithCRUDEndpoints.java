package dev.sanda.apifi.annotations;

import dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithCRUDEndpoints {
  CRUDEndpoints[] value();

  String secured() default "";

  String[] rolesAllowed() default "";

  String preAuthorize() default "";

  String postAuthorize() default "";

  String preFilter() default "";

  String preFilterTarget() default "";

  String postFilter() default "";
}
