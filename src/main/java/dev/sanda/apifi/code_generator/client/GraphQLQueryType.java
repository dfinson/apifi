package dev.sanda.apifi.code_generator.client;

public enum GraphQLQueryType {
    QUERY("query"),
    MUTATION("mutation"),
    SUBSCRIPTION("subscription");

    private final String query;
    GraphQLQueryType(String query) {
        this.query = query;
    }
    @Override
    public String toString(){
        return this.query;
    }
}
