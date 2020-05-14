package dev.sanda.apifi.generator.client;

import lombok.Data;
import lombok.val;
import lombok.var;

import java.util.LinkedHashMap;

import static dev.sanda.apifi.utils.ApifiStaticUtils.inQuotes;

@Data
public class GraphQLQueryBuilder {
    private String queryName;
    private GraphQLQueryType queryType;
    private LinkedHashMap<String, String> vars = new LinkedHashMap<>();
    private boolean isPrimitiveReturnType = false;

    public String args(){
        val builder = new StringBuilder();
        if(!vars.isEmpty())
            vars.forEach((varName, varType) -> builder.append(varName).append(", "));
        if(!isPrimitiveReturnType)
            builder.append("expectedReturn");
        return builder.toString();
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
        if(!varType.contains(".")) return varType;
        val start = varType.lastIndexOf(".") + 1;
        var corrected = varType.substring(start).replace("Input", "");
        if(corrected.equals("Long"))
            corrected = "Int";
        else if(corrected.equals("Double"))
            corrected = "Float";
        val suffix = varType.endsWith("!") ? "!" : "";
        return corrected + suffix;
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
