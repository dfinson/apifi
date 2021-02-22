package dev.sanda.apifi.service.graphql_subcriptions;

import org.springframework.web.socket.SubProtocolCapable;

import java.util.Collections;
import java.util.List;

public interface ApolloSubProtocolCapable extends SubProtocolCapable {
    @Override
    default List<String> getSubProtocols(){
        return Collections.singletonList("graphql-ws");
    }
}
