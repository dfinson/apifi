package dev.sanda.apifi.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.Map;

@Data
public class GraphQLRequest {

  private String query;
  private final String operationName;
  private final Map<String, Object> variables;

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> variablesTypeRef =
    new TypeReference<Map<String, Object>>() {};

  @JsonCreator
  public GraphQLRequest(
    @JsonProperty("query") String query,
    @JsonProperty("operationName") String operationName,
    @JsonProperty("variables") Map<String, Object> variables
  ) {
    this.query = query;
    this.operationName = operationName;
    this.variables = variables;
  }

  public static GraphQLRequest fromObjectNode(ObjectNode objectNode) {
    final String query =
      objectNode.hasNonNull("query") ? objectNode.get("query").asText() : null;
    final String operationName =
      objectNode.hasNonNull("operationName")
        ? objectNode.get("operationName").asText()
        : null;
    final Map<String, Object> variables =
      objectNode.hasNonNull("variables")
        ? mapper.convertValue(objectNode.get("variables"), variablesTypeRef)
        : null;
    return new GraphQLRequest(query, operationName, variables);
  }
}
