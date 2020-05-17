<img align="center" width="100" height="100" src="https://github.com/sanda-dev/apifi/blob/master/readme%20images/Apifi%20logo.png"/>  
  
# Apifi  
  
- [Introduction](#introduction)
 * [Installation](#installation)
 * [Hello World](#hello-world)
   + [Service layer](#service-layer)
 * [@WithCRUDEndpoints(...)](#-withcrudendpoints--)
 * [Customization](#customization)
 * [Search endpoints](#search-endpoints)
 * [Free text search](#free-text-search)
   + [Overview](#overview)
 * [Collections](#collections)
 * [Spring security integration](#spring-security-integration)
   + [Class level security](#class-level-security)
   + [Method level security](#method-level-security)
   + [Search endpoints security](#search-endpoints-security)
  * [License](#license)
  
## Introduction  
Apifi is a Java 8+ annotation processing framework which auto generates a GraphQL API for PA based data models. It spans the full API stack; from data access to client side consumption. Apifi is centered around one simple goal: To eliminate the need for generic CRUD related boilerplate, *without* compromising on control and customizability.

### Installation
```
<dependency>
  <groupId>dev.sanda</groupId>
    <artifactId>apifi</artifactId>
  <version>0.0.4</version>
</dependency>   
```

### Hello World
```
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
#### Service layer
After compiling the project and taking a peek in the "target" folder, the following is the auto generated GraphQL service bean:
```
@...
public class UserGraphQLApiService {

  /*... various internal logic ...*/
	
  @Autowired  
  private ApiLogic<User> apiLogic;

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
```
*Note:* 
As its name suggests, [`ApiLogic<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiLogic.java) implements API CRUD ops generically. 

### @WithCRUDEndpoints(...)
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
10.  ARCHIVE:
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

```
public List<T> batchCreate(List<T> input) {  
	if(apiHooks != null) // <--- note this
		apiHooks.preBatchCreate(input, dataManager);  
	val result = dataManager.saveAll(input);  
	if(apiHooks != null) // <--- and this
		apiHooks.postBatchCreate(result, dataManager);  
	logInfo("batchCreate: created {} new {}", result.size(),toPlural(dataManager.getClazzSimpleName()));  
	return result;  
}
```
This pattern repeats itself in the same manner in all of the other [`ApiLogic<T>`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiLogic.java) methods, allowing for the execution of custom defined logic prior to or following mutations and queries. In order to make use of this feature for an entity of type `T`:
1. create a public class which implements `ApiHooks<T>`
2. Make sure the class is wired into the application context (`@Component`, etc.).
3. Override whichever hooks are relevant. See the relevant source code [here](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/service/ApiHooks.java).
No further configuration is necessary - Apifi leverages spring dependency injection to determine whether such a class has been created for a given entity. If such a service bean has indeed been created - it will be picked up and used by `ApiLogic<T>`. 

Using the previous example:
```
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
Imagine there is a requirement to print a welcome message to the console after every time a new user is added, and a goodbye message after every time a user is deleted. To do so, a custom implementation of `ApiHooks<T>` could be implemented as follows:
```
@Service
public class UserApiHooks implements ApiHooks<User> {
    @Override
    public void postCreate(User added, DataManager<User> dataManager) {
        System.out.println(String.format("Hello %s!", added.getName()));
    }

    @Override
    public void postDelete(User deleted, DataManager<User> dataManager) {
        System.out.println(String.format("Goodbye %s", deleted.getName()));
    }
}
```
The same workflow applies to less trivial use cases such as third party API calls, data related metrics, etc.

### Search endpoints

In addition to the above CRUD endpoints, additional endpoints can be added by making use of the following annotations:
-  `@ApiFindBy`: To fetch a `List<T>` of instances of type `T`, with the search criteria being the value of a specific field of type `TField`, the field in question can be annotated with the `@ApiFindBy` annotation. 
*Example*: 
    ```
	@Entity
	public class User {
	    @Id
	    @GeneratedValue
	    private Long id;
	    @ApiFindBy // <---
	    private String name;
	    private String email;
	    private String phoneNumber;
	    private String passwordHash;
	}
    ```
    The above `@ApiFindBy` annotation generates the following GraphQL endpoint:
    ```
    @...
    UserGraphQLService{
    ...
	  @GraphQLQuery
	  public List<User> findUsersByName(String name) {
	    return apiLogic.apiFindBy("name", name);
	  }
    ...
    }
    ```
-  `@ApiFindAllBy`: Fetch a list of instances with a field value matching at least one of the inputted values. 
*Example:*
    ```
    @Entity
	public class User {
	    @Id
	    @GeneratedValue
	    private Long id;
	    @ApiFindAllBy // <---
	    private String name;
	    private String email;
	    private String phoneNumber;
	    private String passwordHash;
	}
    ```
    The above `@ApiFindAllBy` would generate the following endpoint:
    ```
    @...
    UserGraphQLService{
    ...
	  @GraphQLQuery
	  public List<User> findUsersByNames(List<String> names) {
	    return apiLogic.apiFindAllBy("name", names);
	  }
    ...
    }
    ```
-  `@ApiFindByUnique`: To fetch a single instance by the value of a unique field, the field can be annotated with the `@ApiFindByUnique` annotation.
*Example*:
    ```
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
    ```
	@...
	UserGraphQLService{
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

*Example:*
```  
@Entity
@WithApiFreeTextSearchByFields({"name", "email", "phoneNumber"})
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
```  
@...  
public class UserGraphQLService{  
//...
  @GraphQLQuery
  public Page<User> userFreeTextSearch(FreeTextSearchPageRequest input) {
    if(input.getSortBy() == null) {
      input.setSortBy("id");
    }
    return apiLogic.freeTextSearch(input);
  }
//...
}  

```

### Collections
`Iterable<T>` collections are unique in that they are not "assigned" per say - they're **associated with**, **updated in**, and **removed from**. As such, some specialized endpoints are required in order to work with them. In order to expose endpoints for an embedded collection, annotate the field with the `@EmbeddedCollectionApi` annotation. This annotation takes in several arguments, as follows:
1. `CollectionEndpointType[] endpoints()` - `CollectionEndpointType` is an ENUM comprising four types of embedded collection api endpoints; `ASSOCIATE_WITH, REMOVE_FROM, UPDATE_IN, and PAGINATED_BATCH`.  This argument delineates which CRUD endpoints should be generated for the embedded collection.
2. `Class<? extends EmbeddedCollectionApiHooks> apiHooks()` - This serves a similar purpose to the `ApiHooks<T>` bean described above. It enables custom business logic to be hooked before and / or after CRUD operations. To use, create a public class which implements the `EmbeddedCollectionApiHooks<T>` interface , and pass in the class type token as the argument for this parameter. The class must be wired into the application context (using `@Component`/`@Service`, etc.).
3. `boolean associatePreExistingOnly()` - This parameter specifies whether the `ASSOCIATE_WITH` endpoint should ensure that instances being added to the collection are already present in the database - defaults to `false` if not set.

*Example:*
In order to demonstrate this feature, a new Entity type `Post` with a `@ManyToOne` relationship to `User` will be created as follows:

```
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
```
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

As mentioned above, [`@EmbeddedCollectionApi(...)`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/annotations/EmbeddedCollectionApi.java) can generate four types of [`CollectionEndpointType`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/CollectionEndpointType.java):

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
3. UPDATE_IN:
	*Overview:*
	Update a list of instances referenced within a collection.
	*Input:* 
	 - `owner`: The instance containing the collection to be added to. Must include ID. 
	- `input`: A list of instances to be updated. These must each include IDs, as well as any fields to be updated.
	*Output:*
	The newly updated instances.
4. PAGINATED_BATCH:
	*Overview and output:* 
	Same as the above GET_PAGINATED_BATCH endpoint, but for the fact that the instances are all accessed *within the context of* the entity which has the collection.
	*Input:*
	- `owner`: The instance containing the collection to be added to. Must include ID. 
	- `PageRequest`: See GET_PAGINATED_BATCH

*Example:*
```
@Entity
public class User {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    @EmbeddedCollectionApi(endpoints = {
            ASSOCIATE_WITH,
            UPDATE_IN,
            REMOVE_FROM,
            PAGINATED_BATCH
    })
    @OneToMany(mappedBy = "user")
    private Set<Post> posts;
}
```
Which generates the following endpoints:
```
@...
public class UserGraphQLApiService {
  
  //...
  
  @GraphQLMutation
  public List<Post> associatePostsWithUser(User owner, List<Post> input) {
    return apiLogic.associateWithEmbeddedCollection(owner, "posts", input, postsDataManager, null);
  }

  @GraphQLMutationThis
  public List<Post> updatePostsInUser(User owner, List<Post> input) {
    return apiLogic.updateEmbeddedCollection(owner, postsDataManager, input, null);
  }

  @GraphQLMutation
  public List<Post> removePostsFromUser(User owner, List<Post> input) {
    return apiLogic.removeFromEmbeddedCollection(owner, "posts", input, null);
  }

  @GraphQLQuery
  public Page<Post> postsInUser(User owner, PageRequest input) {
    return apiLogic.getPaginatedBatchInEmbeddedCollection(owner, input, "posts", postsDataManager, null);
  }
}
```
*Note:* 
The final argument in all four of the above method calls to `apiLogic(...)` is `null`. That is because no class type token for an `EmbeddedCollectionApiHooks<Post, User>` implementation has been specified.

### Spring security integration

Annotation based security is especially well suited to GraphQL APIs given that they operate off of a single endpoint. Apifi supports full integration with spring security by leveraging the following 6 annotations: 

- [`@Secured`](https://docs.spring.io/spring-security/site/docs/3.2.8.RELEASE/apidocs/org/springframework/security/access/annotation/Secured.html)
- [`@RolesAllowed`](https://docs.oracle.com/javaee/7/api/javax/annotation/security/RolesAllowed.html)
- [`@PreAuthorize`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PreAuthorize.html)
- [`@PostAuthorize`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PostAuthorize.html)
- [`@PreFilter`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PreFilter.html)
- [`@PostFilter`](https://docs.spring.io/spring-security/site/docs/4.2.13.BUILD-SNAPSHOT/apidocs/org/springframework/security/access/prepost/PostFilter.html).

#### Class level security
In order to apply a security annotation on the class (i.e. GraphQL service bean) level, the `@WithWithServiceLevelSecurity(...)` annotation can be used. It takes in any combination of the following seven arguments:
1. `String secured() default "";`
2. `String[] rolesAllowed() default "";`
3. `String preAuthorize() default "";`
4. `String postAuthorize() default "";`
5. `String preFilter() default "";`
6. `String preFilterTarget() default "";`
7. `String postFilter() default "";`

*Example:*
```
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

```
@...
@RolesAllowed("ROLE_ADMIN")
public class UserGraphQLApiService {
  ...
}
```
#### Method level security
If a more granular security strategy is required, spring security annotations can be placed at the method level. In order to do so, the entity class can be annotated with one or more `@WithMethodLevelSecurity(...)` annotations. Each such annotation takes in one or more of the seven parameters described for `@WithServiceLevelSecurity(...)` annotation, as well as a `targets` parameter which takes in an array of [`CRUDEndpoints`](https://github.com/sanda-dev/apifi/blob/master/src/main/java/dev/sanda/apifi/generator/entity/CRUDEndpoints.java), delineating which endpoints the specified security annotation(s) should be applied to.

*Note:* 
In the event of a discrepancy, method level security annotations will override class level security annotations

#### Search endpoints security
All search endpoints (`@ApiFindBy`, `@ApiFindAllBy`, `@ApiFindByUnique`, `@WithFreeTextSearchFields(...)`) optionally take in the same seven parameters as `@WithServiceLevelSecurity(...)` and `@WithMethodLevelSecurity(...)`, allowing for security policies on a per endpoint basis if need be.

#### That's all for now, happy coding!
### License
Apache 2.0
