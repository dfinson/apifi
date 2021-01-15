package dev.sanda.apifi.generator.client;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;
import lombok.var;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.*;

@Data
@AllArgsConstructor
public class TypescriptModelFactory {

    private ProcessingEnvironment processingEnv;
    private Map<String, TypeElement> typeElementMap;

    public static String generateInterfaces(
            Set<TypeElement> entitiesSet,
            Set<TypeElement> enumsSet,
            ProcessingEnvironment processingEnv){
        val typeElementsMap = entitiesSet
                .stream()
                .collect(
                        Collectors.toMap(
                                type ->
                                type.getSimpleName().toString().toLowerCase(),
                                Function.identity()
                        )
                );
        enumsSet.forEach(typeElement -> typeElementsMap.put(typeElement.getSimpleName().toString().toLowerCase(), typeElement));
        val factoryInstance = new TypescriptModelFactory(processingEnv, typeElementsMap);
        val interfaces = entitiesSet
                .stream()
                .map(factoryInstance::generateInterface)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        val enums =
                System.lineSeparator() +
                 enumsSet
                .stream()
                .map(factoryInstance::generateEnumClass)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        return interfaces + System.lineSeparator() + enums;
    }

    private String generateEnumClass(TypeElement enumTypeElement) {
        return String.format(
                "export enum %s{%s%s%s}",
                enumTypeElement.getSimpleName().toString(),
                System.lineSeparator(),
                String.join("," + System.lineSeparator(), getEnumValues(enumTypeElement)),
                System.lineSeparator());
    }

    private String generateInterface(TypeElement typeElement) {
        val fields =
                System.lineSeparator() +
                getGraphQLFields(typeElement)
                .entrySet()
                .stream()
                .map(this::generateField)
                .collect(Collectors.joining(System.lineSeparator())) +
                System.lineSeparator();
        return String.format("export interface %s{%s}", typeElement.getSimpleName().toString(), fields);
    }

    private String generateField(Map.Entry<String, TypeMirror> graphQLField) {
        val name = graphQLField.getKey();
        val type = resolveFieldType(graphQLField.getValue());
        return "\t" + name + "?: " + type + ";";
    }

    private String resolveFieldType(TypeMirror fieldTypeMirror) {
        val typeName = fieldTypeMirror.toString();
        if(isMap(fieldTypeMirror, processingEnv)){
          val asDeclaredType = (DeclaredType)fieldTypeMirror;
          val keyType = asDeclaredType.getTypeArguments().get(0).toString();
          val valueType = asDeclaredType.getTypeArguments().get(1).toString();
          return String.format("Map<%s, %s>",
                  resolveTypeName(keyType),
                  resolveTypeName(valueType)
          );
        } else if(isIterable(fieldTypeMirror, processingEnv)){
            return String.format("%s<%s>",
                    resolveIterableType(fieldTypeMirror),
                    resolveParameterType(fieldTypeMirror));
        }else
            return resolveTypeName(typeName);
    }

    private String resolveIterableType(TypeMirror fieldTypeMirror) {
        return isSet(fieldTypeMirror, processingEnv) ? "Set" : "Array";
    }

    private String resolveParameterType(TypeMirror fieldTypeMirror) {
        val asDeclaredType = (DeclaredType)fieldTypeMirror;
        val parameterTypeName = asDeclaredType.getTypeArguments().get(0).toString();
        return resolveTypeName(parameterTypeName);
    }

    private String resolveTypeName(String typeName) {
        if(typeName.contains("."))
            typeName = typeName.substring(typeName.lastIndexOf(".") + 1).toLowerCase();
        var result = "any";
        if(numberTypes.contains(typeName))
            result = "number";
        else if(typeName.equals("string") || typeName.startsWith("char"))
            result = "string";
        else if(typeName.equals("boolean"))
            result = "boolean";
        else if(typeElementMap.containsKey(typeName))
            result = typeElementMap.get(typeName).getSimpleName().toString();
        return result;
    }

    private static final Set<String> numberTypes =
            new HashSet<>(Arrays.asList("byte", "short", "int", "integer", "long", "float", "double"));

    private static List<String> getEnumValues(TypeElement enumTypeElement) {
        Preconditions.checkArgument(enumTypeElement.getKind() == ElementKind.ENUM);
        return   enumTypeElement
                .getEnclosedElements()
                .stream()
                .filter(element -> element.getKind() == ElementKind.ENUM_CONSTANT)
                .map(element -> "\t" + element.getSimpleName().toString())
                .collect(Collectors.toList());
    }
}
