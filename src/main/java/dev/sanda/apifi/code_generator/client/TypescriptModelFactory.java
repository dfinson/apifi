package dev.sanda.apifi.code_generator.client;

import static dev.sanda.apifi.utils.ApifiStaticUtils.*;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;

@Data
@AllArgsConstructor
public class TypescriptModelFactory {

  private ProcessingEnvironment processingEnv;
  private Map<String, TypeElement> typeElementMap;
  private boolean hasSubscriptions;

  public static String objectModel(
    Set<TypeElement> entitiesSet,
    Set<TypeElement> enumsSet,
    boolean hasSubscriptions,
    ProcessingEnvironment processingEnv
  ) {
    val typeElementsMap = entitiesSet
      .stream()
      .collect(
        Collectors.toMap(
          type -> type.getSimpleName().toString().toLowerCase(),
          Function.identity()
        )
      );
    enumsSet.forEach(
      typeElement ->
        typeElementsMap.put(
          typeElement.getSimpleName().toString().toLowerCase(),
          typeElement
        )
    );
    val factoryInstance = new TypescriptModelFactory(
      processingEnv,
      typeElementsMap,
      hasSubscriptions
    );
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
    return (
      NEW_LINE +
      NEW_LINE +
      "// project specific data model" +
      NEW_LINE +
      NEW_LINE +
      interfaces +
      NEW_LINE +
      enums +
      NEW_LINE +
      NEW_LINE +
      factoryInstance.apifiObjectModel()
    );
  }

  private String generateEnumClass(TypeElement enumTypeElement) {
    return String.format(
      "export enum %s{%s%s%s}",
      enumTypeElement.getSimpleName().toString(),
      NEW_LINE,
      String.join("," + NEW_LINE, getEnumValues(enumTypeElement)),
      NEW_LINE
    );
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
    return String.format(
      "export interface %s{%s}",
      typeElement.getSimpleName().toString(),
      fields
    );
  }

  private String generateField(Map.Entry<String, TypeMirror> graphQLField) {
    val name = graphQLField.getKey();
    val type = resolveFieldType(graphQLField.getValue());
    return "\t" + name + "?: " + type + ";";
  }

  private String resolveFieldType(TypeMirror fieldTypeMirror) {
    val typeName = fieldTypeMirror.toString();
    if (isMap(fieldTypeMirror, processingEnv)) {
      val asDeclaredType = (DeclaredType) fieldTypeMirror;
      val keyType = asDeclaredType.getTypeArguments().get(0).toString();
      val valueType = asDeclaredType.getTypeArguments().get(1).toString();
      return String.format(
        "Map<%s, %s>",
        resolveTypeName(keyType),
        resolveTypeName(valueType)
      );
    } else if (isIterable(fieldTypeMirror, processingEnv)) {
      return String.format(
        "%s<%s>",
        resolveIterableType(fieldTypeMirror),
        resolveParameterType(fieldTypeMirror)
      );
    } else return resolveTypeName(typeName);
  }

  private String resolveIterableType(TypeMirror fieldTypeMirror) {
    return isSet(fieldTypeMirror, processingEnv) ? "Set" : "Array";
  }

  private String resolveParameterType(TypeMirror fieldTypeMirror) {
    val asDeclaredType = (DeclaredType) fieldTypeMirror;
    val parameterTypeName = asDeclaredType.getTypeArguments().get(0).toString();
    return resolveTypeName(parameterTypeName);
  }

  private String resolveTypeName(String typeName) {
    if (typeName.contains(".")) typeName =
      typeName.substring(typeName.lastIndexOf(".") + 1).toLowerCase();
    String result = "any";
    if (numberTypes.contains(typeName)) result = "number"; else if (
      typeName.equals("string") || typeName.startsWith("char")
    ) result = "string"; else if (typeName.equals("boolean")) result =
      "boolean"; else if (typeName.startsWith("date")) result =
      "Date"; else if (typeElementMap.containsKey(typeName)) result =
      typeElementMap.get(typeName).getSimpleName().toString();
    return result;
  }

  private static List<String> getEnumValues(TypeElement enumTypeElement) {
    Preconditions.checkArgument(enumTypeElement.getKind() == ElementKind.ENUM);
    return enumTypeElement
      .getEnclosedElements()
      .stream()
      .filter(element -> element.getKind() == ElementKind.ENUM_CONSTANT)
      .map(element -> "\t" + element.getSimpleName().toString())
      .collect(Collectors.toList());
  }

  private String apifiObjectModel() {
    return (
      "// Apifi utils object model" +
      NEW_LINE +
      NEW_LINE +
      PAGE_TYPE +
      NEW_LINE +
      NEW_LINE +
      PAGE_REQUEST_TYPE +
      NEW_LINE +
      NEW_LINE +
      FREE_TEXT_SEARCH_PAGE_REQUEST_TYPE +
      NEW_LINE +
      NEW_LINE +
      SORT_DIRECTION_ENUM_TYPE +
      NEW_LINE +
      NEW_LINE +
      EXECUTION_RESULT_TYPE +
      NEW_LINE +
      NEW_LINE +
      EXECUTION_RESULT_ERROR_TYPE +
      NEW_LINE +
      NEW_LINE +
      EXECUTION_RESULT_ERROR_TYPE_LOCATIONS +
      NEW_LINE +
      NEW_LINE +
      DICTIONARY_TYPE +
      subscriptionsRelatedApifiObjectModel()
    );
  }

  private String subscriptionsRelatedApifiObjectModel() {
    if (!hasSubscriptions) return "";
    return (
      NEW_LINE +
      NEW_LINE +
      FLUX_SINK_OVERFLOW_STRATEGY_ENUM_TYPE +
      NEW_LINE +
      NEW_LINE +
      BASE_SUBSCRIPTION_REQUEST_INPUT_TYPE +
      NEW_LINE +
      NEW_LINE +
      SSE_EVENT_TYPE +
      NEW_LINE +
      NEW_LINE +
      SUBSCRIPTION_REQUEST_INPUT_TYPE +
      NEW_LINE +
      NEW_LINE +
      ENTITY_COLLECTION_SUBSCRIPTION_REQUEST_INPUT_TYPE
    );
  }

  private static final String PAGE_TYPE =
    "// represents a subset of the overall data, corresponding to server side JPA pagination" +
    NEW_LINE +
    "export interface Page<T>{" +
    NEW_LINE +
    "   content?: Array<T>;" +
    NEW_LINE +
    "   totalPagesCount?: number;" +
    NEW_LINE +
    "   totalItemsCount?: number;" +
    NEW_LINE +
    "   pageNumber?: number;" +
    NEW_LINE +
    "   customValues?: Map<string, any>;" +
    NEW_LINE +
    "}";

  private static final String PAGE_REQUEST_TYPE =
    "// input to specify desired pagination parameters" +
    NEW_LINE +
    "export interface PageRequest{" +
    NEW_LINE +
    "   pageNumber?: number;" +
    NEW_LINE +
    "   sortBy?: string;" +
    NEW_LINE +
    "   pageSize?: number;" +
    NEW_LINE +
    "   sortDirection?: SortDirection;" +
    NEW_LINE +
    "   fetchAll?: boolean;" +
    NEW_LINE +
    "}";

  private static final String FREE_TEXT_SEARCH_PAGE_REQUEST_TYPE =
    "// input to specify desired pagination parameters, as well as a string value for server side free text search" +
    NEW_LINE +
    "export interface FreeTextSearchPageRequest extends PageRequest{" +
    NEW_LINE +
    "   searchTerm: string;" +
    NEW_LINE +
    "}";

  private static final String SORT_DIRECTION_ENUM_TYPE =
    "// enum type to specify pagination sort ordering" +
    NEW_LINE +
    "export enum SortDirection{" +
    NEW_LINE +
    "   ASC = 'ASC'," +
    NEW_LINE +
    "   DESC = 'DESC'" +
    NEW_LINE +
    "}";

  private static final String FLUX_SINK_OVERFLOW_STRATEGY_ENUM_TYPE =
    "// for GraphQLSubscriptions only - denotes the server side backpressure strategy to be employed by reactive publisher" +
    NEW_LINE +
    "export enum OverflowStrategy{" +
    NEW_LINE +
    "   IGNORE = 'IGNORE'," +
    NEW_LINE +
    "   ERROR = 'ERROR'," +
    NEW_LINE +
    "   DROP = 'DROP'," +
    NEW_LINE +
    "   LATEST = 'LATEST'," +
    NEW_LINE +
    "   BUFFER = 'BUFFER'" +
    NEW_LINE +
    "}";

  private static final String EXECUTION_RESULT_TYPE =
    "// a wrapper around any return value from the GraphQL server" +
    NEW_LINE +
    "export interface ExecutionResult<T>{" +
    NEW_LINE +
    "   data?: T;" +
    NEW_LINE +
    "   errors?: Array<ExecutionResultError>;" +
    NEW_LINE +
    "}";

  private static final String EXECUTION_RESULT_ERROR_TYPE =
    "// should be fairly self explanatory" +
    NEW_LINE +
    "export interface ExecutionResultError{" +
    NEW_LINE +
    "   message: string;" +
    NEW_LINE +
    "   path?: Array<string>;" +
    NEW_LINE +
    "   locations?: Array<ExecutionResultErrorLocation>;" +
    NEW_LINE +
    "   extensions?: Map<string, any>;" +
    NEW_LINE +
    "}";

  private static final String EXECUTION_RESULT_ERROR_TYPE_LOCATIONS =
    "// should be fairly self explanatory" +
    NEW_LINE +
    "export interface ExecutionResultErrorLocation{" +
    NEW_LINE +
    "   line: number;" +
    NEW_LINE +
    "   column: number;" +
    NEW_LINE +
    "}";

  private static final String BASE_SUBSCRIPTION_REQUEST_INPUT_TYPE =
    "// GraphQLSubscription: consists of the SSE event callback functions and return value selection graph" +
    NEW_LINE +
    "export interface BaseSubscriptionRequestInput<T>{" +
    NEW_LINE +
    "   selectionGraph: string;" +
    NEW_LINE +
    "   timeout?: number;" +
    NEW_LINE +
    "   backPressureStrategy?: OverflowStrategy;" +
    NEW_LINE +
    "}";

  private static final String SUBSCRIPTION_REQUEST_INPUT_TYPE =
    "// GraphQLSubscription: consists of the SSE event callback functions, " +
    NEW_LINE +
    "// as well as an array of objects which will be the tracked subjects of the subscription." +
    NEW_LINE +
    "export interface SubscriptionRequestInput<T> extends BaseSubscriptionRequestInput<T>{" +
    NEW_LINE +
    "   toObserve: Array<T>;" +
    NEW_LINE +
    "}";

  public static String subscriptionEmitterType(boolean isTypeScriptMode) {
    Function<String, String> ifTSMode = s -> isTypeScriptMode ? s : "";

    return (
      NEW_LINE +
      NEW_LINE +
      "export class SubscriptionEventsEmitter" +
      ifTSMode.apply("<T>") +
      "{" +
      NEW_LINE +
      NEW_LINE +
      "    constructor(eventSourceUrl" +
      ifTSMode.apply(": string") +
      ") {" +
      NEW_LINE +
      NEW_LINE +
      "        this.eventSource = new EventSource(eventSourceUrl, { withCredentials: includeCredentials } );" +
      NEW_LINE +
      NEW_LINE +
      "        this.eventSource.addEventListener('EXECUTION_RESULT', (event" +
      ifTSMode.apply(": SseEvent") +
      ") => {" +
      NEW_LINE +
      "            this.onExecutionResultConsumer && this.onExecutionResultConsumer(JSON.parse(event.data));" +
      NEW_LINE +
      "        }, false);" +
      "        " +
      NEW_LINE +
      NEW_LINE +
      "        this.eventSource.addEventListener('COMPLETE', (event" +
      ifTSMode.apply(": SseEvent") +
      ") => {" +
      NEW_LINE +
      "            this.onCompleteConsumer && this.onCompleteConsumer();" +
      NEW_LINE +
      "            console.log('completed event stream - terminating connection');" +
      NEW_LINE +
      "            this.eventSource.close();" +
      NEW_LINE +
      "        }, false);" +
      "        " +
      NEW_LINE +
      NEW_LINE +
      "        this.eventSource.addEventListener('FATAL_ERROR', (event" +
      ifTSMode.apply(": SseEvent") +
      ") => {" +
      NEW_LINE +
      "            this.onFatalErrorConsumer && this.onFatalErrorConsumer(event.data['MESSAGE']);" +
      NEW_LINE +
      "            console.log(`encountered fatal error: ${event.data['MESSAGE']} - terminating connection`);" +
      NEW_LINE +
      "            this.eventSource.close();" +
      NEW_LINE +
      "        }, false);" +
      NEW_LINE +
      "    }" +
      NEW_LINE +
      NEW_LINE +
      "    " +
      ifTSMode.apply("private ") +
      "eventSource" +
      ifTSMode.apply(": EventSource") +
      ";" +
      NEW_LINE +
      "    " +
      ifTSMode.apply("private ") +
      "onExecutionResultConsumer" +
      ifTSMode.apply("?: (result: ExecutionResult<T>) => void") +
      ";" +
      NEW_LINE +
      "    " +
      ifTSMode.apply("private ") +
      "onCompleteConsumer" +
      ifTSMode.apply("?: () => void") +
      ";" +
      NEW_LINE +
      "    " +
      ifTSMode.apply("private ") +
      "onFatalErrorConsumer" +
      ifTSMode.apply("?: (message: string) => void") +
      ";" +
      NEW_LINE +
      NEW_LINE +
      "   " +
      ifTSMode.apply("public") +
      " onExecutionResult(onExecutionResultConsumer" +
      ifTSMode.apply(": (result: ExecutionResult<T>) => void") +
      ")" +
      ifTSMode.apply(": SubscriptionEventsEmitter<T>") +
      "{" +
      NEW_LINE +
      "        this.onExecutionResultConsumer = onExecutionResultConsumer;" +
      NEW_LINE +
      "        return this;" +
      NEW_LINE +
      "   }" +
      NEW_LINE +
      NEW_LINE +
      "   " +
      ifTSMode.apply("public") +
      " onComplete(onCompleteConsumer" +
      ifTSMode.apply(": () => void") +
      ")" +
      ifTSMode.apply(": SubscriptionEventsEmitter<T>") +
      "{" +
      NEW_LINE +
      "        this.onCompleteConsumer = onCompleteConsumer;" +
      NEW_LINE +
      "        return this;" +
      NEW_LINE +
      "   }" +
      NEW_LINE +
      NEW_LINE +
      "   " +
      ifTSMode.apply("public") +
      " onFatalError(onFatalErrorConsumer" +
      ifTSMode.apply(": (message: string) => void") +
      ")" +
      ifTSMode.apply(": SubscriptionEventsEmitter<T>") +
      "{" +
      NEW_LINE +
      "        this.onFatalErrorConsumer = onFatalErrorConsumer;" +
      NEW_LINE +
      "        return this;" +
      NEW_LINE +
      "   }" +
      NEW_LINE +
      NEW_LINE +
      "   " +
      ifTSMode.apply("public") +
      " terminate()" +
      ifTSMode.apply(": void") +
      "{" +
      NEW_LINE +
      "        this.eventSource.close();" +
      NEW_LINE +
      "   }" +
      NEW_LINE +
      "}"
    );
  }

  private static final String ENTITY_COLLECTION_SUBSCRIPTION_REQUEST_INPUT_TYPE =
    "// GraphQLSubscription: consists of the SSE event callback functions, " +
    NEW_LINE +
    "export interface EntityCollectionSubscriptionRequestInput<TOwner, TCollection> extends BaseSubscriptionRequestInput<Array<TCollection>>{" +
    NEW_LINE +
    "   owner: TOwner;" +
    NEW_LINE +
    "}";

  private static final String SSE_EVENT_TYPE =
    "// GraphQLSubscription: wrapper around server sent events" +
    NEW_LINE +
    "export interface SseEvent extends Event{" +
    NEW_LINE +
    "   id: string;" +
    NEW_LINE +
    "   name: string;" +
    NEW_LINE +
    "   data: string;" +
    NEW_LINE +
    "}";

  private static final String DICTIONARY_TYPE =
    "// for custom headers to attach to query or mutation HTTP requests" +
    NEW_LINE +
    "export interface Dictionary<T>{" +
    NEW_LINE +
    "   [Key: string]: T;" +
    NEW_LINE +
    "}";
}
