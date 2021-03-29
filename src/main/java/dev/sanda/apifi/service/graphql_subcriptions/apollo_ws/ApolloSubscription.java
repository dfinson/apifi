package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import graphql.ExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.reactivestreams.Publisher;

@Data
@AllArgsConstructor
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApolloSubscription {
    private String apolloId;
    private ApolloSubscriber subscriber;
    private Publisher<ExecutionResult> publisher;
}
