package dev.sanda.apifi.generator.client;

import lombok.Data;
import lombok.val;

import java.util.LinkedHashMap;

@Data
public class GraphQLQueryBuilder {
    private String queryName;
    private LinkedHashMap<String, String> vars;

    private String inQuotes(String str){
        return '\"' + str + '\"';
    }

    private String varsDef(){
        if(vars.isEmpty()) return "";
        val builder = new StringBuilder();
        vars.forEach(
                (varName, varType) ->
                builder .append("$")
                        .append(varName)
                        .append(": ")
                        .append(varType)
                        .append(", ")
        );
        builder.setLength(builder.length() - 2);
        return "(" + builder.toString() + ")";
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
                        builder .append(inQuotes(varName))
                                .append(": ")
                                .append(varName)
                                .append(", \n")
        );
        builder.setLength(builder.length() - 3);
        return builder.toString();
    }

    public String toQueryString(){
        return
                "{\n" +
                    inQuotes("query") + ":" +
                    "\n\t" + " query " + queryName + varsDef() +
                    "\n\t" + queryName + varsArgs() +
                    "\n\t + expectedReturn + " + inQuotes(", ") +
                    "\n\t" + inQuotes("variables") + ": {" +
                    "\n\t" + varsVals() + "}, " +
                    "\n\t" + inQuotes("operationName") + ": " +
                    "\n\t" + inQuotes(queryName) +
                "\n}";
    }
}
