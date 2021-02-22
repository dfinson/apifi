package dev.sanda.apifi.service.graphql_subcriptions.messages;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApolloMessage {

    private String id;
    private String type;

    public ApolloMessage(String id, String type){
        this.id = id;
        this.type = type;
    }

    public ApolloMessage(String type){
        this.type = type;
    }


}
