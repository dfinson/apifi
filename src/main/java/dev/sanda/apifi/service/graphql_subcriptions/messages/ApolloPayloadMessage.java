package dev.sanda.apifi.service.graphql_subcriptions.messages;

import lombok.Getter;


public class ApolloPayloadMessage<T> extends ApolloMessage{
    @Getter
    private final T payload;
    public ApolloPayloadMessage(String id, String type, T payload) {
        super(id, type);
        this.payload = payload;
    }
    public ApolloPayloadMessage(String type, T payload) {
        super(null, type);
        this.payload = payload;
    }

    public ApolloPayloadMessage(T payload) {
        this.payload = payload;
    }
}
