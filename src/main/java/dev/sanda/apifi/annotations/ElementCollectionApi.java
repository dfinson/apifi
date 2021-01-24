package dev.sanda.apifi.annotations;

import dev.sanda.apifi.generator.entity.ElementCollectionEndpointType;
import dev.sanda.apifi.service.ElementCollectionApiHooks;
import dev.sanda.apifi.service.EntityCollectionApiHooks;
import dev.sanda.apifi.service.NullElementCollectionApiHooks;
import dev.sanda.apifi.service.NullEntityCollectionApiHooks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ElementCollectionApi {
    ElementCollectionEndpointType[] endpoints();
    Class<? extends ElementCollectionApiHooks> apiHooks() default NullElementCollectionApiHooks.class;

    String secured() default "";
    String[] rolesAllowed() default "";
    String preAuthorize() default "";
    String postAuthorize() default "";
    String preFilter() default "";
    String preFilterTarget() default "";
    String postFilter() default "";

    String securedAdd() default "";
    String[] rolesAllowedAdd() default "";
    String preAuthorizeAdd() default "";
    String postAuthorizeAdd() default "";
    String preFilterAdd() default "";
    String preFilterTargetAdd() default "";
    String postFilterAdd() default "";

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
