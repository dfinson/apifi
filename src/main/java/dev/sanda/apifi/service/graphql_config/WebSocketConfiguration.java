package dev.sanda.apifi.service.graphql_config;

import dev.sanda.apifi.service.graphql_subcriptions.ApolloProtocolHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WebSocketConfiguration implements WebSocketConfigurer {

    @NonNull
    private final ApolloProtocolHandler apolloProtocolHandler;
    @NonNull
    private final WsAllowedOriginsConfig allowedOrigins;

    @Value("${apifi.subscriptions.ws-endpoint:/graphql}")
    private String wsEndpoint;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("registering" + ApolloProtocolHandler.class.getSimpleName() + " websocket handler at \"" + wsEndpoint + "\"");
        registry.addHandler(apolloProtocolHandler, wsEndpoint)
                .setAllowedOrigins(allowedOrigins.originsArray());
    }

}
