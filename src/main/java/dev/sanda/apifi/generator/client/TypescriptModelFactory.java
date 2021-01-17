package dev.sanda.apifi.generator.client;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.dto.PageRequest;
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

    public static String objectModel(
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
                .collect(Collectors.joining(NEW_LINE + NEW_LINE));
        val enums =
                NEW_LINE +
                 enumsSet
                .stream()
                .map(factoryInstance::generateEnumClass)
                .collect(Collectors.joining(NEW_LINE + NEW_LINE));
        return  "// project specific data model" + NEW_LINE + NEW_LINE +
                interfaces + NEW_LINE +
                enums + NEW_LINE + NEW_LINE +
                apifiObjectModel();
    }

    private String generateEnumClass(TypeElement enumTypeElement) {
        return String.format(
                "export enum %s{%s%s%s}",
                enumTypeElement.getSimpleName().toString(),
                NEW_LINE,
                String.join("," + NEW_LINE, getEnumValues(enumTypeElement)),
                NEW_LINE);
    }

    private String generateInterface(TypeElement typeElement) {
        val fields =
                NEW_LINE +
                getGraphQLFields(typeElement)
                .entrySet()
                .stream()
                .map(this::generateField)
                .collect(Collectors.joining(NEW_LINE)) +
                NEW_LINE;
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

    private static String apifiObjectModel(){
        return  "// Apifi object model" + NEW_LINE + NEW_LINE +
                PAGE_TYPE + NEW_LINE + NEW_LINE +
                PAGE_REQUEST_TYPE + NEW_LINE + NEW_LINE +
                FREE_TEXT_SEARCH_PAGE_REQUEST_TYPE + NEW_LINE + NEW_LINE +
                SORT_DIRECTION_ENUM_TYPE + NEW_LINE + NEW_LINE +
                GRAPHQL_RESULT_TYPE;
    }

    private static final String PAGE_TYPE =
            "export interface Page<T>{" + NEW_LINE +
            "   content?: Array<T>;" + NEW_LINE +
            "   totalPagesCount?: number;" + NEW_LINE +
            "   totalItemsCount?: number;" + NEW_LINE +
            "   pageNumber?: number;" + NEW_LINE +
            "   customValues?: Map<string, any>;" + NEW_LINE +
            "}";

    private static final String PAGE_REQUEST_TYPE =
            "export interface PageRequest<T>{" + NEW_LINE +
            "   pageNumber?: number;" + NEW_LINE +
            "   sortBy?: string;" + NEW_LINE +
            "   pageSize?: number;" + NEW_LINE +
            "   sortDirection?: SortDirection;" + NEW_LINE +
            "   fetchAll?: boolean;" + NEW_LINE +
            "}";

    private static final String FREE_TEXT_SEARCH_PAGE_REQUEST_TYPE =
            "export interface FreeTextSearchPageRequest<T> extends PageRequest<T>{" + NEW_LINE +
            "   searchTerm: string;" + NEW_LINE +
            "}";

    private static final String SORT_DIRECTION_ENUM_TYPE =
            "export enum SortDirection{" + NEW_LINE +
            "   ASC = 'ASC'," + NEW_LINE +
            "   DESC = 'DESC'" + NEW_LINE +
            "}";

    private static final String GRAPHQL_RESULT_TYPE =
            "export interface GraphQLResult<T>{" + NEW_LINE +
            "   data?: T;" + NEW_LINE +
            "   errors?: Array<string>;" + NEW_LINE +
            "}";
}
