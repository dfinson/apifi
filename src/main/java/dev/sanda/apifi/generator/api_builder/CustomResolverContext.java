package dev.sanda.apifi.generator.api_builder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Service
public class CustomResolverContext {
    @Autowired
    private HttpSessionContext httpSessionContext;
    @Autowired
    private Map<String ,ResolverContext> resolverContexts;
    @SuppressWarnings("unchecked")
    public <TResolverContext> TResolverContext get(String qualifier){
        return resolverContexts.containsKey(qualifier) ? (TResolverContext) resolverContexts.get(qualifier).get() : null;
    }
    public HttpServletRequest request(){
        return httpSessionContext.request();
    }
    public HttpServletResponse response(){
        return httpSessionContext.response();
    }
    public SecurityContext security(){
        return httpSessionContext.security();
    }
}
