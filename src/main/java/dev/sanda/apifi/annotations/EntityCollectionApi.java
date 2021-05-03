package dev.sanda.apifi.annotations;

import dev.sanda.apifi.code_generator.entity.EntityCollectionEndpointType;
import dev.sanda.apifi.service.api_hooks.EntityCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.NullEntityCollectionApiHooks;
import dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityCollectionApi {
  Class<? extends EntityCollectionApiHooks> apiHooks() default NullEntityCollectionApiHooks.class;

  boolean associatePreExistingOnly() default false;

  EntityCollectionEndpointType[] endpoints();

  EntityCollectionSubscriptionEndpoints[] subscriptions() default EntityCollectionSubscriptionEndpoints.NONE;

  String[] freeTextSearchFields() default "";

  String secured() default "";

  String[] rolesAllowed() default "";

  String preAuthorize() default "";

  String postAuthorize() default "";

  String preFilter() default "";

  String preFilterTarget() default "";

  String postFilter() default "";

  String securedGetPaginated() default "";

  String[] rolesAllowedGetPaginated() default "";

  String preAuthorizeGetPaginated() default "";

  String postAuthorizeGetPaginated() default "";

  String preFilterGetPaginated() default "";

  String preFilterTargetGetPaginated() default "";

  String postFilterGetPaginated() default "";

  String securedPaginatedFreeTextSearch() default "";

  String[] rolesAllowedPaginatedFreeTextSearch() default "";

  String preAuthorizePaginatedFreeTextSearch() default "";

  String postAuthorizePaginatedFreeTextSearch() default "";

  String preFilterPaginatedFreeTextSearch() default "";

  String preFilterTargetPaginatedFreeTextSearch() default "";

  String postFilterPaginatedFreeTextSearch() default "";

  String securedGet() default "";

  String[] rolesAllowedGet() default "";

  String preAuthorizeGet() default "";

  String postAuthorizeGet() default "";

  String preFilterGet() default "";

  String preFilterTargetGet() default "";

  String postFilterGet() default "";

  String securedAssociateWith() default "";

  String[] rolesAllowedAssociateWith() default "";

  String preAuthorizeAssociateWith() default "";

  String postAuthorizeAssociateWith() default "";

  String preFilterAssociateWith() default "";

  String preFilterTargetAssociateWith() default "";

  String postFilterAssociateWith() default "";

  String securedRemoveFrom() default "";

  String[] rolesAllowedRemoveFrom() default "";

  String preAuthorizeRemoveFrom() default "";

  String postAuthorizeRemoveFrom() default "";

  String preFilterRemoveFrom() default "";

  String preFilterTargetRemoveFrom() default "";

  String postFilterRemoveFrom() default "";

  String securedUpdateIn() default "";

  String[] rolesAllowedUpdateIn() default "";

  String preAuthorizeUpdateIn() default "";

  String postAuthorizeUpdateIn() default "";

  String preFilterUpdateIn() default "";

  String preFilterTargetUpdateIn() default "";

  String postFilterUpdateIn() default "";
}
