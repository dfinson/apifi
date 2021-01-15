package dev.sanda.apifi.utils;

import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.ElementCollectionApi;
import dev.sanda.apifi.annotations.EntityCollectionApi;
import dev.sanda.apifi.annotations.MapElementCollectionApi;
import dev.sanda.apifi.service.ElementCollectionApiHooks;
import dev.sanda.apifi.service.EntityCollectionApiHooks;
import dev.sanda.apifi.service.NullElementCollectionApiHooks;
import dev.sanda.apifi.service.NullEntityCollectionApiHooks;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.val;
import lombok.var;
import org.atteo.evo.inflector.English;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.persistence.*;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.sanda.datafi.DatafiStaticUtils.*;

public abstract class ApifiStaticUtils {
    public static List<VariableElement> getFields(TypeElement typeElement){
        List<VariableElement> fields = new ArrayList<>();
        for(Element element : typeElement.getEnclosedElements()){
            if(element.getKind().isField()){
                fields.add((VariableElement) element);
            }
        }
        return fields;
    }

    public static List<VariableElement> getNonIgnoredFields(TypeElement typeElement){
        return getFields(typeElement)
                .stream()
                .filter(field -> field.getAnnotation(GraphQLIgnore.class) == null)
                .collect(Collectors.toList());
    }

    public static LinkedHashMap<String, TypeMirror> getGraphQLFields(TypeElement typeElement){
        val graphQlIgnoredFields = new HashSet<String>();
        val methodQueryFields = typeElement
                .getEnclosedElements()
                .stream()
                .filter(element -> element instanceof ExecutableElement)
                .map(element -> (ExecutableElement) element)
                .filter(element ->
                        element.getSimpleName().toString().startsWith("get") ||
                                element.getAnnotation(GraphQLQuery.class) != null)
                .filter(executableElement -> !executableElement.getReturnType().toString().equals(void.class.getCanonicalName()))
                .filter(executableElement -> {
                    final boolean isIgnored = executableElement.getAnnotation(GraphQLIgnore.class) != null;
                    if(isIgnored)
                        graphQlIgnoredFields.add(getFieldName(executableElement));
                    return !isIgnored;
                })
                .collect(Collectors.toMap(
                        ApifiStaticUtils::getFieldName,
                        ExecutableElement::getReturnType,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        val fromFields = getNonIgnoredFields(typeElement)
                .stream()
                .filter(field -> !graphQlIgnoredFields.contains(field.getSimpleName().toString()))
                .collect(Collectors.toMap(
                    field -> field.getSimpleName().toString(),
                    VariableElement::asType
        ));
        fromFields.forEach(methodQueryFields::putIfAbsent);
        return methodQueryFields;
    }

    private static String getFieldName(Element element) {
        final String name = element.getSimpleName().toString();
        final GraphQLQuery queryAnnotation = element.getAnnotation(GraphQLQuery.class);
        if (queryAnnotation != null && !queryAnnotation.name().equals(""))
            return queryAnnotation.name();
        else
            return name.startsWith("get") ? toCamelCase(name.replaceFirst("get", "")) : name;
    }

    public static<T> T randomFrom(List<T> aList) {
        return aList.get(ThreadLocalRandom.current().nextInt(aList.size()));
    }

    public static void argsToResolver(String resolverParams, MethodSpec.Builder builder) {
        TypeName listOfObjects = ParameterizedTypeName.get(List.class, Object.class);
        CodeBlock.Builder block = CodeBlock.builder()
                .add("$T args = $T.asList($L)", listOfObjects, Arrays.class, resolverParams);
        builder.addStatement(block.build());
    }

    public static Set<? extends TypeElement> getGraphQLApiEntities(RoundEnvironment roundEnvironment) {
        return getEntitiesSet(roundEnvironment);
    }

    public static TypeName listOf(TypeElement element) {
        return ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(element));
    }

    public static TypeName listOf(VariableElement element) {
        return ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(element.asType()));
    }

    public static TypeName listOf(ClassName className) {
        return ParameterizedTypeName.get(ClassName.get(List.class), className);
    }

    public static TypeName listOf(TypeName typeName) {
        return ParameterizedTypeName.get(ClassName.get(List.class), typeName);
    }

    public static TypeName mapOf(VariableElement map){
        val mapType = ClassName.get(Map.class);
        val keyType = ClassName.bestGuess(getMapKeyType(map));
        val valueType = ClassName.bestGuess(getMapValueType(map));
        return ParameterizedTypeName.get(mapType, keyType, valueType);
    }

    public static String dataManagerName(Element element) {
        return camelcaseNameOf(element) + "DataManager";
    }

    public static TypeName collectionTypeName(VariableElement entityCollectionField) {
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
                ClassName.get(entity));
    }

    public static ParameterizedTypeName pageType(VariableElement collection) {
        val collectionType = getCollectionType(collection);
        return ParameterizedTypeName.get(
                ClassName.get(Page.class),
                ClassName.bestGuess(collectionType));
    }

    public static ParameterizedTypeName mapPageType(VariableElement map){
        val collectionType = getMapValueType(map);
        return ParameterizedTypeName.get(
                ClassName.get(Page.class),
                ClassName.bestGuess(collectionType));
    }

    public static ParameterizedTypeName mapEntryListPageType(VariableElement map){
        val mapValueType = ClassName.bestGuess(getMapValueType(map));
        val pageType = ClassName.get(Page.class);
        val mapKeyType = ClassName.bestGuess(getMapKeyType(map));
        val rawEntryType = ClassName.get(Map.Entry.class);
        val entryType = ParameterizedTypeName.get(rawEntryType, mapKeyType, mapValueType);
        return ParameterizedTypeName.get(pageType, entryType);
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static String inBrackets(String str){
        return String.format("[%s]", str);
    }

    public static String inQuotes(String str){
        return '\"' + str + '\"';
    }

    public static String apiHooksName(TypeElement entity) {
        return camelcaseNameOf(entity) + "MetaOperations";
    }

    public static String camelcaseNameOf(Element entity) {
        return toCamelCase(entity.getSimpleName().toString());
    }

    public static String pascalCaseNameOf(Element entity) {
        return toPascalCase(entity.getSimpleName().toString());
    }

    public static ParameterSpec parameterizeType(TypeElement entity) {
        return ParameterSpec.builder(TypeName.get(entity.asType()), "input").build();
    }

    public static final String reflectionCache = "reflectionCache";

    public static CodeBlock entitiesList(TypeElement entity) {
        ClassName listClassName = ClassName.get("java.util", "List");
        ClassName typeClassName = ClassName.get(entity);
        TypeName listOfEntities = ParameterizedTypeName.get(listClassName, typeClassName);
        return CodeBlock.builder().add("$T entities", listOfEntities).build();
    }

    @SafeVarargs
    public static <A extends Annotation> ParameterSpec asParamList(Element element, Class<A>... annotations) {
        ClassName list = ClassName.get("java.util", "List");
        TypeName typeClassName = TypeName.get(element.asType());
        var builder = ParameterSpec.builder(ParameterizedTypeName.get(list, typeClassName), "input");
        if (annotations.length > 0) {
            for (Class<A> annotation : annotations) {
                builder.addAnnotation(annotation);
            }
        }
        return builder.build();
    }

    @SafeVarargs
    public static <A extends Annotation> ParameterSpec asParamMapKeyList(VariableElement element, Class<A>... annotations) {
        val listType = ClassName.get(List.class);
        val keyType = ClassName.bestGuess(getMapKeyType(element));
        var builder = ParameterSpec.builder(ParameterizedTypeName.get(listType, keyType), "input");
        if (annotations.length > 0) {
            for (Class<A> annotation : annotations) {
                builder.addAnnotation(annotation);
            }
        }
        return builder.build();
    }


    @SafeVarargs
    public static <A extends Annotation> ParameterSpec asParamList(TypeName typeName, Class<A>... annotations) {
        ClassName list = ClassName.get("java.util", "List");
        var builder = ParameterSpec.builder(ParameterizedTypeName.get(list, typeName), "input");
        if (annotations.length > 0) {
            for (Class<A> annotation : annotations) {
                builder.addAnnotation(annotation);
            }
        }
        return builder.build();
    }

    @SafeVarargs
    public static <A extends Annotation> ParameterSpec keySetAsParamList(VariableElement map, Class<A>... annotations) {
        ClassName list = ClassName.get("java.util", "List");
        val mapKeyType = ClassName.bestGuess(getMapKeyType(map));//TODO - validate
        var builder = ParameterSpec.builder(ParameterizedTypeName.get(list, mapKeyType), "input");
        if (annotations.length > 0) {
            for (Class<A> annotation : annotations) {
                builder.addAnnotation(annotation);
            }
        }
        return builder.build();
    }

    @SafeVarargs
    public static <A extends Annotation> ParameterSpec asParamMap(VariableElement map, Class<A>... annotations) {
        val mapType = ClassName.get(Map.class);
        val mapKeyType = ClassName.bestGuess(getMapKeyType(map));
        val mapValueType = ClassName.bestGuess(getMapValueType(map));
        var builder = ParameterSpec.builder(ParameterizedTypeName.get(mapType, mapKeyType, mapValueType), "input");
        if (annotations.length > 0)
            for (Class<A> annotation : annotations)
                builder.addAnnotation(annotation);
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

    public static boolean isAssignableFrom(ProcessingEnvironment processingEnv, TypeElement typeElement, String targetTypeName){ Elements elementUtil;
        TypeMirror targetType = getTargetType(processingEnv, targetTypeName);
        return processingEnv.getTypeUtils().isAssignable(typeElement.asType(), targetType);
    }

    public static TypeMirror getTargetType(ProcessingEnvironment processingEnv, String targetTypeName){
        return processingEnv.getElementUtils().getTypeElement(targetTypeName).asType();
    }

    public static ParameterSpec asEntityCollectionParamList(VariableElement entityCollectionField){
        ClassName list = ClassName.get("java.util", "List");
        TypeName typeClassName = collectionTypeName(entityCollectionField);
        return ParameterSpec.builder(ParameterizedTypeName.get(list, typeClassName), "input").build();
    }

    public static AnnotationSpec suppressDeprecationWarning() {
        return AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "deprecation")
                .build();
    }

    public static ParameterizedTypeName listOfLists(VariableElement element) {

        ParameterizedTypeName nestedList =
                ParameterizedTypeName.get(ClassName.get(List.class), collectionTypeName(element));
        ClassName listName = ClassName.get("java.util", "List");
        return ParameterizedTypeName.get(listName, nestedList);
    }

    public static ParameterizedTypeName listOfEmbedded(VariableElement element){
        return ParameterizedTypeName.get(ClassName.get(List.class), collectionTypeName(element));
    }

    public static String entityCollectionApiHooksName(VariableElement entityCollectionField) {
        val config = entityCollectionField.getAnnotation(EntityCollectionApi.class);
        if(config == null) return "null";
        val apiHooks = getApiHooksTypeName(config);
        return !apiHooks.toString().equals(NullEntityCollectionApiHooks.class.getCanonicalName()) ? camelcaseNameOf(entityCollectionField) + EntityCollectionApiHooks.class.getSimpleName() : "null";
    }

    public static String elementCollectionApiHooksName(VariableElement entityCollectionField) {
        val config = entityCollectionField.getAnnotation(ElementCollectionApi.class);
        if(config == null) return "null";
        val apiHooks = getApiHooksTypeName(config);
        return !apiHooks.toString().equals(NullElementCollectionApiHooks.class.getCanonicalName()) ? camelcaseNameOf(entityCollectionField) + ElementCollectionApiHooks.class.getSimpleName() : "null";
    }

    public static String mapElementCollectionApiHooksName(VariableElement entityCollectionField) {
        val config = entityCollectionField.getAnnotation(MapElementCollectionApi.class);
        if(config == null) return "null";
        val apiHooks = getApiHooksTypeName(config);
        return !apiHooks.toString().equals(NullElementCollectionApiHooks.class.getCanonicalName()) ? camelcaseNameOf(entityCollectionField) + ElementCollectionApiHooks.class.getSimpleName() : "null";
    }

    public static TypeName getApiHooksTypeName(EntityCollectionApi entityCollectionApi) {
        TypeName apiHooksType = null;
        try {
            entityCollectionApi.apiHooks();
        } catch (MirroredTypeException mte) {
            apiHooksType = TypeName.get(mte.getTypeMirror());
        }
        return apiHooksType;
    }

    public static TypeName getApiHooksTypeName(ElementCollectionApi entityCollectionApi) {
        TypeName apiHooksType = null;
        try {
            entityCollectionApi.apiHooks();
        } catch (MirroredTypeException mte) {
            apiHooksType = TypeName.get(mte.getTypeMirror());
        }
        return apiHooksType;
    }

    public static TypeName getApiHooksTypeName(MapElementCollectionApi entityCollectionApi) {
        TypeName apiHooksType = null;
        try {
            entityCollectionApi.apiHooks();
        } catch (MirroredTypeException mte) {
            apiHooksType = TypeName.get(mte.getTypeMirror());
        }
        return apiHooksType;
    }

    public static boolean isTypeElementOfType(Element element, String typeCanonicalName, ProcessingEnvironment processingEnv) {
        return processingEnv
                .getTypeUtils()
                .isAssignable(element.asType(),
                 processingEnv.getElementUtils()
                .getTypeElement(typeCanonicalName)
                .asType());
    }

    public static boolean isClazzArchivable(Class<?> clazz, ReflectionCache reflectionCache){
        return reflectionCache.getEntitiesCache().get(clazz.getSimpleName()).isArchivable();
    }

    public static String getCollectionType(VariableElement element){
        return element
                .asType()
                .toString()
                .replaceAll("^.+<", "")
                .replaceAll(">", "");
    }

    public static String getMapKeyType(VariableElement element){
        return element
                .asType()
                .toString()
                .replaceAll("^.+<", "")
                .replaceAll(",.+", "");
    }

    public static boolean isIterable(TypeMirror typeMirror, ProcessingEnvironment processingEnv){
        TypeMirror iterableType =
                processingEnv.getTypeUtils()
                        .erasure(
                                processingEnv
                                        .getElementUtils()
                                        .getTypeElement("java.lang.Iterable")
                                        .asType()
                        );
        return processingEnv.getTypeUtils().isAssignable(typeMirror, iterableType);
    }

    public static boolean isMap(TypeMirror typeMirror, ProcessingEnvironment processingEnv){
        TypeMirror iterableType =
                processingEnv.getTypeUtils()
                        .erasure(
                                processingEnv
                                        .getElementUtils()
                                        .getTypeElement("java.util.Map")
                                        .asType()
                        );
        return processingEnv.getTypeUtils().isAssignable(typeMirror, iterableType);
    }

    public static boolean isSet(TypeMirror typeMirror, ProcessingEnvironment processingEnv){
        TypeMirror iterableType =
                processingEnv.getTypeUtils()
                        .erasure(
                                processingEnv
                                        .getElementUtils()
                                        .getTypeElement("java.util.Set")
                                        .asType()
                        );
        return processingEnv.getTypeUtils().isAssignable(typeMirror, iterableType);
    }

    public static boolean isFromType(TypeName requestType, TypeName expectedType) {
        if(requestType instanceof ParameterizedTypeName) {
            TypeName typeName = ((ParameterizedTypeName) requestType).rawType;
            return (typeName.equals(expectedType));
        }
        return false;
    }

    public static ParameterSpec graphQLParameter(TypeName typeName, String name, String defaultValue) {
        AnnotationSpec.Builder annotation = AnnotationSpec.builder(GraphQLArgument.class)
                .addMember("name", "$S", name);
        if(defaultValue != null)
            annotation.addMember("defaultValue", "$S", defaultValue);
        return ParameterSpec.builder(typeName, name).addAnnotation(annotation.build()).build();
    }

    public static boolean isForeignKeyOrKeys(VariableElement field) {
        return
                field.getAnnotation(OneToMany.class) != null ||
                field.getAnnotation(ManyToOne.class) != null ||
                field.getAnnotation(OneToOne.class) != null ||
                field.getAnnotation(ManyToMany.class) != null;
    }

    public static String getIdFieldName(TypeElement entity){
        //noinspection OptionalGetWithoutIsPresent
        return
                entity
                .getEnclosedElements()
                .stream()
                .filter(elem -> elem.getKind().isField())
                .filter(field -> field.getAnnotation(Id.class) != null || field.getAnnotation(EmbeddedId.class) != null)
                .findFirst()
                .get()
                .getSimpleName()
                .toString();
    }

    public static String collectionCanonicalTypeNameString(VariableElement entityCollectionField) {
        return entityCollectionField.asType().toString().replaceAll("^.+<", "").replaceAll(">", "");
    }

    public static Properties loadPropertiesFromFile(String resourcePath, ProcessingEnvironment env){
        try {
            val configFileInputStream =
                    env.getFiler()
                            .getResource( StandardLocation.CLASS_OUTPUT, "", resourcePath )
                            .openInputStream();
            val prop = new Properties();
            prop.load(configFileInputStream);
            return prop;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String pluralCamelCaseName(String s){
        return toCamelCase(English.plural(s));
    }

    public String pluralPascalCaseName(String s){
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
}
