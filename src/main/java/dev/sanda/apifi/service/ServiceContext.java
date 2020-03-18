package dev.sanda.apifi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Service
public class ServiceContext {
    @Autowired
    private HttpSessionContext httpSessionContext;
    @Autowired
    private Map<String ,ResolverContext> resolverContexts;
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
