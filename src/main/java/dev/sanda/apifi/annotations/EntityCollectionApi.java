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

  /******* spring security annotations ********/

  // universal annotations - if specified will apply to all created endpoints
  String secured() default "";

  String[] rolesAllowed() default "";

  String preAuthorize() default "";

  String postAuthorize() default "";

  String preFilter() default "";

  String preFilterTarget() default "";

  String postFilter() default "";

  // annotations for 'GetPaginated' endpoint
  String securedGetPaginated() default "";

  String[] rolesAllowedGetPaginated() default "";

  String preAuthorizeGetPaginated() default "";

  String postAuthorizeGetPaginated() default "";

  String preFilterGetPaginated() default "";

  String preFilterTargetGetPaginated() default "";

  String postFilterGetPaginated() default "";

  // annotations for 'PaginatedFreeTextSearch' endpoint
  String securedPaginatedFreeTextSearch() default "";

  String[] rolesAllowedPaginatedFreeTextSearch() default "";

  String preAuthorizePaginatedFreeTextSearch() default "";

  String postAuthorizePaginatedFreeTextSearch() default "";

  String preFilterPaginatedFreeTextSearch() default "";

  String preFilterTargetPaginatedFreeTextSearch() default "";

  String postFilterPaginatedFreeTextSearch() default "";

  // annotations for 'GET' endpoint
  String securedGet() default "";

  String[] rolesAllowedGet() default "";

  String preAuthorizeGet() default "";

  String postAuthorizeGet() default "";

  String preFilterGet() default "";

  String preFilterTargetGet() default "";

  String postFilterGet() default "";

  // annotations for 'AssociateWith' endpoint
  String securedAssociateWith() default "";

  String[] rolesAllowedAssociateWith() default "";

  String preAuthorizeAssociateWith() default "";

  String postAuthorizeAssociateWith() default "";

  String preFilterAssociateWith() default "";

  String preFilterTargetAssociateWith() default "";

  String postFilterAssociateWith() default "";

  // annotations for 'RemoveFrom' endpoint
  String securedRemoveFrom() default "";

  String[] rolesAllowedRemoveFrom() default "";

  String preAuthorizeRemoveFrom() default "";

  String postAuthorizeRemoveFrom() default "";

  String preFilterRemoveFrom() default "";

  String preFilterTargetRemoveFrom() default "";

  String postFilterRemoveFrom() default "";

  // annotations for 'UpdateIn' endpoint
  String securedUpdateIn() default "";

  String[] rolesAllowedUpdateIn() default "";

  String preAuthorizeUpdateIn() default "";

  String postAuthorizeUpdateIn() default "";

  String preFilterUpdateIn() default "";

  String preFilterTargetUpdateIn() default "";

  String postFilterUpdateIn() default "";

  // annotations for 'OnAssociateWith' subscription endpoint
  String securedOnAssociateWith() default "";

  String[] rolesAllowedOnAssociateWith() default "";

  String preAuthorizeOnAssociateWith() default "";

  String postAuthorizeOnAssociateWith() default "";

  String preFilterOnAssociateWith() default "";

  String preFilterTargetOnAssociateWith() default "";

  String postFilterOnAssociateWith() default "";

  // annotations for 'OnUpdateIn' subscription endpoint
  String securedOnUpdateIn() default "";

  String[] rolesAllowedOnUpdateIn() default "";

  String preAuthorizeOnUpdateIn() default "";

  String postAuthorizeOnUpdateIn() default "";

  String preFilterOnUpdateIn() default "";

  String preFilterTargetOnUpdateIn() default "";

  String postFilterOnUpdateIn() default "";

  // annotations for 'OnRemoveFrom' subscription endpoint
  String securedOnRemoveFrom() default "";

  String[] rolesAllowedOnRemoveFrom() default "";

  String preAuthorizeOnRemoveFrom() default "";

  String postAuthorizeOnRemoveFrom() default "";

  String preFilterOnRemoveFrom() default "";

  String preFilterTargetOnRemoveFrom() default "";

  String postFilterOnRemoveFrom() default "";
}
