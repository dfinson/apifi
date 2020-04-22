package dev.sanda.apifi.service;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;


import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public interface GraphQLRequestExecutor {
    default Map<String, Object> executeQuery(
            Map<String, Object> requestRaw,
            HttpServletRequest raw,
            GraphQL graphQL){
        Map<String, Object> variables = new HashMap<>();
        String operationName = "";
        if(requestRaw.get("variables") != null)
            variables = (Map<String, Object>) requestRaw.get("variables");
        if(requestRaw.get("operationName") != null)
            operationName = requestRaw.get("operationName").toString();
        ExecutionResult executionResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(requestRaw.get("query").toString())
                .operationName(operationName)
                .variables(variables)
                .context(raw)
                .build());
        return executionResult.toSpecification();
    }
}
