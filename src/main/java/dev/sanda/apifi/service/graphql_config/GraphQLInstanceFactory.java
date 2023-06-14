package dev.sanda.apifi.service.graphql_config;

import graphql.GraphQL;
import org.dataloader.DataLoaderRegistry;

public interface GraphQLInstanceFactory {
  GraphQL getGraphQLInstance();
  DataLoaderRegistry getDataLoaderRegistry();
}
