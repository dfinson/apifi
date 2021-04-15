package dev.sanda.apifi.code_generator.client;

import lombok.*;

import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.SUBSCRIPTION;
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
        return  queryName.startsWith("on") && queryName.contains("Created")
                ? String.format(": BaseSubscriptionRequestInput<Array<%s>>", entityReturnType)
                : String.format(": BaseSubscriptionRequestInput<%s>", entityReturnType);
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
        if(!isOnCreatedSubscriptionType())
            builder.append("$toObserve: ").append(String.format("[%s]", entityReturnType)).append(", ");
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
        if(!isOnCreatedSubscriptionType())
            builder.append("toObserve: $toObserve, ");
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
        if(!isOnCreatedSubscriptionType())
            builder.append("\t")
                    .append(inQuotes("toObserve"))
                    .append(": ")
                    .append("input.toObserve")
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

    private boolean isOnCreatedSubscriptionType(){
        return isSubscription() && queryName.endsWith("Created");
    }

    public String getSubscriptionReturnType() {
        return isOnCreatedSubscriptionType() ? "Array<" + entityReturnType + ">" : entityReturnType;
    }
}
