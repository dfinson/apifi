package dev.sanda.apifi.utils;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ConfigValues {
    @Value("#{new Boolean('${datafi.logging-enabled:false}')}")
    private Boolean datafiLoggingEnabled;
    @Value("${apifi.subscriptions.ws-endpoint:/graphql}")
    private String wsEndpoint;
    @Value("#{new Long('${apifi.subscriptions.pending-transaction-retry-interval:50}')}")
    private Long pendingTransactionRetryInterval;
    @Value("#{new Long('${apifi.subscriptions.pending-transaction-timeout:500}')}")
    private Long pendingTransactionTimeout;
}
