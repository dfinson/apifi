package dev.sanda.apifi;

import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.ApiHooks;
import dev.sanda.apifi.service.EmbeddedCollectionApiHooks;
import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.ReflectionCache;
import lombok.var;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

    public static void argsToResolver(String resolverParams, MethodSpec.Builder builder) {
        TypeName listOfObjects = ParameterizedTypeName.get(List.class, Object.class);
        CodeBlock.Builder block = CodeBlock.builder()
                .add("$T args = $T.asList($L)", listOfObjects, Arrays.class, resolverParams);
        builder.addStatement(block.build());
    }

    public static Set<? extends TypeElement> getGraphQLApiEntities(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
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

    public static String dataManagerName(Element entity) {
        return camelcaseNameOf(entity) + "DataManager";
    }

    public static TypeName collectionTypeName(VariableElement embedded) {
        String typeNameString = embedded.asType().toString();
        typeNameString = typeNameString.replaceAll("^.+<", "");
        typeNameString = typeNameString.replaceAll(">", "");
        int lastDot = typeNameString.lastIndexOf('.');
        String packageName = typeNameString.substring(0, lastDot);
        String simpleClassName = typeNameString.substring(lastDot + 1);
        return ClassName.get(packageName, simpleClassName);
    }

    /*public static String collectionType(VariableElement embedded) {
        String typeNameString = embedded.asType().toString();
        typeNameString = typeNameString.replaceAll("^.+<", "");
        typeNameString = typeNameString.replaceAll(">", "");
        int lastDot = typeNameString.lastIndexOf('.');
        return typeNameString.substring(lastDot + 1);
    }*/

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

    public static ParameterSpec asEmbeddedCollectionParamList(VariableElement embedded){
        ClassName list = ClassName.get("java.util", "List");
        TypeName typeClassName = collectionTypeName(embedded);
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

    public static String embeddedCollectionMetaOpsName(VariableElement embedded) {
        ApiHooks apiHooks = embedded.getAnnotation(ApiHooks.class);
        if (apiHooks == null)
            return toCamelCase(embedded.getSimpleName().toString()) +
                    toPascalCase(EmbeddedCollectionApiHooks.class.getSimpleName());

        Class<?> apiHooksImpl = apiHooks.value();
        if (apiHooksImpl.isAnnotationPresent(Component.class) || apiHooksImpl.isAnnotationPresent(Service.class))
            return toCamelCase(apiHooksImpl.getSimpleName());
        return "new " + apiHooksImpl.getSimpleName() + "()";
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

    public static <T> Object getId(T input, ReflectionCache reflectionCache) {
        return reflectionCache.getIdOf(input.getClass().getSimpleName(), input);
    }

    public static String getCollectionType(VariableElement element){
        return element
                .asType()
                .toString()
                .replaceAll("^.+<", "")
                .replaceAll(">", "");
    }
}
