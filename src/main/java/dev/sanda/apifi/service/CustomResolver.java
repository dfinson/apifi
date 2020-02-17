package dev.sanda.apifi.service;

import dev.sanda.apifi.generator.custom_resolver.CustomResolverArgumentsMap;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;

@Component
interface CustomResolver<T, TReturn> {
    TReturn handleRequest(CustomResolverArgumentsMap arguments);
}
