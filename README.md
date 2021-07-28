
  
  [![Gitter](https://badges.gitter.im/Apifi-framework/community.svg)](https://gitter.im/Apifi-framework/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
  <img src='https://github.com/sanda-dev/apifi/blob/master/readme%20images/apifi-logo.png' width='470' alt='Apifi Logo' />
  
# Apifi   
 * [Introduction](#introduction)
  * [Getting started](#getting-started)
    + [Installation](#installation)
    + [Basic configuration properties](#basic-configuration-properties)
    + [Hello World](#hello-world)
  * [Defining GraphQL Endpoints](#defining-graphql-endpoints)
  * [Customization](#customization)
  * [Custom endpoints](#custom-endpoints)
  * [Search endpoints](#search-endpoints)
  * [Free text search](#free-text-search)
    + [Overview](#overview)
    + [Input](#input)
    + [Output](#output)
    + [Customization](#customization-1)
  * [Entity Collections](#entity-collections)
  * [Element Collections](#element-collections)
  * [Element collection maps](#element-collection-maps)
  * [GraphQL Subscriptions](#graphql-subscriptions)
    + [Overview](#overview-1)
    + [Built in endpoints](#built-in-endpoints)
    + [Custom subscriptions](#custom-subscriptions)
    + [Advanced configuration properties  for GraphQL subscriptions](#advanced-configuration-properties--for-graphql-subscriptions)
  * [Server side pub-sub configuration](#server-side-pub-sub-configuration)
    + [In memory pub-sub](#in-memory-pub-sub)
    + [Redis based pub-sub](#redis-based-pub-sub)
    + [Using other message brokers for pub-sub](#using-other-message-brokers-for-pub-sub)
  * [Client side consumption of GraphQL subscriptions](#client-side-consumption-of-graphql-subscriptions)
  * [Spring security integration](#spring-security-integration)
    + [Class level security](#class-level-security)
    + [Method level security](#method-level-security)
    + [Search endpoints security](#search-endpoints-security)
   * [Microservices architecture](#microservices-architecture)
	   + [Defining the endpoints](#defining-the-endpoints)
	   + [Required configuration](#required-configuration)
  * [Testing](#testing)
    + [TestableGraphQLService](#testablegraphqlservice)
    + [Example](#example)
      - [Model](#model)
      - [Actual GraphQL service to be included in schema](#actual-graphql-service-to-be-included-in-schema)
      - [Testable GraphQL service class which is **not** exposed as part of the schema](#testable-graphql-service-class-which-is-not-exposed-as-part-of-the-schema)
      - [Example Test class for `UserGraphQLApiService`](#example-test-class-for-usergraphqlapiservice)
  * [GraphQL Client](#graphql-client)
    + [Overview](#overview-2)
    + [What it looks like](#what-it-looks-like)
  * [Logs](#logs)
  * [Known issues](#known-issues)
  * [Credit](#credit)
  * [License](#license)

  
## Introduction 
Apifi is a Java 8+ annotation processing framework which auto generates GraphQL APIs for JPA based data models. It spans the full API stack; from data access to client side consumption. Apifi is centered around one simple goal: To eliminate the need for generic CRUD related boilerplate *without* compromising on control and customizability.  

### Getting started  
#### Installation  
```xml  
<dependency>  
	<groupId>sanda.dev</groupId>
	<artifactId>apifi</artifactId>
	<version>1.0.0</version>
</dependency>  
```  
#### Basic configuration properties  
- `apifi.endpoint` - specifies the path to be used by the generated API. By default its value is `/graphql`.  
- `apifi.max-query-depth` - specifies the deepest level of query nesting / depth allowed in a GraphQL query. By default its value is 15. 
- `datafi.logging-enabled` - specifies whether data access layer logging will be activated in addition to service layer logging. Defaults to `false` if not specified.


  
#### Hello World  
```java  
@Entity  
@WithCRUDEndpoints({CREATE, UPDATE, GET_BY_ID, DELETE})  
public class User {  
    @Id 
    @GeneratedValue 
    private Long id; 
    private String name; 
    private String email; 
    private String phoneNumber; 
    private String passwordHash;
}  
```  
  
After compiling the project and taking a peek in the *target* folder, the following is the auto generated GraphQL service bean:  
```java  
@...  
public class UserGraphQLApiService {

    /*... various internal logic ...*/
    @Autowired
    private ApiLogic < User > apiLogic;

    @GraphQLQuery 
    public User getUserById(Long input) {
        return apiLogic.getById(input);
    }
    @GraphQLMutation 
    public User createUser(User input) {
        return apiLogic.create(input);
    }
    @GraphQLMutation 
    public User updateUser(User input) {
        return apiLogic.update(input);
    }
    @GraphQLMutation 
    public User deleteUser(User input) {
        return apiLogic.delete(input);
    }
    
    @GraphQLMutation 
    public User updateUser(User input) {
        return apiLogic.update(input);
    }
    @GraphQLMutation 
    public User deleteUser(User input) {
       return apiLogic.delete(input);
    }
}
 ```
*Note:* As its name suggests, [`ApiLogic<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiLogic.java) implements API CRUD ops generically.   
  
### Defining GraphQL Endpoints  
Note the `User` entity is annotated with `@WithCRUDEndpoints({CREATE, UPDATE, GET_BY_ID, DELETE})`. This is how Apifi can be directed to generate GraphQL API endpoints for an entity. There are 16 types of [CRUDEndpoints](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/CRUDEndpoints.java):  
  
1. GET_PAGINATED_BATCH:  
     
   *Overview:*  
  Fetches a list of instances, one page at a time.  
      
    *Input:*  
  A [`PageRequest`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/PageRequest.java) consisting of:    
      - `pageNumber` delineating which page to fetch     
      - `pageSize` delineating the amount of items said page should contain      
      - `sortBy` parameter delineating which field the items should be ordered by - defaults to the ID if not set     
      - `sortDirection` parameter delineating whether said order by should be in ascending (ASC) order or descending (DESC) order - defaults to ASC if not set     
      - `fetchAll` Boolean parameter which can override all of the above and fetch all of the instances within a single page - defaults to `false` if not set.    
        
   *Output:*  
  [`Page<T>`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/Page.java)  consisting of a `List<T>` payload called `content`, as well as a `totalPagesCount` and a `totalItemsCount`.  
  
2. GET_BY_ID:   
  
   *Overview, input and output:*   
     
 What its name implies.  
     
3. GET_BATCH_BY_IDS:  
  
   *Overview, input and output:*   
     
 Same as the previous, pluralized.  
     
4. CREATE:  
  
   *Overview:*   
     
 Creates a single instance and save it to the database  
     
   *Input:*   
     
 A single entity instance.  
     
   *Output:*   
     
 The new instance after having been saved to the database.   
     
5. BATCH_CREATE:  
  
   *Overview, input and output:*   
     
 Same as previous, pluralized.  
6. UPDATE:  
  
   *Overview:*   
     
 Updates a single entity instance. This does not include collections, which can have their own set of endpoints as will be explained.  
     
   *Input:*  
  A single, pre-existing entity instance. This instance must include an id, as well as any fields which are to be updated. This method checks for non-null fields in the input, and then overwrites any existing values of the corresponding fields within the existing instance.  
      
   *Output:*   
     
 The updated instance  
     
7. BATCH_UPDATE:   
  
   *Overview, input and output:*   
     
 Same as previous, pluralized.  
     
8. DELETE:  
  
   *Overview:*  
  Deletes a single entity instance.  
     
   *Input:*   
     
 A single entity instance. This must include the ID.  
     
   *Output:*  
  The deleted instance.  
     
9. BATCH_DELETE:  
  
   *Overview, input and output:*   
     
 Same as previous, pluralized.  
     
10. ARCHIVE:    
   *Overview:*    
 Sometimes there is a requirement whereby instances should be archived rather than permanently deleted. In order to enable this for a given entity type, the entity should implement the [`Archivable`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/persistence/Archivable.java) interface. In such a case, the archivability endpoints can be utilized. This particular endpoint archives a single entity instance.    
   *Input:*     
 A single entity instance. This must include the ID.    
   *Output:*    
 The newly archived instance.    
     
11. BATCH_ARCHIVE:  
  
   *Overview, input and output:*   
     
 Same as previous, pluralized.  
     
12. DE_ARCHIVE:  
  
   *Overview, input and output:*   
     
 The opposite of ARCHIVE.  
     
13. BATCH_DE_ARCHIVE:   
  
   *Overview, input and output:*   
     
 Same as previous, pluralized.  
     
14. GET_ARCHIVED_PAGINATED_BATCH:  
  
   *Overview, input and output:*  
  This endpoint functions in precisely the same way as GET_PAGINATED_BATCH, the difference being that this endpoint will only return archived instances.  
     
15. GET_TOTAL_COUNT:  
  
   *Overview, input and output:*   
     
 Gets the total count of all existing instances. Requires no input.  
     
16. GET_TOTAL_ARCHIVED_COUNT:  
  
   *Overview, input and output:*   
     
 Same as previous, for archived instances.  
     
  
### Customization  
Oftentimes there is a need for additional business logic on CRUD endpoints. This is where the [`ApiHooks<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiHooks.java) interface comes into play. It contains methods which are "hooked" or called at the appropriate times during the API endpoint request handling life cycle. For example; the `preCreate(...)` method is called prior to a new entity instance having been passed to the CREATE endpoint being saved to the database, and the `postCreate(...)` method is called immediately after the instance has been saved.  The same goes for any and all other phases of all the other options. As a rule, the hooked methods get passed the state which is relevant to the life cycle phase in which they are called, as well as a [`DataManager<T>`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/service/DataManager.java) bean instance for the entity.  
  
To gain a better understanding as to how this works, see the following code snippet from the `ApiLogic<T>` class where the CRUD operational logic is generically implemented.  More specifically, this is the `batchCreate(List<T> input)` method:   
  
```java  
public List < T > batchCreate(List < T > input) {
    if (apiHooks != null) // <--- note this  
        apiHooks.preBatchCreate(input, dataManager);
    val result = dataManager.saveAll(input);
    if (apiHooks != null) // <--- and this  
        apiHooks.postBatchCreate(result, dataManager);
    logInfo("batchCreate: created {} new {}", result.size(), toPlural(dataManager.getClazzSimpleName()));
    return result;
}  
```  
This pattern repeats itself in the same manner in all of the other [`ApiLogic<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiLogic.java) methods, allowing for the execution of custom defined logic prior to or following mutations and queries. In order to make use of this feature for an entity of type `T`:  
1. create a public class which implements `ApiHooks<T>`  
2. Make sure the class is wired into the application context (`@Component`, etc.).  
3. Override whichever hooks are relevant. See the relevant source code [here](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiHooks.java).  
No further configuration is necessary - Apifi leverages spring dependency injection to determine whether such a class has been created for a given entity. If such a service bean has indeed been created - it will be picked up and used by `ApiLogic<T>`.   
  
Using the previous example:  
```java  
@Entity
@WithCRUDEndpoints({
    CREATE,
    UPDATE,
    GET_BY_ID,
    DELETE
})
public class User {
    @Id @GeneratedValue 
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
}  
```  
Imagine there is a requirement to print a welcome message to the console after every time a new user is added, and a goodbye message after every time a user is deleted. To do so, a custom implementation of `ApiHooks<T>` could be implemented as follows:  
```java  
@Service
public class UserApiHooks implements ApiHooks <User> {
    @Override 
    public void postCreate(User added, DataManager < User > dataManager) {
        System.out.println(String.format("Hello %s!", added.getName()));
    }
    @Override 
    public void postDelete(User deleted, DataManager < User > dataManager) {
        System.out.println(String.format("Goodbye %s", deleted.getName()));
    }
}
```  
The same workflow applies to less trivial use cases such as third party API calls, data related metrics, etc.  

### Custom endpoints

If there is no way to achieve the desired functionality by using Apifi autogenerated endpoints and their corresponding `ApiHooks<T>`, 
custom GraphQL queries, mutations and subscriptions can be added by annotating any spring managed bean with
the `@GraphQLService` annotation. Any contained methods with the annotations `@GraphQLQuery`, `@GraphQLMutation` or `@GraphQLSubscription`
will then be included in the API schema.

*It should be noted that Apifi was designed to mitigate the need for such custom defined endpoints as much as possible. 
Therefore, this feature should be used sparingly - a re-evaluation of the level of cohesion between data model and API requirements may be advisable.*


### Search endpoints  
  
In addition to the above CRUD endpoints, additional endpoints can be added by making use of the following annotations:  
- `@ApiFindBy`: To fetch a `List<T>` of instances of type `T`, with the search criteria being the value of a specific field of type `TField`, the field in question can be annotated with the `@ApiFindBy` annotation.   
*Example*:   
 ```java  
	@Entity
	public class User {
	   @Id @GeneratedValue 
	   private Long id;
	   @ApiFindBy // <--- 
	   private String name; 
	   private String email; 
	   private String phoneNumber; 
	   private String passwordHash;   
	} 
  ```  
 The above `@ApiFindBy` annotation generates the following GraphQL endpoint:  
   ```java  
 @...
public class UserGraphQLService {
	    ...
	    @GraphQLQuery 
	    public List <User> findUsersByName(String name) {
	    return apiLogic.apiFindBy("name", name);
	  }
	  ...
} 
```
 - `@ApiFindAllBy`: Fetch a list of instances with a field value matching at least one of the inputted values.   
*Example:*  
  ```java  
@Entity 
public class User {
    @Id @GeneratedValue 
    private Long id;
    @ApiFindAllBy // <--- 
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
} 
 ```  
 The above `@ApiFindAllBy` would generate the following endpoint:  
   ```java  
 @... 
 public class UserGraphQLService{ 
	 ... 
	 @GraphQLQuery 
	 public List<User> findUsersByNames(List<String> names) {
		 return apiLogic.apiFindAllBy("name", names); 
	 } 
	 ... 
 } 
 ```
 - `@ApiFindByUnique`: To fetch a single instance by the value of a unique field, the field can be annotated with the `@ApiFindByUnique` annotation.  
*Example*:  
  ```java  
@Entity
public class User {
	   @Id
	   @GeneratedValue 
	   private Long id;
	   private String name;
	   @ApiFindByUnique // <--- 
	   @Column(unique = true) // Column MUST be marked as unique! 
	   private String email; 
	   private String phoneNumber; 
	   private String passwordHash;   
}  
 ```  
 The above `@ApiFindByUnique` would generate the following endpoint:  
   ```java  
   @...  
   public class UserGraphQLService{  
 ...      
	  @GraphQLQuery    
      public User findUserByUniqueEmail(String email) {    
        return apiLogic.apiFindByUnique("email", email);    
      }  
      ...  
 } 
```  
### Free text search  
  
#### Overview  
  
Apifi comes with non case sensitive free text search out of the box. To make use of this feature for a given entity, the class should be annotated with the `@WithApiFreeTextSearchByFields({"field1", "field2", ...})` annotation.  
  
#### Input  
A single [FreeTextSearchPageRequest](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/FreeTextSearchPageRequest.java) object which extends the above described [PageRequest](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/PageRequest.java) with the addition of a `String searchTerm` field.  
  
#### Output  
A [Page](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/Page.java) object containing `List<T> content`, `long totalPageCount`, and `long totalItemsCount` (same output type as GET_PAGINATED_BATCH).  
  
*Example:*  
```java 
@Entity
@WithApiFreeTextSearchByFields({
  "name",
  "email",
  "phoneNumber"
})
public class User {
    @Id
    @GeneratedValue 
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
}
``` 
Which would generate the following endpoint:  
```java 
@... 
public class UserGraphQLService {

  /* ... */
  @GraphQLQuery
  public Page < User > userFreeTextSearch(FreeTextSearchPageRequest input) {
    if (input.getSortBy() == null) {
      input.setSortBy("id");
    }
    return apiLogic.freeTextSearch(input);
  }
}  
```  
	 
  
#### Customization
The default Apifi implementation for free text search utilizes JPQL LIKE statements. This is usually fine for small datasets where the requirements are simple and do not need to scale. However since this approach is typically insufficient for larger datasets in projects with more complex free text search requirements, Apifi allows for custom implementations of the free text search. 

This can be utilized by overriding the `executeCustomFreeTextSearch` method present in all [`ApiHooks`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiHooks.java) implementations. The method takes in a [`FreeTextSearchPageRequest`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/FreeTextSearchPageRequest.java) argument, as well as a [`DataManager<T>`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/service/DataManager.java) argument. It returns a [`Page<T>`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/Page.java) object. `executeCustomFreeTextSearch` leaves the implementation of all free text search functionality - including pagination, in the hands of the developer.
  
### Entity Collections  
Collections are unique in that they are not "assigned" wholesale - they're instead **associated with** new items, which may later be **removed from** them. As such, some specialized endpoints are required in order to work with them. In order to expose endpoints for an entity collection, annotate the field with the `@EntityCollectionApi` annotation. This annotation takes in several arguments, as follows:  
1. `EntityCollectionEndpointType[] endpoints()` - [`EntityCollectionEndpointType`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/EntityCollectionEndpointType.java) is an ENUM comprising four types of embedded collection api endpoints; `ASSOCIATE_WITH, REMOVE_FROM`, `PAGINATED_BATCH` and `PAGINATED_FREE_TEXT_SEARCH`.  This argument denotes which endpoints should be generated.
2. `String[] freeTextSearchFields()` - If `PAGINATED_FREE_TEXT_SEARCH` was specified as an endpoint, this argument denotes which fields the entity should be searchable by.  
3. `Class<? extends EntityCollectionApiHooks> apiHooks()` - This serves a similar purpose to the `ApiHooks<T>` interface described above. It enables custom business logic to be "hooked" before and / or after CRUD operations. To use, create a public class which implements the [`EntityCollectionApiHooks<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/EntityCollectionApiHooks.java) interface , and pass in the class type token as the argument for this parameter. The class must be wired into the application context (using `@Component`/`@Service`/etc.).  
4. `boolean associatePreExistingOnly()` - This parameter specifies whether the `ASSOCIATE_WITH` endpoint should ensure that instances being added to the collection are already present in the database, and defaults to `false` if not set.  
  
*Example:*  
In order to demonstrate this feature, a new Entity type `Post` with a `@ManyToOne` relationship to `User` will be created as follows:  
  
```java  
@Entity
public class Post {
    @Id
    @GeneratedValue 
    private Long id;
    @ManyToOne 
    private User user;
    private String content;
}  
```  
`User` will be modified as follows:  
```java  
@Entity
public class User {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    @OneToMany(mappedBy = "user")
    private Set<Post> posts;
}
```  
  
As mentioned above, [`@EntityCollectionApi(...)`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/EntityCollectionApi.java) can generate five types of [`CollectionEndpointType`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/CollectionEndpointType.java):  
  
1. ASSOCIATE_WITH:  
   *Overview:*  
  Associates a list of inputted entity instances with a collection.  
   *Input:*  
 - `owner`: The instance containing the collection to be added to. Must include ID.   
   - `input`: The list of items to be added.  
  
   *Output:*  
  The newly added items.  
2. REMOVE_FROM:  
   *Overview:*  
  Remove a list of items from a collection. This does not actually perform any deletions, it merely removes the references from the collection. In order to remove the items from the database, either cascading or an appropriate method within [`ApiHooks<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiHooks.java) should be utilized.
3. PAGINATED_BATCH:  
   *Overview and output:*   
 Same as the above GET_PAGINATED_BATCH endpoint, but for the fact that the instances are all accessed *within the context of* the entity which has the collection.  
   *Input:*  
 - `owner`: The instance containing the collection to be added to. Must include ID.   
   - `PageRequest`: See GET_PAGINATED_BATCH  
4. PAGINATED_FREE_TEXT_SEARCH:  
    *Overview and output:*   
 Same as the above `@WithApiFreeTextSearchByFields(...)` endpoint, but for the fact that the instances are all accessed *within the context of* the entity which has the collection.  
   *Input:*  
 - `owner`: The instance containing the collection. Must include ID.   
   - [`FreeTextSearchPageRequest`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/FreeTextSearchPageRequest.java): See free text search section.  
  
*Example:*  
```java  
@Entity
public class User {
    @Id
    @GeneratedValue 
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    @EntityCollectionApi(endpoints = {
    ASSOCIATE_WITH,
    REMOVE_FROM,
    PAGINATED_BATCH
    })
    @OneToMany(mappedBy = "user") 
    private Set<Post> posts;
}
```  
Which generates the following endpoints:  
```java  
@...  
public class UserGraphQLApiService {
    //...  
    
    @GraphQLMutation
    public List < Post > associatePostsWithUser(User owner, List < Post > input) {
    return apiLogic.associateWithEntityCollection(owner, "posts", input, postsDataManager, null);
    }
    
    @GraphQLMutation 
    public List < Post > removePostsFromUser(User owner, List < Post > input) {
    return apiLogic.removeFromEntityCollection(owner, "posts", input, null);
    }
    
    @GraphQLQuery 
    public Page < Post > postsOfUser(User owner, PageRequest input) {
    return apiLogic.getPaginatedBatchInEntityCollection(owner, input, "posts", postsDataManager, null);
    }
} 
```  
*Note:* 
The final argument in all four of the above method calls to `apiLogic(...)` is `null`. That is because no class type token for an `EntityCollectionApiHooks<Post, User>` implementation has been specified.  
  
### Element Collections 
In the event of a collection of primitive / embedded types annotated with the `@ElementCollection` annotation, the [`@ElementCollectionApi(...)`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/ElementCollectionApi.java) annotation can be used to generate the following [`ElementCollectionEndpointType`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/ElementCollectionEndpointType.java) GraphQL endpoints:   
1. ADD_TO:     
*Overview:*    
 Adds a list of inputted values to a collection.      
   *Input:*   
- `owner`: The instance containing the collection to be added to. Must include ID.    
   - `input`: The list of items to be added.    
  
   *Output:*   
 The newly added items.   
2. REMOVE__FROM    
*Overview:*    
 Remove a list of items from a collection.     
   *Input:*   
- `owner`: The instance containing the collection to be removed from. Must include ID.   
   - `input`: The list of items to be removed.  
  
   *Output:*   
 The deleted items.   
3. PAGINATED__BATCH_        
*Overview and output:*    
 Get a paginated batch of items within the collection.       
   *Input:*   
- `owner`: The instance containing the collection. Must include ID.   
   - `PageRequest`: See GET_PAGINATED_BATCH   
4. PAGINATED__FREE__TEXT_SEARCH        
*Overview and output:*    
 Get a paginated batch of items within the collection using free text search.      
   *Input:*   
- `owner`: The instance containing the collection. Must include ID.   
   - [`FreeTextSearchPageRequest`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/FreeTextSearchPageRequest.java): See free text search section.  
     
### Element collection maps 
Given a field of type `Map<K, V>` which is annotated as an `@ElementCollection`, the [`@MapElementCollectionApi`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/MapElementCollectionApi.java) annotation can be utilized to generate the following [`MapElementCollectionEndpointType`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/MapElementCollectionEndpointType.java) endpoints:  
1. PUT_ALL       
*Overview:*      
 Adds a list of inputted key-value pairs to a map.       
   *Input:*   
- `owner`: The instance containing the map to be added to. Must include ID.   
   - `input`: The list of key-value items to be added.   
  
   *Output:*   
 The newly added key-value items.   
2. REMOVE_ALL         
*Overview:*         
 Remove a list of key-value pairs from a map.       
   *Input:*  
 - `owner`: The instance containing the map to be removed from. Must include ID.   
   - `input`: The list of keys whos corresponding values are to be removed from the map.  
  
   *Output:*   
 The deleted key-value pairs.   
3. PAGINATED__BATCH__      
*Overview and output:*      
 Get a paginated batch of key-value pairs within the map.      
   *Input:*   
- `owner`: The instance containing the map. Must include ID.   
   - [`PageRequest`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/PageRequest.java): See GET_PAGINATED_BATCH. The only difference is that the return value is a page whos content field is a map.  
4. PAGINATED__FREE__TEXT__SEARCH     
*Overview and output:*      
 Get a paginated batch of items within the map using free text search **on the keys set** (and not the values set).     
   *Input:*   
- `owner`: The instance containing the map. Must include ID.   
   - [`FreeTextSearchPageRequest`](https://github.com/sanda-dev/datafi/blob/master/src/main/java/dev/sanda/datafi/dto/FreeTextSearchPageRequest.java): See free text search section.  

### GraphQL Subscriptions
#### Overview
In addition to queries and mutations, GraphQL supports a third operation type: subscriptions. Like queries, subscriptions enable the fetching of data. Unlike queries, subscriptions are long-lasting operations that can change their result over time. They can maintain an active connection to the GraphQL server, enabling the server to push updates to the subscription's result. Subscriptions are useful for notifying clients in real time about changes to back-end data, such as the creation of a new object or updates to an important field.  

Apifi offers comprehensive out of the box support for GraphQL subscriptions, managing all aspects of server-client pub-sub behind the scenes. There are built in subscriptions which can be added to the GraphQL schema via annotations (as is the case with queries and mutations), as well as a `GraphQLSubscriptionsService<T>` bean which can be autowired into any custom defined spring component and used for custom subscriptions. 

#### Built in endpoints
In order to create entity level subscriptions, the `@WithSubscriptionEndpoints(...)` annotation can be used, as follows:
```java
@Entity
@Getter @Setter
@WithSubscriptionEndpoints({
  ON_CREATE, // will return a list of newly created user(s) after creation
  ON_UPDATE, // takes a list of users to monitor as input, returning an updated user every time a member of that list is updated
  ON_DELETE, // takes a list of users to monitor as input, returning a deleted user object every time a member of that list is deleted
  ON_ARCHIVE, // takes a list of users to monitor as input, returning an archived user object every time a member of that list is archived
  ON_DE_ARCHIVE // takes a list of users to monitor as input, returning a de-archived user object every time a member of that list is de-archived
})
public class User implements Archivable {
  @Id @GeneratedValue
  private Long id;
  private Boolean isArchived = false;
  private String name;
  private String username;
  private String phoneNumber;
  @OneToMany(mappedBy = "user", cascade = ALL)
  private Set<Post> posts;
}
```
To create entity collection subscriptions, the following parameters may be specified in the `@EntityCollectionApi(...)`  annotation:
```java
@Entity
@Getter @Setter
@WithSubscriptionEndpoints(...)
public class User implements Archivable {
  @Id @GeneratedValue
  private Long id;
  private Boolean isArchived = false;
  private String name;
  private String username;
  private String phoneNumber;
  @EntityCollectionApi(
    subscriptions = {
      ON_ASSOCIATE_WITH, // takes in a user to monitor as input, returns any new posts which are added to the annotated collection
      ON_REMOVE_FROM // takes in a user to monitor as input, returns any new posts which are removed from the annotated collection
    }
  )
  @OneToMany(mappedBy = "user", cascade = ALL)
  private Set<Post> posts;
}
```
All subscription endpoints accept an optional `FluxSink.OverflowStrategy` argument as their last parameter. If not specified this defaults to `FluxSink.OverflowStrategy.BUFFER`. More information about `org.reactivestreams` `FluxSink.OverflowStrategy` can be read [here](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/FluxSink.OverflowStrategy.html).

#### Custom subscriptions
In order to create custom subscriptions, the `GraphQLSubscriptionsService<T>` service can be used as follows. We'll take a simple example; we want to create a subscription to an event whereby a users phone number has been updated.

First, the subscription endpoint:
```java
@Service 
@GraphQLComponent
public class CustomUserSubscriptionsService{

    @Autowired
    private GraphQLSubscriptionsService<User> userGraphQLSubscriptionsService;

    @GraphQLSubscription
    public Flux<User> onUserPhoneNumberUpdated(User toObserve){
        final String topic = 
	        "User with id#" + toObserve.getId() + " updated phoneNumber";
        return userGraphQLSubscriptionsService.generatePublisher(topic);
    }
}
```
Second, we'll implement the `Apihooks<T>` class for `User` in order to publish these events as they occur:
```java
@Service
public class UserApiHooks implements ApiHooks<User> {

  @Autowired
  private GraphQLSubscriptionsService<User> userGraphQLSubscriptionsService;

  @Override
  public void postUpdate(
    User originalInput, // the deserialized user json object "as is" from the API call
    User toUpdate, // a copy of the corresponding user object from the DB, prior to being updated
    User updated, // the final updated user object which has been saved to the DB
    DataManager<User> dataManager // think of DataManager<T> as an enhanced JpaRepository
  ) {
    if (originalInput.getPhoneNumber() != null) { // if the input to update included a new phone number
      final String topic =
        "User with id#" + updated.getId() + " updated phoneNumber";
      userGraphQLSubscriptionsService.publishToTopic(topic, updated);
    }
  }
}
```
#### Advanced configuration properties  for GraphQL subscriptions
- `apifi.subscriptions.ws.enabled` - specifies whether the Apollo protocol over web sockets for GraphQL subscriptions is enabled. Defaults to `true`.

- `apifi.subscriptions.ws.endpoint` - specifies the endpoint at which the Apollo protocol over web sockets will be made available. Defaults to `/graphql` (i.e. `ws(s)://<domain-url>:<port>/<apifi.subscriptions.ws.endpoint>`).

- `apifi.subscriptions.ws.keepalive.enabled` - specified whether Apollo session keep-alive is enabled. Defaults to `true`.

- `apifi.subscriptions.ws.keepalive.interval-ms` - specifies the scheduled keep-alive intervals at which the server will send a keep-alive message to the client. Defaults to `10000 ms`.

- `apifi.subscriptions.sse-endpoint` - if SSE is being used instead of Apollo protocol over web sockets for the purpose of serving GraphQL subscriptions, this property specifies the endpoint at which the SSE session can be initiated. Will default to `/graphql/sse`.

- `apifi.subscriptions.sse.timeout-ms` - the amount of time in MS for an SSE session to remain active absent any activity. Defaults to `-1`, which means "never time out".

- `apifi.subscriptions.sse.timeout-param-enabled` - when sending a GraphQL subscription request to the SSE endpoint, an HTTP GET request is used containing the following parameters: `queryString`, containing the actual GraphQL query string, and optionally a `timeout` parameter denoting what timeout value in ms should be set on the server side SSEEmitter object assigned to the request. This configuration property specifies whether this `timeout` parameter may be sent.

- `apifi.subscriptions.pending-transaction-retry-interval` - when new data is available to be published to one or more waiting GraphQL subscription clients, two things happen simultaneously; a "publish" event containing part of the data is fired off on a separate thread, and the data itself is persisted to the database within the context of a transaction on the original thread. If the server side publishing service receives the event and tries to publish it to the relevant clients prior to the original transaction completing, it will not find the data in the database and will not be able to proceed. Hence one or more retries may be necessary in order to give the original thread enough time to complete the database transaction. This property denotes how many milliseconds should pass between each successive retry attempt. By default, the value is `50 ms`.

- `apifi.subscriptions.pending-transaction-max-retries` - the maximum number of such retries which will be performed prior to logging an error and aborting the publish operation. Defaults to 10.

- `apifi.subscriptions.redis-pubsub-url` - when utilizing Apifis' built in pub-sub support for GraphQL subscriptions in a multi-instance / load balanced environment where events to be published may not necessarily occur on the same instance maintaining the session(s) with the relevant subscriber(s), a message broker is required. One of the most popular is redis, and this property specifies the full connection string (including credentials if relevant) to the relevant redis server which will be used by Apifi as such a pub-sub broker. Defaults to `null`.

- `REDIS_PUBSUB_URL` - identical to the above `apifi.subscriptions.redis-pubsub-url` property, only as an environment variable (this is useful and more secure if the connection string contains credentials). Also defaults to `null`.

### Server side pub-sub configuration

#### In memory pub-sub
By default all pub-sub management is handled in memory. This requires no additional configuration and provides an easy out-of-the-box solution for dev environments.

#### Redis based pub-sub
As described above with respect to the `apifi.subscriptions.redis-pubsub-url` property, Apifi comes with a built in, fully managed and production ready pub sub system auto-configured to leverage redis as a message broker. All that is required in order to make this work is a redis URL connection string. 

By default, Apifi will be using this URL to generate a `RedisStandaloneConfiguration` object with which to create a connection factory. However, if a `RedisClusterConfiguration`, `RedisSentinelConfiguration`, `RedisStaticMasterReplicaConfiguration`, or `RedisSocketConfiguration` class is implemented and wired into the spring application context, Apifi will use it instead.

#### Using other message brokers for pub-sub
If redis is not the ideal pub-sub solution for a project, the `CustomPubSubMessagingService` interface can be implemented and wired into the spring application context. This interface can be seen [here](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/graphql_subcriptions/pubsub/CustomPubSubMessagingService.java), and defines the necessary contract for Apifis internal pub-sub management system to make use of any message broker.

### Client side consumption of GraphQL subscriptions
Server side pub-sub configuration isn't very useful without an actual transport protocol through which data can be sent to a subscribing client over an active session. Apifi solves this problem in two distinct ways:

1. A full implementation of the Apollo protocol over web sockets.
2. A server sent events (SSE) based protocol, for which both JS and TS clients are auto-generated at compile time (these can be found in the "Apifi clients" folder within the root project directory after compilation). 

### Spring security integration  
  
Annotation based security is especially well suited to GraphQL APIs given that they operate off of a single endpoint. Apifi supports full integration with spring security by leveraging the following 6 annotations:   
  
- [`@Secured`](https://docs.spring.io/spring-security/site/docs/3.2.8.RELEASE/apidocs/org/springframework/security/access/annotation/Secured.html)  
- [`@RolesAllowed`](https://docs.oracle.com/javaee/7/api/javax/annotation/security/RolesAllowed.html)  
- [`@PreAuthorize`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PreAuthorize.html)  
- [`@PostAuthorize`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PostAuthorize.html)  
- [`@PreFilter`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PreFilter.html)  
- [`@PostFilter`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PostFilter.html).  
  
#### Class level security  
In order to apply a security annotation on the class (i.e. GraphQL service bean) level, the [`@WithServiceLevelSecurity(...)`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/WithServiceLevelSecurity.java) annotation can be used. It takes in any combination of the following seven arguments:  
1. `String secured() default "";`  
2. `String[] rolesAllowed() default "";`  
3. `String preAuthorize() default "";`  
4. `String postAuthorize() default "";`  
5. `String preFilter() default "";`  
6. `String preFilterTarget() default "";`  
7. `String postFilter() default "";`  
  
*Example:*  
```java  
@Entity
@WithServiceLevelSecurity(rolesAllowed = "ROLE_ADMIN")
public class User {
    @Id
    @GeneratedValue 
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    @OneToMany(mappedBy = "user", cascade = ALL) 
    private Set<Post> posts;
}  
```  
  
Would generate the following:  
  
```java  
@...  
@RolesAllowed("ROLE_ADMIN")  
public class UserGraphQLApiService {  
	 ...
 }  
```  
#### Method level security  
If a more granular security strategy is required, spring security annotations can be placed at the method level. In order to do so, the entity class can be annotated with one or more [`@WithMethodLevelSecurity(...)`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/WithMethodLevelSecurity.java) annotations. Each such annotation takes in one or more of the seven parameters described for [`@WithServiceLevelSecurity(...)`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/WithServiceLevelSecurity.java) annotation, as well as a `targets` parameter which takes in an array of [`CRUDEndpoints`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/CRUDEndpoints.java), denoting which auto generated endpoints the specified security annotation(s) should be applied to.  
  
*Example:*  
```java  
@Entity
@WithCRUDEndpoints({CREATE, GET_BY_ID, UPDATE, DELETE})
@WithMethodLevelSecurity(targets = {CREATE,DELETE}, rolesAllowed = "ROLE_ADMIN")
@WithMethodLevelSecurity(targets = {UPDATE,GET_BY_ID}, rolesAllowed = "ROLE_USER")
public class User {
    @Id
    @GeneratedValue 
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
}
```  
  
Would generate the following:  
  
```java  
@...  
public class UserGraphQLApiService {
    
  /* ... */
  
  @GraphQLQuery
  @RolesAllowed("ROLE_USER") 
  public User getUserById(Long input) {
    return apiLogic.getById(input);
  }
  
  @GraphQLMutation
  @RolesAllowed("ROLE_ADMIN") 
  public User createUser(User input) {
    return apiLogic.create(input);
  }
  
  @GraphQLMutation
  @RolesAllowed("ROLE_USER") 
  public User updateUser(User input) {
    return apiLogic.update(input);
  }
  
  @GraphQLMutation
  @RolesAllowed("ROLE_ADMIN") 
  public User deleteUser(User input) {
    return apiLogic.delete(input);
  }
} 
```  
*Note:*   
In the event of a discrepancy, method level security annotations will override class level security annotations.  
  
#### Search endpoints security  
All search endpoints (`@ApiFindBy`, `@ApiFindAllBy`, `@ApiFindByUnique`, `@WithFreeTextSearchFields(...)`) optionally take in the same seven parameters as `@WithServiceLevelSecurity(...)` and `@WithMethodLevelSecurity(...)`, allowing for security policies on a per endpoint basis if need be.  

### Microservices architecture

_This section assumes a working knowledge of [maven modules](https://maven.apache.org/guides/mini/guide-multiple-modules.html)._

Apifi allows for a project to be split across multiple Maven modules. It does this by allowing for all of the annotations described thus far to be applied not only on entity classes themselves, but also on any class directly inheriting from an entity class, provided it's annotated with the `@EntityApiSpec` annotation. This allows for each module to have one or more classes inheriting from whichever entities are relevant to that modules specific requirements, with each inheriting class defining the relevant endpoints. This can also be utilized within a single module project if desired.

#### Defining the endpoints

In order to define an endpoint on a non entity class, the class must inherit from a class annotated with the javax `@Entity` annotation, and itself be annotated with the `@EntityApiSpec` annotation. For example; given the following data Model, defined within a Maven module called  _common_:
```java  
@Entity
public class User {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    @OneToMany(mappedBy = "user", cascade = ALL)
    private Set<Post> posts;
}
```  
```java  
@Entity
public class Post {
    @Id
    @GeneratedValue 
    private Long id;
    @ManyToOne 
    private User user;
    private String content;
}  
```  
And given the following set of project requirements:
1. A maven module called _users_service_, representing a micro-service responsible for user management endpoints (e.g. create user, update user, delete user).
2. An additional maven module called _posts_service_, representing a micro-service responsible for post browsing and management endpoints (e.g. get paginated posts by user, associate new posts with user, update post, remove posts).

In order to fulfill the first requirement, a module called _users_service_ is created. A dependency upon the _common_ module is added within its _pom.xml_ file, and a package called _api_specs_ is created (this naming and package structure is recommended for readability but is not required). Within this package the following class is created:
```java
@EntityApiSpec  
@WithCRUDEndpoints({CREATE, UPDATE, DELETE})
public class UserApiSpec extends User{}
```
In order to fulfill the second requirement, a module called _posts_service_ is created. A dependency upon the _common_ module is added within its _pom.xml_ file, and a package called _api_specs_ is created. Within this package the following classes are created:
```java
@EntityApiSpec  
public class PostsOfUserApiSpec extends User {  
  
 // the entity collection fields getter is overriden and substitutes for the field itself
 @Override  
 @EntityCollectionApi(  
  endpoints = { ASSOCIATE_WITH, GET_PAGINATED__BATCH, REMOVE_FROM }  
)
  public Set<Post> getPosts() {  
    return super.getPosts();  
  }  
}
```
```java
@EntityApiSpec  
@WithCRUDEndpoints(UPDATE)  
public class PostApiSpec extends Post {}
```

#### Required configuration
Given one module which defines GraphQL endpoints (e.g. _users_service_ and _posts_service_ in the above example), which depends upon another module, the module which is depended upon (e.g. _common_ in the above example) must contain a class which is annotated with the `@TransientModule` annotation. This is in order to prevent Apifis annotation processor from generating duplicate spring beans at compile time, which would crash the application at startup. For the above example, something like the following would be added to the _common_ module:
```java
@TransientModule
public class TransientModuleMarker {}
```
This annotation can be placed on any class within the module.

### Testing
The obvious problem with testing components made up of code which is generated at compile time is that they cannot be referenced at compile time. There's obviously no **direct** way to test against code which is unavailable during compilation. This is where [`TestableGraphQLService<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/test_utils/TestableGraphQLService.java) comes into play. 

#### TestableGraphQLService

For each graphql service bean which is auto generated, a corresponding testable replica is also generated. This replica is can be located in the *target/generated-sources/annotations/...your package structure here.../testable_service* directory after compilation. It contains all of the same publicly declared methods as the original `...GraphQLService` class, but has 3 key differences:
1. Naming convention - It has the same name as the original with the prefix "Testable".
2. It implements the [`TestableGraphQLService<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/test_utils/TestableGraphQLService.java) interface - This is what allows us to leverage springs `@Autowired` annotation to get a runtime reference to the bean.
3. It's not annotated as `@Transactional` - Tests are typically run as transactions, and seeing as JPA doesn't support nested transactions this is actually quite crucial in order to prevent unexpected behavior while testing. 

#### Example

##### Model
```java
@Entity
@WithCRUDEndpoints({CREATE, GET_BY_ID, UPDATE, DELETE})  
public class User {  
    @Id  
    @GeneratedValue
    private Long id;
    private String name;  
    private String username;  
    private String phoneNumber;  
}
```  


  ##### Actual GraphQL service to be included in schema
```java
@Service
@Transactional
public class UserGraphQLApiService {
  /* ... internal logic ... */
  @GraphQLQuery
  public User getUserById(Long input) {
    return apiLogic.getById(input);
  }

  @GraphQLMutation
  public User createUser(User input) {
    return apiLogic.create(input);
  }

  @GraphQLMutation
  public User updateUser(User input) {
    return apiLogic.update(input);
  }

  @GraphQLMutation
  public User deleteUser(User input) {
    return apiLogic.delete(input);
  }
}
```

  ##### Testable GraphQL service class which is **not** exposed as part of the schema

```java
@Service
public class TestableUserGraphQLApiService implements TestableGraphQLService<User> {

  /* ... internal logic ...*/

  @GraphQLQuery
  public User getUserById(Long input) {
    return apiLogic.getById(input);
  }

  @GraphQLMutation
  public User createUser(User input) {
    return apiLogic.create(input);
  }

  @GraphQLMutation
  public User updateUser(User input) {
    return apiLogic.update(input);
  }

  @GraphQLMutation
  public User deleteUser(User input) {
    return apiLogic.delete(input);
  }
}
```
##### Example Test class for `UserGraphQLApiService`
```java
@Transactional
@SpringBootTest
@RunWith(SpringRunner.class)
class UserGraphQLApiServiceTest {
    
    @Autowired
    private TestableGraphQLService<User> testApi;
    @Autowired
    private JpaRepository<User, Long> userRepo;

	// helpers
	
    private User generateMockUser(boolean saveUser) {
	    // from java-faker library
        Faker faker = new Faker();
        User user = new User();
        user.setName(faker.name().fullName());
        user.setPhoneNumber(faker.phoneNumber().cellPhone());
        user.setUsername(faker.name().username());
        if(saveUser)
            user = userRepo.save(user);
        return user;
    }
    private User generateMockUser() {
        return generateMockUser(false);
    }

	//tests
	
    @Test
    void getUserById() {
        // case 1 - user is found
        User user = generateMockUser(true);
        Long id = user.getId();
        User fetchedUser = testApi.invokeEndpoint("getUserById", id);
        assertEquals(user, fetchedUser);

        //case 2 - user isn't found
        Long badId = -1;
        assertThrows(RuntimeException.class, () -> testApi.invokeEndpoint("getUserById", badId));
    }

    @Test
    void createUser() {
        final String methodName = "createUser";
        // case 1 - user input is valid
        User userToCreate = generateMockUser();
        User newlyCreatedUser = testApi.invokeEndpoint(methodName, userToCreate);
        assertEquals(userToCreate, newlyCreatedUser);
        User newlyCreatedUserLoadedFromDataBase = userRepo.findById(newlyCreatedUser.getId()).orElse(null);
        assertEquals(userToCreate, newlyCreatedUserLoadedFromDataBase);
        // case 2 - user input is null
        assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> testApi.invokeEndpoint(methodName, new Object[]{null})
        );
    }

    @Test
    void updateUser() {
        final String methodName = "updateUser";
        // case 1 - user input is valid
        Faker faker = new Faker();
        User original = generateMockUser(true);
        User toUpdate = new User();
        toUpdate.setId(original.getId());
        toUpdate.setName(faker.lordOfTheRings().character());
        toUpdate.setUsername(faker.lordOfTheRings().character());
        toUpdate.setPhoneNumber(faker.phoneNumber().phoneNumber());
        assertNotEquals(original, toUpdate);
        User updatedUser = testApi.invokeEndpoint(methodName, toUpdate);
        assertEquals(toUpdate, updatedUser);
        // case 2 - user input is null
        assertThrows(IllegalArgumentException.class, () -> testApi.invokeEndpoint(methodName, new Object[]{null}));
    }

    @Test
    void deleteUser() {
        User user = generateMockUser(true);
        User deletedUser = testApi.invokeEndpoint("deleteUser", user);
        assertEquals(user, deletedUser);
        assertFalse(userRepo.existsById(user.getId()));
    }
}
```

### GraphQL Client  
  
  
#### Overview  
  
While generating the back end GraphQL API, Apifi simultaneously generates a simple, ultra-lightweight and easy to use front end GraphQL client. This client is a single JavaScript file called *apifiClient.js*, which is written to the root project directory at compile time. *apifiClient.js* is made up of pure JavaScript with no external dependencies, relying solely on the *fetch* API in order to send and receive data from the back end API.   
  
#### What it looks like  
Given the following example model:  
```java  
@Entity
@WithCRUDEndpoints({CREATE, GET_BY_ID})
public class User {
    @Id
    @GeneratedValue 
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
}
```  
The corresponding *apifiClient.js* would look as follows:  
```javascript  
let apiUrl = location.origin + '/graphql';  
let includeCredentials = false;  
let bearerToken;  
  
  
  
// project specific client side API calls  
  
export default{  
  
   setBearerToken(token){  
      bearerToken = token;  
   },  
  
   setApiUrl(url){  
      apiUrl = url;  
   },  
  
   setIncludeCredentials(value){  
      includeCredentials = value;  
   },  
  
   async getUserById(input, selectionGraph, customHeaders){  
         let requestHeaders = { "Content-Type": "application/json" }  
         if(customHeaders) requestHeaders = Object.assign({}, requestHeaders, customHeaders);  
         if(bearerToken) requestHeaders["Authorization"] = bearerToken;  
         const requestInit = {  
            method: "POST",  
            credentials: !!includeCredentials ? 'include' : 'omit',  
            headers: requestHeaders,  
            body: JSON.stringify({  
               query: `query getUserById($input: Long) { getUserById(input: $input)${selectionGraph} }`,   
               variables: {  
                  "input": input  
               }  
            })  
         };  
         return await (await fetch(apiUrl, requestInit)).json();  
   },  
  
   async createUser(input, selectionGraph, customHeaders){  
         let requestHeaders = { "Content-Type": "application/json" }  
         if(customHeaders) requestHeaders = Object.assign({}, requestHeaders, customHeaders);  
         if(bearerToken) requestHeaders["Authorization"] = bearerToken;  
         const requestInit = {  
            method: "POST",  
            credentials: !!includeCredentials ? 'include' : 'omit',  
            headers: requestHeaders,  
            body: JSON.stringify({  
               query: `mutation createUser($input: UserInput) { createUser(input: $input)${selectionGraph} }`,   
               variables: {  
                  "input": input  
               }  
            })  
         };  
         return await (await fetch(apiUrl, requestInit)).json();  
   },  
  
}
```  
The file starts off with the `apiUrl` (defaults to `${window.location.origin}/graphql`) and the `bearerToken`, along with their corresponding setters. It then has a corresponding method for each GraphQL endpoint on the back end. To use; import the *apifiClient.js* file, call the relevant method with whichever variables may be required, as well as the GraphQL response format, and the API stack is good to go.  

In addition, a Typescript client is also generated. This includes all of the functionality of the JS client, plus auto-generated interfaces for all data model entities and of course all function arguments and return values are typed. In this case, it looks as follows:
```ts
let apiUrl = location.origin + '/graphql';  
let includeCredentials = false;  
let bearerToken: string;  
  
  
  
// project specific client side API calls  
  
export default{  
  
   setBearerToken(token: string): void{  
      bearerToken = token;  
   },  
  
   setApiUrl(url: string): void{  
      apiUrl = url;  
   },  
  
   setIncludeCredentials(value: boolean): void{  
      includeCredentials = value;  
   },  
  
   async getUserById(input: User, selectionGraph: string, customHeaders?: Dictionary<string>): Promise<ExecutionResult<User>>{  
         let requestHeaders = { "Content-Type": "application/json" }  
         if(customHeaders) requestHeaders = Object.assign({}, requestHeaders, customHeaders);  
         if(bearerToken) requestHeaders["Authorization"] = bearerToken;  
         const requestInit: RequestInit = {  
            method: "POST",  
            credentials: !!includeCredentials ? 'include' : 'omit',  
            headers: requestHeaders,  
            body: JSON.stringify({  
               query: `query getUserById($input: Long) { getUserById(input: $input)${selectionGraph} }`,   
               variables: {  
                  "input": input  
               }  
            })  
         };  
         return await (await fetch(apiUrl, requestInit)).json();  
   },  
  
   async createUser(input: User, selectionGraph: string, customHeaders?: Dictionary<string>): Promise<ExecutionResult<User>>{  
         let requestHeaders = { "Content-Type": "application/json" }  
         if(customHeaders) requestHeaders = Object.assign({}, requestHeaders, customHeaders);  
         if(bearerToken) requestHeaders["Authorization"] = bearerToken;  
         const requestInit: RequestInit = {  
            method: "POST",  
            credentials: !!includeCredentials ? 'include' : 'omit',  
            headers: requestHeaders,  
            body: JSON.stringify({  
               query: `mutation createUser($input: UserInput) { createUser(input: $input)${selectionGraph} }`,   
               variables: {  
                  "input": input  
               }  
            })  
         };  
         return await (await fetch(apiUrl, requestInit)).json();  
   },  
  
}  
  
// project specific data model  
  
export interface User{  
   phoneNumber?: string;  
   isArchived?: boolean;  
   name?: string;  
   id?: number;  
   username?: string;  
}  


  
// Apifi utils object model  
  
// represents a subset of the overall data, corresponding to server side JPA pagination  
export interface Page<T>{  
   content?: Array<T>;  
   totalPagesCount?: number;  
   totalItemsCount?: number;  
   pageNumber?: number;  
   customValues?: Map<string, any>;  
}  
  
// input to specify desired pagination parameters  
export interface PageRequest{  
   pageNumber?: number;  
   sortBy?: string;  
   pageSize?: number;  
   sortDirection?: SortDirection;  
   fetchAll?: boolean;  
}  
  
// input to specify desired pagination parameters, as well as a string value for server side free text search  
export interface FreeTextSearchPageRequest extends PageRequest{  
   searchTerm: string;  
}  
  
// enum type to specify pagination sort ordering  
export enum SortDirection{  
   ASC = 'ASC',  
   DESC = 'DESC'  
}  
  
// a wrapper around any return value from the GraphQL server  
export interface ExecutionResult<T>{  
   data?: T;  
   errors?: Array<ExecutionResultError>;  
}  
  
export interface ExecutionResultError{  
   message: string;  
   path?: Array<string>;  
   locations?: Array<ExecutionResultErrorLocation>;  
   extensions?: Map<string, any>;  
}  
  
export interface ExecutionResultErrorLocation{  
   line: number;  
   column: number;  
}  
  
// for custom headers to attach to query or mutation HTTP requests  
export interface Dictionary<T>{  
   [Key: string]: T;  
}
```
  
### Logs  
Server CRUD events are logged in real time using *Sl4j*.  
  
  
### Known issues  
1. Occasional null pointer exception at compile time. The fix is to delete the *target* folder and try again. This happens with intellij build, not `mvn compile`. 
2. *apifiClient.js* not generated by intellij build. The fix is to run `mvn compile`.  
  
#### That's all for now, happy coding!  
  
### Credit  
This project relies heavily on the awesome [SPQR](https://github.com/leangen/graphql-spqr) java-graphql library, so a big thanks to all of its contributors. A copy of their licence can be found [here](https://github.com/leangen/graphql-spqr/blob/master/LICENSE).  
### License  
Apache 2.0
