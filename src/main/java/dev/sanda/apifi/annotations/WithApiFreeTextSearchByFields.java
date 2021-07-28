package dev.sanda.apifi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * When placed on a field of a JPA Entity annotated class
 * this annotation tells apifi to autogenerate an endpoint
 * for finding all instances of said JPA Entity class
 * by the value of a list of field names provided as arguments.
 *
 * For example:
 * <pre>{@code
 * @Data
 * @Entity
 * public class User {
 *   @Id
 *   @GeneratedValue
 *   private Long id;
 *   private String name;
 *   private String address;
 *   private String phoneNumber;
 *   //...
 * }
 * }</pre>
 * Will translate into the following GraphQL query resolver:
 * <pre>{@Code
 *   @GraphQLQuery
 *   public List<User> findUsersByNames(List<String> names) {
 *     return apiLogic.apiFindAllBy("name", names); // internal logic
 *   }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithApiFreeTextSearchByFields {
  String[] value();

  String secured() default "";

  String[] rolesAllowed() default "";

  String preAuthorize() default "";

  String postAuthorize() default "";

  String preFilter() default "";

  String preFilterTarget() default "";

  String postFilter() default "";
}
