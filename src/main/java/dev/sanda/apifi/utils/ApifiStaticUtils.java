package dev.sanda.apifi.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.ElementCollectionApi;
import dev.sanda.apifi.annotations.EntityCollectionApi;
import dev.sanda.apifi.annotations.MapElementCollectionApi;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.service.api_hooks.*;
import dev.sanda.datafi.annotations.EntityApiSpec;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.atteo.evo.inflector.English;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.FluxSink;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.sanda.datafi.DatafiStaticUtils.*;

public abstract class ApifiStaticUtils {

  public static List<VariableElement> getFields(TypeElement typeElement) {
    return typeElement
      .getEnclosedElements()
      .stream()
      .filter(element -> element.getKind().isField())
      .map(element -> (VariableElement) element)
      .collect(Collectors.toList());
  }

  public static ObjectMapper nonTransactionalObjectMapper() {
    val mapper = new ObjectMapper();
    mapper.disable(
      MapperFeature.AUTO_DETECT_CREATORS,
      MapperFeature.AUTO_DETECT_GETTERS,
      MapperFeature.AUTO_DETECT_IS_GETTERS
    );
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    return mapper;
  }

  public static List<VariableElement> getNonIgnoredFields(
    TypeElement typeElement
  ) {
    return getFields(typeElement)
      .stream()
      .filter(field -> field.getAnnotation(GraphQLIgnore.class) == null)
      .collect(Collectors.toList());
  }

  public static final String NEW_LINE = System.lineSeparator();

  public static LinkedHashMap<String, TypeMirror> getGraphQLFields(
    TypeElement typeElement
  ) {
    val graphQlIgnoredFields = new HashSet<String>();
    val methodQueryFields = typeElement
      .getEnclosedElements()
      .stream()
      .filter(element -> element instanceof ExecutableElement)
      .map(element -> (ExecutableElement) element)
      .filter(element ->
        element.getSimpleName().toString().startsWith("get") ||
        element.getAnnotation(GraphQLQuery.class) != null
      )
      .filter(executableElement ->
        !executableElement
          .getReturnType()
          .toString()
          .equals(void.class.getCanonicalName())
      )
      .filter(executableElement -> {
        final boolean isIgnored =
          executableElement.getAnnotation(GraphQLIgnore.class) != null;
        if (isIgnored) graphQlIgnoredFields.add(
          getFieldName(executableElement)
        );
        return !isIgnored;
      })
      .collect(
        Collectors.toMap(
          ApifiStaticUtils::getFieldName,
          ExecutableElement::getReturnType,
          (first, second) -> first,
          LinkedHashMap::new
        )
      );
    val fromFields = getNonIgnoredFields(typeElement)
      .stream()
      .filter(field ->
        !graphQlIgnoredFields.contains(field.getSimpleName().toString())
      )
      .collect(
        Collectors.toMap(
          field -> field.getSimpleName().toString(),
          VariableElement::asType
        )
      );
    fromFields.forEach(methodQueryFields::putIfAbsent);
    return methodQueryFields;
  }

  private static String getFieldName(Element element) {
    final String name = element.getSimpleName().toString();
    final GraphQLQuery queryAnnotation = element.getAnnotation(
      GraphQLQuery.class
    );
    if (
      queryAnnotation != null && !queryAnnotation.name().equals("")
    ) return queryAnnotation.name(); else return name.startsWith("get")
      ? toCamelCase(name.replaceFirst("get", ""))
      : name;
  }

  public static List<EntityGraphQLApiSpec> getGraphQLApiSpecs(
    RoundEnvironment roundEnvironment,
    ProcessingEnvironment processingEnv
  ) {
    Set<TypeElement> entities = new HashSet<>();
    entities.addAll(
      (Collection<? extends TypeElement>) roundEnvironment.getElementsAnnotatedWith(
        Entity.class
      )
    );
    entities.addAll(
      (Collection<? extends TypeElement>) roundEnvironment.getElementsAnnotatedWith(
        Table.class
      )
    );
    Map<TypeElement, TypeElement> extensionsMap =
      (
        (Set<TypeElement>) roundEnvironment.getElementsAnnotatedWith(
          EntityApiSpec.class
        )
      ).stream()
        .collect(
          Collectors.toMap(
            Function.identity(),
            typeElement ->
              (TypeElement) processingEnv
                .getTypeUtils()
                .asElement(typeElement.getSuperclass())
          )
        )
        .entrySet()
        .stream()
        .filter(entry ->
          entry.getValue().getAnnotation(Table.class) != null ||
          entry.getValue().getAnnotation(Entity.class) != null
        )
        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    entities.forEach(entity -> extensionsMap.putIfAbsent(entity, null));
    Map<TypeElement, TypeElement> temp = new HashMap<>();
    for (Map.Entry<TypeElement, TypeElement> typeElementTypeElementEntry : extensionsMap.entrySet()) {
      extractReferencedEntities(
        typeElementTypeElementEntry.getKey(),
        processingEnv
      )
        .forEach(entityReference -> temp.putIfAbsent(entityReference, null));
    }
    temp.forEach(extensionsMap::putIfAbsent);
    return extensionsMap
      .entrySet()
      .stream()
      .map(entry -> new EntityGraphQLApiSpec(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());
  }

  public static void argsToResolver(
    String resolverParams,
    MethodSpec.Builder builder
  ) {
    TypeName listOfObjects = ParameterizedTypeName.get(
      List.class,
      Object.class
    );
    CodeBlock.Builder block = CodeBlock
      .builder()
      .add(
        "$T args = $T.asList($L)",
        listOfObjects,
        Arrays.class,
        resolverParams
      );
    builder.addStatement(block.build());
  }

  public static Set<? extends TypeElement> getGraphQLApiEntities(
    RoundEnvironment roundEnvironment
  ) {
    return getEntitiesSet(roundEnvironment);
  }

  public static TypeName listOf(TypeElement element) {
    return ParameterizedTypeName.get(
      ClassName.get(List.class),
      ClassName.get(element)
    );
  }

  public static TypeName listOf(VariableElement element) {
    return ParameterizedTypeName.get(
      ClassName.get(List.class),
      ClassName.get(element.asType())
    );
  }

  public static TypeName listOf(ClassName className) {
    return ParameterizedTypeName.get(ClassName.get(List.class), className);
  }

  public static TypeName listOf(TypeName typeName) {
    return ParameterizedTypeName.get(ClassName.get(List.class), typeName);
  }

  public static TypeName mapOf(VariableElement map) {
    val mapType = ClassName.get(Map.class);
    val keyType = ClassName.bestGuess(getMapKeyType(map));
    val valueType = ClassName.bestGuess(getMapValueType(map));
    return ParameterizedTypeName.get(mapType, keyType, valueType);
  }

  public static String dataManagerName(Element element) {
    return camelcaseNameOf(element) + "DataManager";
  }

  public static TypeName collectionTypeName(
    VariableElement entityCollectionField
  ) {
    String typeNameString = entityCollectionField.asType().toString();
    typeNameString = typeNameString.replaceAll("^.+<", "");
    typeNameString = typeNameString.replaceAll(">", "");
    int lastDot = typeNameString.lastIndexOf('.');
    String packageName = typeNameString.substring(0, lastDot);
    String simpleClassName = typeNameString.substring(lastDot + 1);
    return ClassName.get(packageName, simpleClassName);
  }

  /*public static String collectionType(VariableElement entityCollectionField) {
        String typeNameString = embedded.asType().toString();
        typeNameString = typeNameString.replaceAll("^.+<", "");
        typeNameString = typeNameString.replaceAll(">", "");
        int lastDot = typeNameString.lastIndexOf('.');
        return typeNameString.substring(lastDot + 1);
    }*/

  public static ParameterizedTypeName pageType(TypeElement entity) {
    return ParameterizedTypeName.get(
      ClassName.get(Page.class),
      ClassName.get(entity)
    );
  }

  public static ParameterizedTypeName pageType(VariableElement collection) {
    val collectionType = getCollectionType(collection);
    return ParameterizedTypeName.get(
      ClassName.get(Page.class),
      ClassName.bestGuess(collectionType)
    );
  }

  public static ParameterizedTypeName mapPageType(VariableElement map) {
    val collectionType = getMapValueType(map);
    return ParameterizedTypeName.get(
      ClassName.get(Page.class),
      ClassName.bestGuess(collectionType)
    );
  }

  public static ParameterizedTypeName mapEntryListPageType(
    VariableElement map
  ) {
    val mapValueType = ClassName.bestGuess(getMapValueType(map));
    val pageType = ClassName.get(Page.class);
    val mapKeyType = ClassName.bestGuess(getMapKeyType(map));
    val rawEntryType = ClassName.get(Map.Entry.class);
    val entryType = ParameterizedTypeName.get(
      rawEntryType,
      mapKeyType,
      mapValueType
    );
    return ParameterizedTypeName.get(pageType, entryType);
  }

  public static <T> Predicate<T> distinctByKey(
    Function<? super T, ?> keyExtractor
  ) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  public static String inBrackets(String str) {
    return String.format("[%s]", str);
  }

  public static String inQuotes(String str) {
    return '\"' + str + '\"';
  }

  public static String apiHooksName(TypeElement entity) {
    return camelcaseNameOf(entity) + ApiHooks.class.getSimpleName();
  }

  public static String camelcaseNameOf(Element entity) {
    return toCamelCase(entity.getSimpleName().toString());
  }

  public static String pascalCaseNameOf(Element entity) {
    return toPascalCase(entity.getSimpleName().toString());
  }

  public static ParameterSpec parameterizeType(TypeElement entity) {
    return ParameterSpec
      .builder(TypeName.get(entity.asType()), "input")
      .build();
  }

  public static final String REFLECTION_CACHE = "reflectionCache";

  public static CodeBlock entitiesList(TypeElement entity) {
    ClassName listClassName = ClassName.get("java.util", "List");
    ClassName typeClassName = ClassName.get(entity);
    TypeName listOfEntities = ParameterizedTypeName.get(
      listClassName,
      typeClassName
    );
    return CodeBlock.builder().add("$T entities", listOfEntities).build();
  }

  @SafeVarargs
  public static <A extends Annotation> ParameterSpec asParamList(
    Element element,
    Class<A>... annotations
  ) {
    ClassName list = ClassName.get("java.util", "List");
    TypeName typeClassName = TypeName.get(element.asType());
    ParameterSpec.Builder builder = ParameterSpec.builder(
      ParameterizedTypeName.get(list, typeClassName),
      "input"
    );
    if (annotations.length > 0) {
      for (Class<A> annotation : annotations) {
        builder.addAnnotation(annotation);
      }
    }
    return builder.build();
  }

  @SafeVarargs
  public static <A extends Annotation> ParameterSpec asParamMapKeyList(
    VariableElement element,
    Class<A>... annotations
  ) {
    val listType = ClassName.get(List.class);
    val keyType = ClassName.bestGuess(getMapKeyType(element));
    ParameterSpec.Builder builder = ParameterSpec.builder(
      ParameterizedTypeName.get(listType, keyType),
      "input"
    );
    if (annotations.length > 0) {
      for (Class<A> annotation : annotations) {
        builder.addAnnotation(annotation);
      }
    }
    return builder.build();
  }

  @SafeVarargs
  public static <A extends Annotation> ParameterSpec asParamList(
    TypeName typeName,
    Class<A>... annotations
  ) {
    ClassName list = ClassName.get("java.util", "List");
    ParameterSpec.Builder builder = ParameterSpec.builder(
      ParameterizedTypeName.get(list, typeName),
      "input"
    );
    if (annotations.length > 0) {
      for (Class<A> annotation : annotations) {
        builder.addAnnotation(annotation);
      }
    }
    return builder.build();
  }

  @SafeVarargs
  public static <A extends Annotation> ParameterSpec keySetAsParamList(
    VariableElement map,
    Class<A>... annotations
  ) {
    ClassName list = ClassName.get("java.util", "List");
    val mapKeyType = ClassName.bestGuess(getMapKeyType(map)); //TODO - validate
    ParameterSpec.Builder builder = ParameterSpec.builder(
      ParameterizedTypeName.get(list, mapKeyType),
      "input"
    );
    if (annotations.length > 0) {
      for (Class<A> annotation : annotations) {
        builder.addAnnotation(annotation);
      }
    }
    return builder.build();
  }

  @SafeVarargs
  public static <A extends Annotation> ParameterSpec asParamMap(
    VariableElement map,
    Class<A>... annotations
  ) {
    val mapType = ClassName.get(Map.class);
    val mapKeyType = ClassName.bestGuess(getMapKeyType(map));
    val mapValueType = ClassName.bestGuess(getMapValueType(map));
    ParameterSpec.Builder builder = ParameterSpec.builder(
      ParameterizedTypeName.get(mapType, mapKeyType, mapValueType),
      "input"
    );
    if (
      annotations.length > 0
    ) for (Class<A> annotation : annotations) builder.addAnnotation(annotation);
    return builder.build();
  }

  public static String getMapValueType(VariableElement map) {
    return map
      .asType()
      .toString()
      .replaceAll("^.+<", "")
      .replaceAll(">", "")
      .replaceAll(".+,", "");
  }

  public static boolean isAssignableFrom(
    ProcessingEnvironment processingEnv,
    TypeElement typeElement,
    String targetTypeName
  ) {
    Elements elementUtil;
    TypeMirror targetType = getTargetType(processingEnv, targetTypeName);
    return processingEnv
      .getTypeUtils()
      .isAssignable(typeElement.asType(), targetType);
  }

  public static TypeMirror getTargetType(
    ProcessingEnvironment processingEnv,
    String targetTypeName
  ) {
    return processingEnv
      .getElementUtils()
      .getTypeElement(targetTypeName)
      .asType();
  }

  public static ParameterSpec asEntityCollectionParamList(
    VariableElement entityCollectionField
  ) {
    ClassName list = ClassName.get("java.util", "List");
    TypeName typeClassName = collectionTypeName(entityCollectionField);
    return ParameterSpec
      .builder(ParameterizedTypeName.get(list, typeClassName), "input")
      .build();
  }

  public static AnnotationSpec suppressDeprecationWarning() {
    return AnnotationSpec
      .builder(SuppressWarnings.class)
      .addMember("value", "$S", "deprecation")
      .build();
  }

  public static ParameterizedTypeName listOfLists(VariableElement element) {
    ParameterizedTypeName nestedList = ParameterizedTypeName.get(
      ClassName.get(List.class),
      collectionTypeName(element)
    );
    ClassName listName = ClassName.get("java.util", "List");
    return ParameterizedTypeName.get(
      listName,
      nestedList
    );
  }

  public static ParameterizedTypeName listOfEmbedded(VariableElement element) {
    return ParameterizedTypeName.get(
      ClassName.get(List.class),
      collectionTypeName(element)
    );
  }

  public static String entityCollectionApiHooksName(
    VariableElement entityCollectionField
  ) {
    val config = entityCollectionField.getAnnotation(EntityCollectionApi.class);
    if (config == null) return "null";
    val apiHooks = getApiHooksTypeName(config);
    return !apiHooks
        .toString()
        .equals(NullEntityCollectionApiHooks.class.getCanonicalName())
      ? camelcaseNameOf(entityCollectionField) +
      EntityCollectionApiHooks.class.getSimpleName()
      : "null";
  }

  public static AnnotationSpec autowiredRequiredArgsConstructor() {
    return AnnotationSpec
      .builder(RequiredArgsConstructor.class)
      .addMember("onConstructor_", "@$T", Autowired.class)
      .build();
  }

  public static String elementCollectionApiHooksName(
    VariableElement entityCollectionField
  ) {
    val config = entityCollectionField.getAnnotation(
      ElementCollectionApi.class
    );
    if (config == null) return "null";
    val apiHooks = getApiHooksTypeName(config);
    return !apiHooks
        .toString()
        .equals(NullElementCollectionApiHooks.class.getCanonicalName())
      ? camelcaseNameOf(entityCollectionField) +
      ElementCollectionApiHooks.class.getSimpleName()
      : "null";
  }

  public static String mapElementCollectionApiHooksName(
    VariableElement entityCollectionField
  ) {
    val config = entityCollectionField.getAnnotation(
      MapElementCollectionApi.class
    );
    if (config == null) return "null";
    val apiHooks = getApiHooksTypeName(config);
    return !apiHooks
        .toString()
        .equals(NullElementCollectionApiHooks.class.getCanonicalName())
      ? camelcaseNameOf(entityCollectionField) +
      ElementCollectionApiHooks.class.getSimpleName()
      : "null";
  }

  public static TypeName getApiHooksTypeName(
    EntityCollectionApi entityCollectionApi
  ) {
    TypeName apiHooksType = null;
    try {
      entityCollectionApi.apiHooks();
    } catch (MirroredTypeException mte) {
      apiHooksType = TypeName.get(mte.getTypeMirror());
    }
    return apiHooksType;
  }

  public static TypeName getApiHooksTypeName(
    ElementCollectionApi entityCollectionApi
  ) {
    TypeName apiHooksType = null;
    try {
      entityCollectionApi.apiHooks();
    } catch (MirroredTypeException mte) {
      apiHooksType = TypeName.get(mte.getTypeMirror());
    }
    return apiHooksType;
  }

  public static TypeName getApiHooksTypeName(
    MapElementCollectionApi entityCollectionApi
  ) {
    TypeName apiHooksType = null;
    try {
      entityCollectionApi.apiHooks();
    } catch (MirroredTypeException mte) {
      apiHooksType = TypeName.get(mte.getTypeMirror());
    }
    return apiHooksType;
  }

  public static boolean isTypeElementOfType(
    Element element,
    String typeCanonicalName,
    ProcessingEnvironment processingEnv
  ) {
    return processingEnv
      .getTypeUtils()
      .isAssignable(
        element.asType(),
        processingEnv
          .getElementUtils()
          .getTypeElement(typeCanonicalName)
          .asType()
      );
  }

  public static boolean isClazzArchivable(
    Class<?> clazz,
    ReflectionCache reflectionCache
  ) {
    return reflectionCache
      .getEntitiesCache()
      .get(clazz.getSimpleName())
      .isArchivable();
  }

  public static String getCollectionType(VariableElement element) {
    return element
      .asType()
      .toString()
      .replaceAll("^.+<", "")
      .replaceAll(">", "");
  }

  public static String getCollectionTypeSimpleName(VariableElement element) {
    val fullName = element
      .asType()
      .toString()
      .replaceAll("^.+<", "")
      .replaceAll(">", "");
    val lastDot = fullName.lastIndexOf(".");
    return fullName.substring(lastDot + 1);
  }

  public static String getTypeScriptElementCollectionType(
    VariableElement element,
    Set<String> enumTypes
  ) {
    val type = toSimpleName(getCollectionType(element));
    return resolveTypescriptType(type, enumTypes);
  }

  public static String resolveTypescriptType(
    String type,
    Set<String> entityTypes
  ) {
    if (numberTypes.contains(type.toLowerCase())) return "number"; else if (
      type.equalsIgnoreCase("string") || type.equalsIgnoreCase("boolean")
    ) return type.toLowerCase(); else if (
      entityTypes.contains(type.toLowerCase())
    ) return type; else if (
      type.equals("Date") || type.equals("DateTime")
    ) return "Date"; else return "any";
  }

  public static final Set<String> numberTypes = new HashSet<>(
    Arrays.asList("byte", "short", "int", "integer", "long", "float", "double")
  );

  public static String getMapKeyType(VariableElement element) {
    return element
      .asType()
      .toString()
      .replaceAll("^.+<", "")
      .replaceAll(",.+", "");
  }

  public static String toSimpleName(String name) {
    return name.substring(name.lastIndexOf(".") + 1);
  }

  public static boolean isIterable(
    TypeMirror typeMirror,
    ProcessingEnvironment processingEnv
  ) {
    TypeMirror iterableType = processingEnv
      .getTypeUtils()
      .erasure(
        processingEnv
          .getElementUtils()
          .getTypeElement("java.lang.Iterable")
          .asType()
      );
    return processingEnv.getTypeUtils().isAssignable(typeMirror, iterableType);
  }

  public static boolean isMap(
    TypeMirror typeMirror,
    ProcessingEnvironment processingEnv
  ) {
    TypeMirror iterableType = processingEnv
      .getTypeUtils()
      .erasure(
        processingEnv.getElementUtils().getTypeElement("java.util.Map").asType()
      );
    return processingEnv.getTypeUtils().isAssignable(typeMirror, iterableType);
  }

  public static boolean isSet(
    TypeMirror typeMirror,
    ProcessingEnvironment processingEnv
  ) {
    TypeMirror iterableType = processingEnv
      .getTypeUtils()
      .erasure(
        processingEnv.getElementUtils().getTypeElement("java.util.Set").asType()
      );
    return processingEnv.getTypeUtils().isAssignable(typeMirror, iterableType);
  }

  public static boolean isFromType(
    TypeName requestType,
    TypeName expectedType
  ) {
    if (requestType instanceof ParameterizedTypeName) {
      TypeName typeName = ((ParameterizedTypeName) requestType).rawType;
      return (typeName.equals(expectedType));
    }
    return false;
  }

  public static ParameterSpec graphQLParameter(
    TypeName typeName,
    String name,
    String defaultValue
  ) {
    AnnotationSpec.Builder annotation = AnnotationSpec
      .builder(GraphQLArgument.class)
      .addMember("name", "$S", name);
    if (defaultValue != null) annotation.addMember(
      "defaultValue",
      "$S",
      defaultValue
    );
    return ParameterSpec
      .builder(typeName, name)
      .addAnnotation(annotation.build())
      .build();
  }

  public static boolean isForeignKeyOrKeys(VariableElement field) {
    return (
      field.getAnnotation(OneToMany.class) != null ||
      field.getAnnotation(ManyToOne.class) != null ||
      field.getAnnotation(OneToOne.class) != null ||
      field.getAnnotation(ManyToMany.class) != null
    );
  }

  public static AnnotationSpec graphqlQueryAnnotation() {
    return AnnotationSpec.builder(GraphQLQuery.class).build();
  }

  public static AnnotationSpec graphqlMutationAnnotation() {
    return AnnotationSpec.builder(GraphQLMutation.class).build();
  }

  public static String collectionTypeSimpleName(TypeName collectionTypeName) {
    final String name = collectionTypeName.toString();
    return name.substring(name.lastIndexOf(".") + 1);
  }

  public static CodeBlock initSortByIfNull(TypeElement entityType) {
    return CodeBlock
            .builder()
            .beginControlFlow("if(input.getSortBy() == null)")
            .addStatement("input.setSortBy($S)", getIdFieldName(entityType))
            .endControlFlow()
            .build();
  }

  public static ParameterSpec subscriptionBackPressureStrategyParam() {
    return ParameterSpec
            .builder(FluxSink.OverflowStrategy.class, "backPressureStrategy")
            .addAnnotation(
                    AnnotationSpec
                            .builder(GraphQLArgument.class)
                            .addMember("name", "$S", "backPressureStrategy")
                            .addMember(
                                    "defaultValue",
                                    "$S",
                                    "\"" + FluxSink.OverflowStrategy.BUFFER + "\""
                            )
                            .build()
            )
            .build();
  }

  public static String getIdFieldName(TypeElement entity) {
    //noinspection OptionalGetWithoutIsPresent
    return entity
      .getEnclosedElements()
      .stream()
      .filter(elem -> elem.getKind().isField())
      .filter(field ->
        field.getAnnotation(Id.class) != null ||
        field.getAnnotation(EmbeddedId.class) != null
      )
      .findFirst()
      .get()
      .getSimpleName()
      .toString();
  }

  public static String collectionCanonicalTypeNameString(
    VariableElement entityCollectionField
  ) {
    return entityCollectionField
      .asType()
      .toString()
      .replaceAll("^.+<", "")
      .replaceAll(">", "");
  }

  public static Properties loadPropertiesFromFile(
    String resourcePath,
    ProcessingEnvironment env
  ) {
    try {
      val configFileInputStream = env
        .getFiler()
        .getResource(StandardLocation.CLASS_OUTPUT, "", resourcePath)
        .openInputStream();
      val prop = new Properties();
      prop.load(configFileInputStream);
      return prop;
    } catch (IOException ex) {
      ex.printStackTrace();
    }
    return null;
  }

  public String pluralCamelCaseName(String s) {
    return toCamelCase(English.plural(s));
  }

  public String pluralPascalCaseName(String s) {
    return toPascalCase(English.plural(s));
  }

  public static String toSimpleCamelcaseName(String service) {
    int lastDot = service.lastIndexOf(".") + 1;
    return toCamelCase(service.substring(lastDot));
  }

  public static String toSimplePascalCaseName(String service) {
    int lastDot = service.lastIndexOf(".") + 1;
    return toPascalCase(service.substring(lastDot));
  }

  public static ScheduledExecutorService generateOptimalScheduledExecutorService() {
    return Executors.newScheduledThreadPool(
      Runtime.getRuntime().availableProcessors()
    );
  }

  public static <T> ArrayList<T> tryReloadCollection(
    Collection<T> collection,
    DataManager<T> dataManager,
    ReflectionCache reflectionCache
  ) {
    try {
      return new ArrayList<>(
        dataManager.findAllById(getIdList(collection, reflectionCache))
      );
    } catch (Exception e) {
      return new ArrayList<>(collection);
    }
  }
}
