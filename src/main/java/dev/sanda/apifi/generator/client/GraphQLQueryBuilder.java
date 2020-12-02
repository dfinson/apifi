package dev.sanda.apifi.generator.client;

import lombok.*;

import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.inQuotes;

@Data
public class GraphQLQueryBuilder {

    private GraphQLQueryBuilder(){}

    public GraphQLQueryBuilder(Collection<TypeElement> typeElements){
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

    public String args(){
        val builder = new StringBuilder();
        if(vars.isEmpty()) return "";
        vars.forEach((varName, varType) -> builder.append(varName).append(", "));
        if(!isPrimitiveReturnType)
            builder.append("expectedReturn");
        return builder.append(", ").toString();
    }

    private String varsDef(){
        if(vars.isEmpty()) return "";
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
        return "(" + builder.toString() + ")";
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
        val builder = new StringBuilder();
        vars.forEach(
                (varName, varType) ->
                        builder .append(varName)
                                .append(": $")
                                .append(varName)
                                .append(", ")
        );
        builder.setLength(builder.length() - 2);
        return "(" + builder.toString() + ")";
    }

    private String varsVals() {
        if(vars.isEmpty()) return "";
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
                "\n\t\t\t\t\t}, ";
    }

    public String buildQueryString(){
        return
                "\n\t\t\t\t\t" +
                        "query" + ": `" +
                        queryType.toString() + " " +
                        queryName + varsDef() + " { " + queryName +
                        varsArgs() + expectedResultString() + ", " +
                        varsVals()  +
                    "\n\t\t\t\t\t" + "operationName" + ": " + inQuotes(queryName) + "\n\t\t\t";
    }

    private String expectedResultString() {
        return isPrimitiveReturnType ? " }`" : "${expectedReturn} }`";
    }
}
