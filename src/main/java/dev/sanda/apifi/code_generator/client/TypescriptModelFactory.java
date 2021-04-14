package dev.sanda.apifi.code_generator.client;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;
import lombok.var;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        else if(typeName.startsWith("date"))
            result = "Date";
        else if(typeElementMap.containsKey(typeName))
            result = typeElementMap.get(typeName).getSimpleName().toString();
        return result;
    }

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
        return  "// Apifi utils object model" + NEW_LINE + NEW_LINE +
                PAGE_TYPE + NEW_LINE + NEW_LINE +
                PAGE_REQUEST_TYPE + NEW_LINE + NEW_LINE +
                FREE_TEXT_SEARCH_PAGE_REQUEST_TYPE + NEW_LINE + NEW_LINE +
                SORT_DIRECTION_ENUM_TYPE + NEW_LINE + NEW_LINE +
                FLUX_SINK_OVERFLOW_STRATEGY_ENUM_TYPE + NEW_LINE + NEW_LINE +
                EXECUTION_RESULT_TYPE + NEW_LINE + NEW_LINE +
                EXECUTION_RESULT_ERROR_TYPE + NEW_LINE + NEW_LINE +
                EXECUTION_RESULT_ERROR_TYPE_LOCATIONS + NEW_LINE + NEW_LINE +
                ON_CREATE_SUBSCRIPTION_REQUEST_INPUT_TYPE + NEW_LINE + NEW_LINE +
                SUBSCRIPTION_REQUEST_INPUT_TYPE + NEW_LINE + NEW_LINE +
                DICTIONARY_TYPE;
    }

    private static final String PAGE_TYPE =
            "// represents a subset of the overall data, corresponding to server side JPA pagination" + NEW_LINE +
            "export interface Page<T>{" + NEW_LINE +
            "   content?: Array<T>;" + NEW_LINE +
            "   totalPagesCount?: number;" + NEW_LINE +
            "   totalItemsCount?: number;" + NEW_LINE +
            "   pageNumber?: number;" + NEW_LINE +
            "   customValues?: Map<string, any>;" + NEW_LINE +
            "}";

    private static final String PAGE_REQUEST_TYPE =
            "// input to specify desired pagination parameters"  + NEW_LINE +
            "export interface PageRequest{" + NEW_LINE +
            "   pageNumber?: number;" + NEW_LINE +
            "   sortBy?: string;" + NEW_LINE +
            "   pageSize?: number;" + NEW_LINE +
            "   sortDirection?: SortDirection;" + NEW_LINE +
            "   fetchAll?: boolean;" + NEW_LINE +
            "}";

    private static final String FREE_TEXT_SEARCH_PAGE_REQUEST_TYPE =
            "// input to specify desired pagination parameters, as well as a string value for server side free text search"  + NEW_LINE +
            "export interface FreeTextSearchPageRequest extends PageRequest{" + NEW_LINE +
            "   searchTerm: string;" + NEW_LINE +
            "}";

    private static final String SORT_DIRECTION_ENUM_TYPE =
            "// enum type to specify pagination sort ordering"  + NEW_LINE +
            "export enum SortDirection{" + NEW_LINE +
            "   ASC = 'ASC'," + NEW_LINE +
            "   DESC = 'DESC'" + NEW_LINE +
            "}";

    private static final String FLUX_SINK_OVERFLOW_STRATEGY_ENUM_TYPE =
            "// for GraphQLSubscriptions only - denotes the server side backpressure strategy to be employed by reactive publisher"  + NEW_LINE +
            "export enum OverflowStrategy{" + NEW_LINE +
                    "   IGNORE = 'IGNORE'," + NEW_LINE +
                    "   ERROR = 'ERROR'," + NEW_LINE +
                    "   DROP = 'DROP'," + NEW_LINE +
                    "   LATEST = 'LATEST'," + NEW_LINE +
                    "   BUFFER = 'BUFFER'" + NEW_LINE +
                    "}";

    private static final String EXECUTION_RESULT_TYPE =
            "// a wrapper around any return value from the GraphQL server"  + NEW_LINE +
            "export interface ExecutionResult<T>{" + NEW_LINE +
            "   data?: T;" + NEW_LINE +
            "   errors?: Array<ExecutionResultError>;" + NEW_LINE +
            "}";

    private static final String EXECUTION_RESULT_ERROR_TYPE =
            "// should be fairly self explanatory"  + NEW_LINE +
            "export interface ExecutionResultError{" + NEW_LINE +
                    "   message: string;" + NEW_LINE +
                    "   path?: Array<string>;" + NEW_LINE +
                    "   locations?: Array<ExecutionResultErrorLocation>;" + NEW_LINE +
                    "   extensions?: Map<string, any>;" + NEW_LINE +
                    "}";

    private static final String EXECUTION_RESULT_ERROR_TYPE_LOCATIONS =
            "// should be fairly self explanatory"  + NEW_LINE +
            "export interface ExecutionResultErrorLocation{" + NEW_LINE +
                    "   line: number;" + NEW_LINE +
                    "   column: number;" + NEW_LINE +
                    "}";

    private static final String ON_CREATE_SUBSCRIPTION_REQUEST_INPUT_TYPE =
            "// for GraphQLSubscription only - consists of the SSE event callback functions"  + NEW_LINE +
            "export interface OnCreateSubscriptionRequestInput<T>{" + NEW_LINE +
                    "   onExecutionResult: (result: ExecutionResult<T>) => void;" + NEW_LINE +
                    "   onComplete?: () => void;" + NEW_LINE +
                    "   onFatalError?: (message: string) => void;" + NEW_LINE +
                    "}";

    private static final String SUBSCRIPTION_REQUEST_INPUT_TYPE =
            "// for GraphQLSubscription only - consists of the SSE event callback functions, "  + NEW_LINE +
            "// as well as an array of objects which will be the tracked subjects of the subscription."  + NEW_LINE +
            "export interface SubscriptionRequestInput<T> extends OnCreateSubscriptionRequestInput<T>{" + NEW_LINE +
                    "   toObserve: Array<T>;" + NEW_LINE +
                    "}";

    private static final String DICTIONARY_TYPE =
            "// for custom headers to attach to query or mutation HTTP requests"  + NEW_LINE +
            "export interface Dictionary<T>{" + NEW_LINE +
            "   [Key: string]: T;" + NEW_LINE +
            "}";
}
