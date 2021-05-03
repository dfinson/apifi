package dev.sanda.apifi.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class HttpSessionContext {

  public HttpServletRequest request() {
    return requestAttributes().getRequest();
  }

  public HttpServletResponse response() {
    return requestAttributes().getResponse();
  }

  public SecurityContext security() {
    return SecurityContextHolder.getContext();
  }

  public ServletRequestAttributes requestAttributes() {
    return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
  }
}
