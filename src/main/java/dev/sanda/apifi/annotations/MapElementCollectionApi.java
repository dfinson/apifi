package dev.sanda.apifi.annotations;

import dev.sanda.apifi.code_generator.entity.MapElementCollectionEndpointType;
import dev.sanda.apifi.service.api_hooks.MapElementCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.NullMapElementCollectionApiHooks;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface MapElementCollectionApi {
  MapElementCollectionEndpointType[] endpoints();

  Class<? extends MapElementCollectionApiHooks> apiHooks() default NullMapElementCollectionApiHooks.class;

  String secured() default "";

  String[] rolesAllowed() default "";

  String preAuthorize() default "";

  String postAuthorize() default "";

  String preFilter() default "";

  String preFilterTarget() default "";

  String postFilter() default "";

  String securedPut() default "";

  String[] rolesAllowedPut() default "";

  String preAuthorizePut() default "";

  String postAuthorizePut() default "";

  String preFilterPut() default "";

  String preFilterTargetPut() default "";

  String postFilterPut() default "";

  String securedRemove() default "";

  String[] rolesAllowedRemove() default "";

  String preAuthorizeRemove() default "";

  String postAuthorizeRemove() default "";

  String preFilterRemove() default "";

  String preFilterTargetRemove() default "";

  String postFilterRemove() default "";

  String securedGetPaginated() default "";

  String[] rolesAllowedGetPaginated() default "";

  String preAuthorizeGetPaginated() default "";

  String postAuthorizeGetPaginated() default "";

  String preFilterGetPaginated() default "";

  String preFilterTargetGetPaginated() default "";

  String postFilterGetPaginated() default "";

  String securedFreeTextSearch() default "";

  String[] rolesAllowedFreeTextSearch() default "";

  String preAuthorizeFreeTextSearch() default "";

  String postAuthorizeFreeTextSearch() default "";

  String preFilterFreeTextSearch() default "";

  String preFilterTargetFreeTextSearch() default "";

  String postFilterFreeTextSearch() default "";
}
