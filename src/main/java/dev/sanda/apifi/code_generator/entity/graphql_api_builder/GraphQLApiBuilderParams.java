package dev.sanda.apifi.code_generator.entity.graphql_api_builder;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.isIterable;

@Getter
public class GraphQLApiBuilderParams {

  private final EntityGraphQLApiSpec apiSpec;
  private final List<FieldGraphQLApiSpec> fieldGraphQLApiSpecs;
  private final Map<CRUDEndpoints, Boolean> crudResolvers;
  private final ProcessingEnvironment processingEnv;
  private final Map<String, TypeElement> entitiesMap;
  private final ApifiClientFactory clientFactory;
  private final String entityName;
  private final Set<String> enumTypes;
  private final Set<String> allEntityTypesSimpleNames;

  @Setter
  private Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap;

  public GraphQLApiBuilderParams(
    EntityGraphQLApiSpec apiSpec,
    Map<String, TypeElement> entitiesMap,
    List<CRUDEndpoints> crudResolvers,
    Set<TypeElement> enumTypes,
    ProcessingEnvironment processingEnv,
    ApifiClientFactory clientFactory,
    Map<String, ClassName> collectionsTypes
  ) {
    this.enumTypes = parseEnumTypes(enumTypes);
    this.allEntityTypesSimpleNames = new HashSet<>(this.enumTypes);
    this.allEntityTypesSimpleNames.addAll(
        parseAllEntityTypesSimpleNames(entitiesMap)
      );
    this.apiSpec = apiSpec;
    this.entityName = apiSpec.getSimpleName();
    this.crudResolvers =
      crudResolvers.stream().collect(Collectors.toMap(cr -> cr, cr -> true));
    this.entitiesMap = entitiesMap;
    this.fieldGraphQLApiSpecs = apiSpec.getFieldGraphQLApiSpecs();
    this.clientFactory = clientFactory;
    this.processingEnv = processingEnv;
    registerCollectionsTypes(collectionsTypes);
    SecurityAnnotationsFactory.setProcessingEnv(processingEnv);
  }

  private Set<String> parseAllEntityTypesSimpleNames(
    Map<String, TypeElement> entitiesMap
  ) {
    return entitiesMap
      .values()
      .stream()
      .map(TypeElement::getSimpleName)
      .map(Objects::toString)
      .map(String::toLowerCase)
      .collect(Collectors.toSet());
  }

  private Set<String> parseEnumTypes(Set<TypeElement> enumTypes) {
    return enumTypes
      .stream()
      .map(TypeElement::getSimpleName)
      .map(Objects::toString)
      .map(String::toLowerCase)
      .collect(Collectors.toSet());
  }

  private void registerCollectionsTypes(
    Map<String, ClassName> collectionsTypes
  ) {
    this.fieldGraphQLApiSpecs.forEach(fieldGraphQLApiSpec -> {
        val fieldElement = fieldGraphQLApiSpec.getElement();
        if (isIterable(fieldElement.asType(), processingEnv)) {
          String key =
            apiSpec.getSimpleName() + "." + fieldGraphQLApiSpec.getSimpleName();
          ClassName value = ClassName.bestGuess(
            fieldElement
              .asType()
              .toString()
              .replaceAll(".+<", "")
              .replaceAll(">", "")
          );
          collectionsTypes.put(key, value);
        }
      });
  }
}
