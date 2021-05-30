package dev.sanda.apifi.utils;

public class ControllerEndpointsConstants {

  public static final String PRIMARY_ENDPOINT = "${apifi.endpoint:/graphql}";
  public static final String SSE_ENDPOINT =
    "${apifi.subscriptions.sse-endpoint:/graphql/sse}";
  public static final String WS_ENDPOINT =
    "${apifi.subscriptions.ws-endpoint:/graphql}";
  public static final String WS_ENABLED = "apifi.subscriptions.ws.enabled";
  public static final String[] WS_HEADERS = {
    "Connection!=Upgrade",
    "Connection!=keep-alive, Upgrade",
  };
}
