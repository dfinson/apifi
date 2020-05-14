package dev.sanda.apifi.generator.client;

public enum GraphQLQueryType {
    QUERY("query"),
    MUTATION("mutation");

    private final String query;
    GraphQLQueryType(String query) {
        this.query = query;
    }
    @Override
    public String toString(){
        return this.query;
    }
}
