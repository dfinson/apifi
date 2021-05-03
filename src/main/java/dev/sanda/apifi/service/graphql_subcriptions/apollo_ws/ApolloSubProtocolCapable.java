package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import java.util.Collections;
import java.util.List;
import org.springframework.web.socket.SubProtocolCapable;

public interface ApolloSubProtocolCapable extends SubProtocolCapable {
  @Override
  default List<String> getSubProtocols() {
    return Collections.singletonList("graphql-ws");
  }
}
