package dev.sanda.apifi.service.graphql_subcriptions.messages;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class ApolloMessage {
    private String id;
    @NonNull
    private String type;
}
