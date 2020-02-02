package dev.sanda.apifi.generator.api_builder;

import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;

@Component
interface CustomResolver<T, TReturn> {
    TReturn handleRequest(CustomResolverArgumentsMap arguments, CustomResolverContext context, DataManager<T> dataManager);
}
