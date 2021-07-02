package dev.sanda.apifi.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphQLRequest {

  private String query;
  private final String operationName;
  private final Map<String, Object> variables;

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> variablesTypeRef = new TypeReference<Map<String, Object>>() {};

  public static GraphQLRequest fromObjectNode(ObjectNode objectNode) {
    final String query = objectNode.get("query").asText();
    final String operationName = objectNode.has("operationName")
      ? objectNode.get("operationName").asText()
      : null;
    final Map<String, Object> variables = objectNode.has("variables")
      ? mapper.convertValue(objectNode.get("variables"), variablesTypeRef)
      : null;
    return new GraphQLRequest(query, operationName, variables);
  }
}
