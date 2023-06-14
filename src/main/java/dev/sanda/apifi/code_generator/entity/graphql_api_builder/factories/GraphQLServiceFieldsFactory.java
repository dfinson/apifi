package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.apifi.service.api_logic.ApiLogic;
import dev.sanda.apifi.service.api_logic.SubscriptionsLogicService;
import dev.sanda.datafi.service.DataManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;

public class GraphQLServiceFieldsFactory {

  private final EntityGraphQLApiSpec apiSpec;
  private final List<FieldGraphQLApiSpec> fieldGraphQLApiSpecs;
  private final ProcessingEnvironment processingEnv;
  private final Map<String, TypeElement> entitiesMap;

  public GraphQLServiceFieldsFactory(GraphQLApiBuilderParams params) {
    this.apiSpec = params.getApiSpec();
    this.fieldGraphQLApiSpecs = params.getFieldGraphQLApiSpecs();
    this.processingEnv = params.getProcessingEnv();
    this.entitiesMap = params.getEntitiesMap();
  }

  public List<FieldSpec> generateGraphQLServiceFields() {
    val fields = new ArrayList<FieldSpec>();
    fields.add(apiLogic());
    fields.add(subscriptionsLogicService());
    fields.add(entityDataManager());
    fields.add(entityApiHooks());
    fields.addAll(foreignKeyApiLogicFields());
    fields.addAll(foreignKeySubscriptionLogicServices());
    return fields;
  }

  private List<FieldSpec> foreignKeyApiLogicFields() {
    return fieldGraphQLApiSpecs
      .stream()
      .map(FieldGraphQLApiSpec::getElement)
      .filter(field -> entitiesMap.containsKey(getTypeNameKey(field)))
      .map(field ->
        dataManager(
          entitiesMap.get(getTypeNameKey(field)),
          dataManagerName(field)
        )
      )
      .collect(Collectors.toList());
  }

  private String getTypeNameKey(VariableElement field) {
    return isIterable(field.asType(), processingEnv)
      ? getCollectionType(field)
      : field.asType().toString();
  }

  private FieldSpec apiLogic() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(ApiLogic.class),
          ClassName.get(apiSpec.getElement())
        ),
        "apiLogic"
      )
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private FieldSpec subscriptionsLogicService() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(SubscriptionsLogicService.class),
          ClassName.get(apiSpec.getElement())
        ),
        "subscriptionsLogicService"
      )
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private FieldSpec entityDataManager() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(DataManager.class),
          ClassName.get(apiSpec.getElement())
        ),
        "dataManager"
      )
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private FieldSpec entityApiHooks() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(ApiHooks.class),
          ClassName.get(apiSpec.getElement())
        ),
        "apiHooks"
      )
      .addAnnotation(
        AnnotationSpec
          .builder(Setter.class)
          .addMember("onMethod_", "@$T(required = false)", Autowired.class)
          .build()
      )
      .addModifiers(PRIVATE)
      .build();
  }

  private FieldSpec dataManager(TypeElement entity, String namePrefix) {
    String suffix = namePrefix.endsWith("DataManager") ? "" : "DataManager";
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(DataManager.class),
          ClassName.get(entity)
        ),
        namePrefix + suffix
      )
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private List<FieldSpec> foreignKeySubscriptionLogicServices() {
    return fieldGraphQLApiSpecs
      .stream()
      .map(FieldGraphQLApiSpec::getElement)
      .filter(field -> entitiesMap.containsKey(getTypeNameKey(field)))
      .map(field ->
        createSubscriptionLogicService(
          entitiesMap.get(getTypeNameKey(field)),
          field.getSimpleName()
        )
      )
      .collect(Collectors.toList());
  }

  private FieldSpec createSubscriptionLogicService(
    TypeElement type,
    Name fieldName
  ) {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(SubscriptionsLogicService.class),
          ClassName.get(type)
        ),
        fieldName + SubscriptionsLogicService.class.getSimpleName(),
        PRIVATE,
        FINAL
      )
      .build();
  }
}
