package dev.sanda.apifi.web;

import dev.sanda.apifi.dto.GraphQLRequest;
import dev.sanda.apifi.service.graphql_config.GraphQLRequestExecutor;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionSerializationService;
import lombok.AllArgsConstructor;
import lombok.val;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor(onConstructor_ = @Autowired)
public class ApiController {

    private final GraphQLRequestExecutor<HttpServletRequest> httpGraphQLRequestExecutor;
    private final GraphQLRequestExecutor<WebSocketSession> webSocketGraphQLRequestExecutor;

    private final SubscriptionSerializationService subscriptionSerializationService;

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

    public final static List<Publisher> activePublishers = new ArrayList<>();
    public final static List<WebSocketSession> sessions = new ArrayList<>();

    @GetMapping("/active-subscriptions")
    public List<Publisher> activeSubscriptions(){
        val firstPublisher = activePublishers.get(0);
        val res = subscriptionSerializationService.serializePublisher(firstPublisher);
        return activePublishers;
    }

}
