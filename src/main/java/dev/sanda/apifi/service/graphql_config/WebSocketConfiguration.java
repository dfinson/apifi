package dev.sanda.apifi.service.graphql_config;

import dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.ApolloProtocolHandler;
import dev.sanda.apifi.utils.ConfigValues;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnProperty(name = "apifi.subscriptions.ws.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfiguration implements WebSocketConfigurer {

    @NonNull
    private final ApolloProtocolHandler apolloProtocolHandler;
    @NonNull
    private final WsAllowedOriginsConfig allowedOrigins;
    @NonNull
    private final ConfigValues configValues;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        val wsEndpoint = configValues.getWsEndpoint();
        log.info("registering" + ApolloProtocolHandler.class.getSimpleName() + " websocket handler at \"" + wsEndpoint + "\"");
        registry.addHandler(apolloProtocolHandler, wsEndpoint)
                .setAllowedOrigins(allowedOrigins.originsArray());
    }

}
