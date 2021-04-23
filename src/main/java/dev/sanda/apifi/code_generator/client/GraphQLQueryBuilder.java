package dev.sanda.apifi.code_generator.client;

import lombok.*;

import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.SUBSCRIPTION;
import static dev.sanda.apifi.code_generator.client.SubscriptionObservableType.COLLECTION_OWNER;
import static dev.sanda.apifi.code_generator.client.SubscriptionObservableType.LIST_TO_OBSERVE;
import static dev.sanda.apifi.utils.ApifiStaticUtils.inQuotes;

@Data
public class GraphQLQueryBuilder {

    public GraphQLQueryBuilder(
            Collection<TypeElement> typeElements,
            ClientSideReturnType clientSideReturnType,
            String returnType){
        this.entityReturnType = returnType;
        this.clientSideReturnType = clientSideReturnType;
        entityTypes = typeElements
                .stream()
                .map(type -> type.getSimpleName().toString() + "Input")
                .collect(Collectors.toCollection(HashSet::new));
        entityTypes.add("PageRequestInput");
        entityTypes.add("FreeTextSearchPageRequestInput");
    }

    private HashSet<String> entityTypes;
    private String queryName;
    private GraphQLQueryType queryType;
    private SubscriptionObservableType subscriptionObservableType;
    private LinkedHashMap<String, String> vars = new LinkedHashMap<>();
    private boolean isPrimitiveReturnType = false;
    private ClientSideReturnType clientSideReturnType;
    private boolean isTypescriptMode;
    private String entityReturnType;
    private String ownerEntityType;
    private String findByUniqueFieldType;

    public String args(){
        val builder = new StringBuilder();
        if(vars.isEmpty()) return "";
        vars.forEach((varName, varType) -> {
            builder.append(varName).append(resolveVarTypescriptType(varName));
            if(!isSubscription())
                builder.append(", ");
        });
        if(!isPrimitiveReturnType && !isSubscription()) {
            builder.append("selectionGraph").append(selectionGraphType());
            builder.append(", ");
        }
        return builder.toString();
    }

    private String resolveVarTypescriptType(String varName) {
        if(!isTypescriptMode) return "";
        return isSubscription() ? resolveSubscriptionVarTypescriptType(varName) : resolveQueryOrMutationVarTypescriptType(varName);
    }

    private String resolveSubscriptionVarTypescriptType(String varName) {
        switch (subscriptionObservableType){
            case LIST_TO_OBSERVE: return String.format(": SubscriptionRequestInput<%s>", entityReturnType);
            case COLLECTION_OWNER: return String.format(": EntityCollectionSubscriptionRequestInput<%s, %s>", ownerEntityType, entityReturnType);
            case ENTITY_TYPE: return String.format(": BaseSubscriptionRequestInput<Array<%s>>", entityReturnType);
        }
        throw new RuntimeException("This should not have happened...");
    }

    private String resolveQueryOrMutationVarTypescriptType(String varName) {
        if(varName.equals("owner"))
            return ": " + this.ownerEntityType;
        if(queryName.contains("ByUnique"))
            return ": " + findByUniqueFieldType;
        switch (clientSideReturnType){
            case PAGE: return ": " + (isFreeTextSearchQuery() ? "FreeTextSearchPageRequest" : "PageRequest");
            case NUMBER:return "";
            case INSTANCE: return ": " + entityReturnType;
            case ARRAY: return ": Array<" + entityReturnType + ">";
            case SET: return ": Set<" + entityReturnType + ">";
            case MAP: return queryName.startsWith("add")
                    ? ": Map<" + entityReturnType + ">"
                    : ": Array<" + entityReturnType.substring(0, entityReturnType.indexOf(",")) + ">";
        }
        return ": any";
    }

    private String varsDef(){
        if(vars.isEmpty()) return "";
        return isSubscription() ? subscriptionVarsDef() : queryOrMutationVarsDef();
    }

    private String queryOrMutationVarsDef(){
        val builder = new StringBuilder();
        vars.forEach(
                (varName, varType) ->
                        builder .append("$")
                                .append(varName)
                                .append(": ")
                                .append(resolveVarType(varType))
                                .append(", ")
        );
        builder.setLength(builder.length() - 2);
        return "(" + builder + ")";
    }

    private String subscriptionVarsDef(){
        val builder = new StringBuilder();
        switch (subscriptionObservableType){
            case LIST_TO_OBSERVE: builder.append("$toObserve: ").append(String.format("[%s]", entityReturnType)).append(", "); break;
            case COLLECTION_OWNER: builder.append("$owner: ").append(ownerEntityType).append(", "); break;
        }
        builder.append("$backPressureStrategy: OverflowStrategy");
        return "(" + builder + ")";
    }

    private String resolveVarType(String varType) {
        boolean isArray = varType.startsWith("[") && varType.endsWith("]");
        if(isEntityType(varType)) return varType;
        varType = varType.replaceAll("\\[", "").replaceAll("]", "").replaceAll("Input", "");
        val start = varType.lastIndexOf(".") + 1;
        var simpleTypeName = varType.substring(start);
        return  isArray ? "[" + simpleTypeName + "]" : simpleTypeName;
    }

    private boolean isEntityType(String varType) {
        return entityTypes.contains(varType.replaceAll("\\[", "").replaceAll("]", ""));
    }

    private String varsArgs() {
        if(vars.isEmpty()) return "";
        if(isSubscription())
            return subscriptionVarsArgs();
        val builder = new StringBuilder();
        vars.forEach(
                (varName, varType) ->
                        builder .append(varName)
                                .append(": $")
                                .append(varName)
                                .append(", ")
        );
        builder.setLength(builder.length() - 2);
        return "(" + builder + ")";
    }

    private String subscriptionVarsArgs() {
        val builder = new StringBuilder();
        switch (subscriptionObservableType){
            case LIST_TO_OBSERVE: builder.append("toObserve: $toObserve, "); break;
            case COLLECTION_OWNER: builder.append("owner: $owner, "); break;
        }
        builder.append("backPressureStrategy: $backPressureStrategy");
        return "(" + builder + ")";
    }

    private String varsVals() {
        if(vars.isEmpty()) return "";
        if(isSubscription())
            return subscriptionVarsVals();
        val builder = new StringBuilder();
        vars.forEach(
                (varName, varType) ->
                        builder .append("\t")
                                .append(inQuotes(varName))
                                .append(": ")
                                .append(varName)
                                .append(", \n\t\t\t\t\t")
        );
        builder.setLength(builder.length() - 8);
        return "\n\t\t\t\t\t" +
                "variables" +
                ": {\n\t\t\t\t\t" +
                builder +
                "\n\t\t\t\t\t}";
    }

    private String subscriptionVarsVals() {
        val builder = new StringBuilder();
        if(subscriptionObservableType.equals(LIST_TO_OBSERVE))
            builder.append("\t")
                    .append(inQuotes("toObserve"))
                    .append(": ")
                    .append("input.toObserve")
                    .append(", \n\t\t\t\t\t");
        else if(subscriptionObservableType.equals(COLLECTION_OWNER))
            builder.append("\t")
                    .append(inQuotes("owner"))
                    .append(": ")
                    .append("input.owner")
                    .append(", \n\t\t\t\t\t");
        builder.append("\t")
                .append(inQuotes("backPressureStrategy"))
                .append(": ")
                .append("input.backPressureStrategy || 'BUFFER'")
                .append(", \n\t\t\t\t\t");
        builder.setLength(builder.length() - 8);
        return "\n\t\t\t\t\t" +
                "variables" +
                ": {\n\t\t\t\t\t" +
                builder +
                "\n\t\t\t\t\t}";
    }

    public String buildQueryString(){
        return
                "\n\t\t\t\t\t" +
                        "query" + ": `" +
                        queryType.toString() + " " +
                        queryName + varsDef() + " { " + queryName +
                        varsArgs() + expectedResultString() + ", " +
                        varsVals()  + "\n\t\t\t";
    }

    private String expectedResultString() {
        return isPrimitiveReturnType ? " }`" : String.format("${%sselectionGraph}", (isSubscription() ? "input." : "")) + " }`";
    }

    private boolean isFreeTextSearchQuery() {
        return vars.getOrDefault("input", "").equals("FreeTextSearchPageRequestInput");
    }

    private String selectionGraphType(){
        return isTypescriptMode ? ": string" : "";
    }

    private boolean isSubscription(){
        return queryType.equals(SUBSCRIPTION);
    }

    public String getSubscriptionReturnType() {
        return subscriptionObservableType.equals(LIST_TO_OBSERVE) ? entityReturnType : "Array<" + entityReturnType + ">";
    }
}
