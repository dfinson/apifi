package dev.sanda.apifi.service;

@SuppressWarnings("unchecked")
public interface ResolverContext<TResolverContext> {
    default TResolverContext get(){ return (TResolverContext) this;}
}
