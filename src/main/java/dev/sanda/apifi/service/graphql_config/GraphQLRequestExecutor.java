package dev.sanda.apifi.service.graphql_config;

import dev.sanda.apifi.dto.GraphQLRequest;
import graphql.ExecutionInput;
import graphql.ExecutionResult;

import java.util.HashMap;
import java.util.Map;

public interface GraphQLRequestExecutor<R> {
    default ExecutionResult executeQuery(
            GraphQLRequest requestRaw,
            R request){
        Map<String, Object> variables = new HashMap<>();
        String operationName = "";
        if(requestRaw.getVariables() != null)
            variables = requestRaw.getVariables();
        if(requestRaw.getOperationName() != null)
            operationName = requestRaw.getOperationName();
        return getGraphQLService()
                        .getGraphQLInstance()
                        .execute(ExecutionInput.newExecutionInput()
                .query(requestRaw.getQuery())
                .operationName(operationName)
                .variables(variables)
                .context(request)
                .build());
    }

    GraphQLService getGraphQLService();
}
