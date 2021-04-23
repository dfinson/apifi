package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages;

import lombok.Getter;


public class PayloadMessage<T> extends OperationMessage {
    @Getter
    private final T payload;
    public PayloadMessage(String id, String type, T payload) {
        super(id, type);
        this.payload = payload;
    }
    public PayloadMessage(String type, T payload) {
        super(null, type);
        this.payload = payload;
    }

    public PayloadMessage(T payload) {
        this.payload = payload;
    }
}
