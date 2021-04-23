package dev.sanda.apifi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.sanda.apifi.dto.GraphQLRequest;
import dev.sanda.apifi.service.graphql_config.GraphQLRequestExecutor;
import dev.sanda.apifi.service.graphql_subcriptions.sse.SseSubscriptionsHandler;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@AllArgsConstructor(onConstructor_ = @Autowired)
public class ApiController {

    private final GraphQLRequestExecutor<HttpServletRequest> httpGraphQLRequestExecutor;
    private final GraphQLRequestExecutor<WebSocketSession> webSocketGraphQLRequestExecutor;
    private final SseSubscriptionsHandler sseSubscriptionsHandler;

    @PostMapping("${apifi.endpoint:/graphql}")
    public Map<String, Object> httpEndpoint(@RequestBody GraphQLRequest graphQLRequest, HttpServletRequest httpServletRequest) {
        return httpGraphQLRequestExecutor
                .executeQuery(graphQLRequest, httpServletRequest)
                .toSpecification();
    }

    @CrossOrigin("*")
    @GetMapping("${apifi.subscriptions.sse-endpoint:/graphql/sse}")
    public SseEmitter sseEndpoint(
            @RequestParam String queryString,
            @RequestParam(required = false) Long timeout,
            HttpServletRequest httpServletRequest) {
        return sseSubscriptionsHandler.handleSubscriptionRequest(parseEncodedRequest(queryString), timeout, httpServletRequest);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    @SneakyThrows
    private GraphQLRequest parseEncodedRequest(String encodedQueryString){
        val decodedQueryString = URLDecoder.decode(encodedQueryString, StandardCharsets.UTF_8.toString());
        val objectNode = (ObjectNode)objectMapper.readTree(decodedQueryString);
        return GraphQLRequest.fromObjectNode(objectNode);
    }

    @GetMapping(
            value = "${apifi.subscriptions.ws-endpoint:/graphql}",
            headers = { "Connection!=Upgrade", "Connection!=keep-alive, Upgrade" }
    )
    @ResponseBody
    @ConditionalOnProperty(name = "apifi.subscriptions.ws.enabled", havingValue = "true", matchIfMissing = true)
    public Object wsEndpoint(GraphQLRequest graphQLRequest, WebSocketSession request) {
        return webSocketGraphQLRequestExecutor.executeQuery(graphQLRequest, request);
    }

}