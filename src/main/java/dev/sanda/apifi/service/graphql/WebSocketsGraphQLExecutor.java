package dev.sanda.apifi.service.graphql;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;


@Component
public class WebSocketsGraphQLExecutor implements GraphQLRequestExecutor<WebSocketSession> {
    @Getter
    @Autowired
    private GraphQLService graphQLService;
}
