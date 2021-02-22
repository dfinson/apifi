package dev.sanda.apifi.service.graphql_config;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;

@Configuration
@AllArgsConstructor(onConstructor_ = @Autowired)
public class GraphQLExecutorsFactory {

    private final GraphQLService graphQLService;

    @Bean
    public GraphQLRequestExecutor<HttpServletRequest> httpGraphQLRequestExecutor(){
        return () -> graphQLService;
    }

    @Bean
    public GraphQLRequestExecutor<WebSocketSession> webSocketGraphQLRequestExecutor(){
        return () -> graphQLService;
    }
}
