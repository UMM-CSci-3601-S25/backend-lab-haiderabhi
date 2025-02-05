package umm3601.todo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;

//import java.io.IOException;
import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
//import org.jetbrains.annotations.TestOnly;
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
  static final String OWNER_KEY = "owner";
   public static final String STATUS_KEY = "status";
  static final String BODY_KEY = "body";
  public static final String CATEGORY_KEY = "category";
  public static final String LIMIT_KEY = "limit";
  static final String ORDER_SORT_KEY = "order";

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
   *id
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
      .limit(limit(ctx))
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

// Checks if the we search for a word in the body and returns nothing
    if (ctx.queryParamMap().containsKey(BODY_KEY)) {
      String targetContent = ctx.queryParam(BODY_KEY);
      Pattern pattern = Pattern.compile(targetContent, Pattern.CASE_INSENSITIVE);
      filters.add(regex("body", pattern));
    }

    //Checks if the status is complete or incomplete and returns true or false status.
    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      String statusParam = ctx.queryParam(STATUS_KEY);
      boolean targetStatus;
      if (statusParam.equalsIgnoreCase("complete")) {
        targetStatus = true;
      } else if (statusParam.equalsIgnoreCase("incomplete")) {
        targetStatus = false;
      } else {
        throw new BadRequestResponse("Invalid status request");
      }
      filters.add(eq(STATUS_KEY, targetStatus));
    }




    // Filters Todos by the owner name.
    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
    String targetOwner = ctx.queryParam(OWNER_KEY);
      filters.add(regex("owner", Pattern.compile(targetOwner, Pattern.CASE_INSENSITIVE)));
    }


    // Filters Todos by categories.
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
  // what does the code above do?
  //



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
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  //Limiting the number of todos that are displayed

  private int limit(Context ctx) {
    if (ctx.queryParamMap().containsKey(LIMIT_KEY)) {
      int targetLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
      .check(it -> it > 0, "Limit should be greater than 0. You gave" + ctx.queryParam(LIMIT_KEY))
      .get();
      return targetLimit;
    } else {
      return (int) todoCollection.countDocuments();
    }
  }


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
