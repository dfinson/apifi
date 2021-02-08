package dev.sanda.apifi.service.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;

import java.util.HashMap;
import java.util.Map;

public interface GraphQLRequestExecutor<R> {
    default Map<String, Object> executeQuery(
            Map<String, Object> requestRaw,
            R request){
        Map<String, Object> variables = new HashMap<>();
        String operationName = "";
        if(requestRaw.get("variables") != null)
            variables = (Map<String, Object>) requestRaw.get("variables");
        if(requestRaw.get("operationName") != null)
            operationName = requestRaw.get("operationName").toString();
        ExecutionResult executionResult = getGraphQLService()
                        .getGraphQLInstance()
                        .execute(ExecutionInput.newExecutionInput()
                .query(requestRaw.get("query").toString())
                .operationName(operationName)
                .variables(variables)
                .context(request)
                .build());
        return executionResult.toSpecification();
    }

    GraphQLService getGraphQLService();
}
