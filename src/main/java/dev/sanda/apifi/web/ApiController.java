package dev.sanda.apifi.web;

import dev.sanda.apifi.dto.GraphQLRequest;
import dev.sanda.apifi.service.graphql_config.GraphQLRequestExecutor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@AllArgsConstructor(onConstructor_ = @Autowired)
public class ApiController {

    private final GraphQLRequestExecutor<HttpServletRequest> httpGraphQLRequestExecutor;
    private final GraphQLRequestExecutor<WebSocketSession> webSocketGraphQLRequestExecutor;

    @PostMapping("${apifi.endpoint:/graphql}")
    public Map<String, Object> graphqlEndPoint(@RequestBody GraphQLRequest graphQLRequest, HttpServletRequest httpServletRequest) {
        return httpGraphQLRequestExecutor
                .executeQuery(graphQLRequest, httpServletRequest)
                .toSpecification();
    }

    @GetMapping(
            value = "${apifi.subscriptions.ws-endpoint:/graphql}",
            headers = { "Connection!=Upgrade", "Connection!=keep-alive, Upgrade" }
    )
    @ResponseBody
    public Object executeGet(GraphQLRequest graphQLRequest, WebSocketSession request) {
        return webSocketGraphQLRequestExecutor.executeQuery(graphQLRequest, request);
    }

}
