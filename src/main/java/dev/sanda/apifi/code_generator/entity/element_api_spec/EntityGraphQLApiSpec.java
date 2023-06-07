package dev.sanda.apifi.code_generator.entity.element_api_spec;

import static dev.sanda.datafi.DatafiStaticUtils.*;

import dev.sanda.apifi.annotations.*;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints;
import dev.sanda.datafi.code_generator.annotated_element_specs.AnnotatedElementSpec;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import lombok.Getter;
import lombok.val;

public class EntityGraphQLApiSpec extends AnnotatedElementSpec<TypeElement> {

  @Getter
  private List<FieldGraphQLApiSpec> fieldGraphQLApiSpecs;

  @Getter
  private List<CRUDEndpoints> mergedCrudEndpoints;

  @Getter
  private List<SubscriptionEndpoints> mergedSubscriptionEndpoints;

  public EntityGraphQLApiSpec(
    TypeElement entity,
    TypeElement apiSpecExtension
  ) {
    super(entity);
    if (apiSpecExtension != null) addAnnotations(apiSpecExtension);
    setFieldGraphQlApiSpecs(entity, apiSpecExtension);
    setMergedCrudEndpoints(entity, apiSpecExtension);
    setMergedSubscriptionEndpoints(entity, apiSpecExtension);
  }

  private void setMergedSubscriptionEndpoints(
    TypeElement entity,
    TypeElement apiSpecExtension
  ) {
    WithSubscriptionEndpoints entitySubscriptionEndpoints = entity.getAnnotation(
      WithSubscriptionEndpoints.class
    );
    WithSubscriptionEndpoints apiSpecExtensionSubscriptionEndpoints = null;
    if (apiSpecExtension != null) apiSpecExtensionSubscriptionEndpoints =
      apiSpecExtension.getAnnotation(WithSubscriptionEndpoints.class);
    Set<SubscriptionEndpoints> subscriptionEndpoints = new HashSet<>();
    if (entitySubscriptionEndpoints != null) subscriptionEndpoints.addAll(
      Arrays.asList(entitySubscriptionEndpoints.value())
    );
    if (
      apiSpecExtensionSubscriptionEndpoints != null
    ) subscriptionEndpoints.addAll(
      Arrays.asList(apiSpecExtensionSubscriptionEndpoints.value())
    );
    this.mergedSubscriptionEndpoints = new ArrayList<>(subscriptionEndpoints);
  }

  private void setMergedCrudEndpoints(
    TypeElement entity,
    TypeElement apiSpecExtension
  ) {
    WithCRUDEndpoints entityCrudEndpoints = entity.getAnnotation(
      WithCRUDEndpoints.class
    );
    WithCRUDEndpoints apiSpecExtensionCrudEndpoints = null;
    if (apiSpecExtension != null) apiSpecExtensionCrudEndpoints =
      apiSpecExtension.getAnnotation(WithCRUDEndpoints.class);
    Set<CRUDEndpoints> crudEndpoints = new HashSet<>();
    if (entityCrudEndpoints != null) crudEndpoints.addAll(
      Arrays.asList(entityCrudEndpoints.value())
    );
    if (apiSpecExtensionCrudEndpoints != null) crudEndpoints.addAll(
      Arrays.asList(apiSpecExtensionCrudEndpoints.value())
    );
    this.mergedCrudEndpoints = new ArrayList<>(crudEndpoints);
  }

  private void setFieldGraphQlApiSpecs(
    TypeElement entity,
    TypeElement apiSpecExtension
  ) {
    val fields = getFieldsOf(entity);
    val gettersByFieldName = getGettersOf(apiSpecExtension)
      .stream()
      .collect(
        Collectors.toMap(
          getter ->
            toCamelCase(
              getter.getSimpleName().toString().replaceFirst("get", "")
            ),
          Function.identity()
        )
      );
    fieldGraphQLApiSpecs =
      fields
        .stream()
        .map(
          field ->
            new FieldGraphQLApiSpec(
              field,
              gettersByFieldName.get(
                toCamelCase(field.getSimpleName().toString())
              )
            )
        )
        .collect(Collectors.toList());
  }

  @Override
  protected <A extends Annotation> Class<A>[] targetAnnotations() {
    return new Class[] {
      WithApiFreeTextSearchByFields.class,
      WithCRUDEndpoints.class,
      WithMethodLevelSecurity.class,
      WithServiceLevelSecurity.class,
      WithSubscriptionEndpoints.class,
    };
  }
}
