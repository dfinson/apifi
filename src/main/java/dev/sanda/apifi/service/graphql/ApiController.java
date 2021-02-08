package dev.sanda.apifi.service.graphql;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class ApiController {
    @Autowired
    private GraphQLRequestExecutor<HttpServletRequest> httpGraphQLRequestExecutor;

    @PostMapping("${apifi.endpoint:/graphql}")
    public Map<String, Object> graphqlEndPoint(@RequestBody Map<String, Object> request, HttpServletRequest httpServletRequest) {
        return httpGraphQLRequestExecutor.executeQuery(request, httpServletRequest);
    }

}
