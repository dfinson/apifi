package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApolloMessage {
    private String id;
    private String type;

    public ApolloMessage(String type){
        this.type = type;
    }
}
