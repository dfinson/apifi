package dev.sanda.apifi.service.graphql_config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.WebSocketSession;

@Configuration
@AllArgsConstructor(onConstructor_ = @Autowired)
public class GraphQLExecutorsFactory {

  private final GraphQLInstanceFactory graphQLInstanceFactory;

  @Bean
  @Primary
  public GraphQLRequestExecutor objectGraphQLRequestExecutor() {
    return () -> graphQLInstanceFactory;
  }

  @Bean
  public GraphQLRequestExecutor<HttpServletRequest> httpGraphQLRequestExecutor() {
    return () -> graphQLInstanceFactory;
  }

  @Bean
  public GraphQLRequestExecutor<WebSocketSession> webSocketGraphQLRequestExecutor() {
    return () -> graphQLInstanceFactory;
  }
}
