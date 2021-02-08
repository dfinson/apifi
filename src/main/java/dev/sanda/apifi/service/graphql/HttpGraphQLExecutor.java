package dev.sanda.apifi.service.graphql;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
public class HttpGraphQLExecutor implements GraphQLRequestExecutor<HttpServletRequest> {
    @Getter
    @Autowired
    private GraphQLService graphQLService;
}
