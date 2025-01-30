package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
//import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
//import com.mongodb.client.result.DeleteResult;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

/**
 * Controller that manages requests for info about todos.
 */
public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODO_BY_ID = "/api/todos/{id}";
  static final String OWNER_KEY= "owner";
  static final String STATUS_KEY = "status";
  static final String BODY_KEY = "body";
  static final String CATEGORY_KEY = "category";

  //private static final String STATUS_REGEX = "^(complete|incomplete)$";
  private static final String CATEGORY_REGEX = "^(video games|homework|groceries|software design)$";
  //public static final String EMAIL_REGEX = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";

  private final JacksonMongoCollection<Todo> todoCollection;

  /**
   * Construct a controller for todos.
   *
   * @param database the database containing todo data
   */
  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single todo
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

    try {
      todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested todo id wasn't a legal Mongo Object ID.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested todo was not found");
    } else {
      ctx.json(todo);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Set the JSON body of the response to be a list of all the todos returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    // All three of the find, sort, and into steps happen "in parallel" inside the
    // database system. So MongoDB is going to find the todos with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<Todo> matchingTodos = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .into(new ArrayList<>());

    // Set the JSON body of the response to be the list of todos returned by the database.
    // According to the Javalin documentation (https://javalin.io/documentation#context),
    // this calls result(jsonString), and also sets content type to json
    ctx.json(matchingTodos);

    // Explicitly set the context status to OK
    ctx.status(HttpStatus.OK);
  }

  /**
   * Construct a Bson filter document to use in the `find` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `age`, `company`, and `role` query
   * parameters and constructs a filter document that will match todos with
   * the specified values for those fields.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of todos
   */
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters

  //  if (ctx.queryParamMap().containsKey(AGE_KEY)) {
  //    int targetAge = ctx.queryParamAsClass(AGE_KEY, Integer.class)
  //      .check(it -> it > 0, "todo's age must be greater than zero; you provided " + ctx.queryParam(AGE_KEY))
  //      .check(it -> it < REASONABLE_AGE_LIMIT,
  //        "todo's age must be less than " + REASONABLE_AGE_LIMIT + "; you provided " + ctx.queryParam(AGE_KEY))
  //      .get();
  //    filters.add(eq(AGE_KEY, targetAge));
  //  }

    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(STATUS_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(STATUS_KEY, pattern));
    }

    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
      String role = ctx.queryParamAsClass(CATEGORY_KEY, String.class)
        .check(it -> it.matches(CATEGORY_REGEX), "todo must have a legal todo category")
        .get();
      filters.add(eq(CATEGORY_KEY, role));
    }

    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }

  /**
   * Construct a Bson sorting document to use in the `sort` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `sortby` and `sortorder` query
   * parameters and constructs a sorting document that will sort todos by
   * the specified field in the specified order. If the `sortby` query
   * parameter is not present, it defaults todo "name". If the `sortorder`
   * query parameter is not present, it defaults to "asc".
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *   used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *  to sort the database collection of todos
   */
  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `sortby` query param (default "name")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "name");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  /**
   * Set the JSON body of the response to be a list of all the todo names and IDs
   * returned from the database, grouped by company
   *
   * This "returns" a list of todo names and IDs, grouped by company in the JSON
   * body of the response. The todo names and IDs are stored in `todoIdName` objects,
   * and the company name, the number of todos in that company, and the list of todo
   * names and IDs are stored in `todoByCompany` objects.
   *
   * @param ctx a Javalin HTTP context that provides the query parameters
   *   used to sort the results. We support either sorting by company name
   *   (in either `asc` or `desc` order) or by the number of todos in the
   *   company (`count`, also in either `asc` or `desc` order).
   */

 /* public void getTodosGroupedByCompany(Context ctx) {
    // We'll support sorting the results either by company name (in either `asc` or `desc` order)
    // or by the number of todos in the company (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "_id");
    if (sortBy.equals("company")) {
      sortBy = "_id";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    // The `todoByCompany` class is a simple class that has fields for the company
    // name, the number of todos in that company, and a list of todo names and IDs
    // (using the `todoIdName` class to store the todo names and IDs).
    // We're going to use the aggregation pipeline to group todos by company, and
    // then count the number of todos in each company. We'll also collect the todo
    // names and IDs for each todo in each company. We'll then convert the results
    // of the aggregation pipeline to `todoByCompany` objects.

    ArrayList<TodoByCompany> matchingTodos = todoCollection
      // The following aggregation pipeline groups todos by company, and
      // then counts the number of todos in each company. It also collects
      // the todo names and IDs for each todo in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("name", 1).append("company", 1)),
          // Group the todos by company, and count the number of todos in each company
          new Document("$group", new Document("_id", "$company")
            // Count the number of todos in each company
            .append("count", new Document("$sum", 1))
            // Collect the todo names and IDs for each todo in each company
            .append("todos", new Document("$push", new Document("_id", "$_id").append("name", "$name")))),
          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to todoGroupResult objects
        // (i.e., a list of todoGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        todoByCompany.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }*/

  /**
   * Add a new todo using information from the context
   * (as long as the information gives "legal" values to todo fields)
   *
   * @param ctx a Javalin HTTP context that provides the todo info
   *  in the JSON body of the request
   */




  /**
   * Utility function to generate an URI that points
   * at a unique avatar image based on a todo's email.
   *
   * This uses the service provided by gravatar.com; there
   * are numerous other similar services that one could
   * use if one wished.
   *
   * YOU DON'T NEED TO USE THIS FUNCTION FOR THE TODOS.
   *

  //String generateAvatar(String email) {
  //  String avatar;
  //  try {
      // generate unique md5 code for identicon
  //    avatar = "https://gravatar.com/avatar/" + md5(email) + "?d=identicon";
  //  } catch (NoSuchAlgorithmException ignored) {
      // set to mystery person
  //    avatar = "https://gravatar.com/avatar/?d=mp";
  //  }
  //  return avatar;
  //}

  /**
   * Utility function to generate the md5 hash for a given string
   *
   * @param str the string to generate a md5 for
   */
  public String md5(String str) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] hashInBytes = md.digest(str.toLowerCase().getBytes(StandardCharsets.UTF_8));

    StringBuilder result = new StringBuilder();
    for (byte b : hashInBytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  /**
   * Setup routes for the `todo` collection endpoints.
   *
   * These endpoints are:
   *   - `GET /api/todos/:id`
   *       - Get the specified todo
   *   - `GET /api/todos?age=NUMBER&company=STRING&name=STRING`
   *      - List todos, filtered using query parameters
   *      - `age`, `company`, and `name` are optional query parameters
   *   - `GET /api/todosByCompany`
   *     - Get todo names and IDs, possibly filtered, grouped by company
   *   - `DELETE /api/todos/:id`
   *      - Delete the specified todo
   *   - `POST /api/todos`
   *      - Create a new todo
   *      - The todo info is in the JSON body of the HTTP request
   *
   * GROUPS SHOULD CREATE THEIR OWN CONTROLLERS THAT IMPLEMENT THE
   * `Controller` INTERFACE FOR WHATEVER DATA THEY'RE WORKING WITH.
   * You'll then implement the `addRoutes` method for that controller,
   * which will set up the routes for that data. The `Server#setupRoutes`
   * method will then call `addRoutes` for each controller, which will
   * add the routes for that controller's data.
   *
   * @param server The Javalin server instance
   * @param todoController The controller that handles the todo endpoints
   */
  public void addRoutes(Javalin server) {
    // Get the specified todo
    server.get(API_TODO_BY_ID, this::getTodo);

    // List todos, filtered using query parameters
    server.get(API_TODOS, this::getTodos);

    // Get the todos, possibly filtered, grouped by company
    //server.get("/api/todosByCompany", this::getTodosGroupedByCompany);

    // Add new todo with the todo info being in the JSON body
    // of the HTTP request


    // Delete the specified todo

  }
}
