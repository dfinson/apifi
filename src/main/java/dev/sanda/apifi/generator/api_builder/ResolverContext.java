package dev.sanda.apifi.generator.api_builder;

@SuppressWarnings("unchecked")
public interface ResolverContext<TResolverContext> {
    default TResolverContext get(){ return (TResolverContext) this;}
}
