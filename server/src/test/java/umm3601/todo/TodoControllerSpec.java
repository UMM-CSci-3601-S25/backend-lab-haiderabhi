package umm3601.todo;

//import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
//import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
//import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonMappingException;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
//import io.javalin.validation.BodyValidator;
import io.javalin.validation.Validation;
//import io.javalin.validation.ValidationError;
//import io.javalin.validation.ValidationException;
import io.javalin.validation.Validator;
//import umm3601.user.UserController;

/**
 * Tests the logic of the UserController
 *
 * @throws IOException
 */

 @SuppressWarnings({ "MagicNumber" })
 class TodoControllerSpec {

   // An instance of the controller we're testing that is prepared in
   // `setupEach()`, and then exercised in the various tests below.
   private TodoController todoController;

   // A Mongo object ID that is initialized in `setupEach()` and used
   // in a few of the tests. It isn't used all that often, though,
   // which suggests that maybe we should extract the tests that
   // care about it into their own spec file?
   private ObjectId samsId;

   // The client and database that will be used
   // for all the tests in this spec file.
   private static MongoClient mongoClient;
   private static MongoDatabase db;


   @Mock
   private Context ctx;

   @Captor
   private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;

   @Captor
   private ArgumentCaptor<Todo> todoCaptor;

   @Captor
   private ArgumentCaptor<Map<String, String>> mapCaptor;

   /**
    * Sets up (the connection to the) DB once; that connection and DB will
    * then be (re)used for all the tests, and closed in the `teardown()`
    * method. It's somewhat expensive to establish a connection to the
    * database, and there are usually limits to how many connections
    * a database will support at once. Limiting ourselves to a single
    * connection that will be shared across all the tests in this spec
    * file helps both speed things up and reduce the load on the DB
    * engine.
    */
   @BeforeAll
   static void setupAll() {
     String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

     mongoClient = MongoClients.create(
         MongoClientSettings.builder()
             .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
             .build());
     db = mongoClient.getDatabase("test");
   }

   @AfterAll
   static void teardown() {
     db.drop();
     mongoClient.close();
   }

   @BeforeEach
   void setupEach() throws IOException {
     // Reset our mock context and argument captor (declared with Mockito
     // annotations @Mock and @Captor)
     MockitoAnnotations.openMocks(this);

     // Setup database
     MongoCollection<Document> todoDocuments = db.getCollection("todos");
     todoDocuments.drop();
     List<Document> testTodos = new ArrayList<>();
     testTodos.add(
         new Document()
             .append("owner", "Chris")
             .append("status", true)
             .append("body", "UMM")
             .append("category", "homework"));
     testTodos.add(
         new Document()
             .append("owner", "Pat")
             .append("status", true)
             .append("body", "IBM")
             .append("category", "work"));
     testTodos.add(
         new Document()
             .append("owner", "Jamie")
             .append("status", true)
             .append("body", "OHMNET")
             .append("category", "project"));

     samsId = new ObjectId();
     Document sam = new Document()
         .append("_id", samsId)
         .append("owner", "Sam")
         .append("status", false)
         .append("body", "OHMNET")
         .append("category", "software design");

     todoDocuments.insertMany(testTodos);
     todoDocuments.insertOne(sam);

     todoController = new TodoController(db);
   }

   @Test
   void addsRoutes() {
     Javalin mockServer = mock(Javalin.class);
     todoController.addRoutes(mockServer);
     verify(mockServer, Mockito.atLeast(2)).get(any(), any());
   }

   @Test
   void canGetAllUsers() throws IOException {
     // When something asks the (mocked) context for the queryParamMap,
     // it will return an empty map (since there are no query params in
     // this case where we want all users).
     when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

     // Now, go ahead and ask the userController to getUsers
     // (which will, indeed, ask the context for its queryParamMap)
    todoController.getTodos(ctx);

     // We are going to capture an argument to a function, and the type of
     // that argument will be of type ArrayList<User> (we said so earlier
     // using a Mockito annotation like this):
     // @Captor
     // private ArgumentCaptor<ArrayList<User>> userArrayListCaptor;
     // We only want to declare that captor once and let the annotation
     // help us accomplish reassignment of the value for the captor
     // We reset the values of our annotated declarations using the command
     // `MockitoAnnotations.openMocks(this);` in our @BeforeEach

     // Specifically, we want to pay attention to the ArrayList<User> that
     // is passed as input when ctx.json is called --- what is the argument
     // that was passed? We capture it and can refer to it later.
    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

     // Check that the database collection holds the same number of documents
     // as the size of the captured List<User>
       assertEquals(
        db.getCollection("todos").countDocuments(),
        todoArrayListCaptor.getValue().size());
   }

   @Test
   void canGetTodosWithLimit() throws IOException {
   Map<String, List<String>> queryParams = new HashMap<>();
   Integer limit = 2;
   String limitString = limit.toString();

   queryParams.put(TodoController.LIMIT_KEY, Arrays.asList(new String[] {limitString}));
   when(ctx.queryParamMap()).thenReturn(queryParams);

   // Create a validator that confirms that when we ask for the value associated with
   // `LIMIT_KEY` _as an integer_, we get back the integer value 2.
   Validation validation = new Validation();
   Validator<Integer> validator = validation.validator(TodoController.LIMIT_KEY, Integer.class, limitString);
   when(ctx.queryParamAsClass(TodoController.LIMIT_KEY, Integer.class)).thenReturn(validator);
   when(ctx.queryParam(TodoController.LIMIT_KEY)).thenReturn(limitString);

   todoController.getTodos(ctx);
   verify(ctx).json(todoArrayListCaptor.capture());
   verify(ctx).status(HttpStatus.OK);

   // Confirm that there are only 2 values returned since we limited to 2.
   assertEquals(2, todoArrayListCaptor.getValue().size());
   }


  @Test
  void canGetTodosWithStatusTrue() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();

    String statusString = "complete";

    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {statusString}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(statusString);

    todoController.getTodos(ctx);

    //Validation validation = new Validation();



   verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

   for (Todo todo : todoArrayListCaptor.getValue()) {
    assertTrue(todo.status);
   }
  }

  @Test
  void canGetTodosWithStatusFalse() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();

    String statusStringF = "incomplete";

    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {statusStringF}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(statusStringF);

    todoController.getTodos(ctx);

    //Validation validation = new Validation();



   verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

   for (Todo todo : todoArrayListCaptor.getValue()) {
    assertFalse(todo.status);
   }
  }

@Test
void canGetTodosWithCategory() throws IOException {
  Map<String, List<String>> queryParams = new HashMap<>();
  String categoryString = "homework";
  queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {categoryString}));
  when(ctx.queryParamMap()).thenReturn(queryParams);

Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY, String.class, categoryString);

  when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class)).thenReturn(validator);

  todoController.getTodos(ctx);

  verify(ctx).json(todoArrayListCaptor.capture());
  verify(ctx).status(HttpStatus.OK);

  for (Todo todo : todoArrayListCaptor.getValue()) {
    assertEquals(categoryString, todo.category);
  }


}


  @Test
  void getTodoWithExistentId() throws IOException {
    String id = samsId.toHexString();
    when(ctx.pathParam("id")).thenReturn(id);

    todoController.getTodo(ctx);

    verify(ctx).json(todoCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals("Sam", todoCaptor.getValue().owner);
    assertEquals(samsId.toHexString(), todoCaptor.getValue()._id);
  }

  @Test
  void getTodoWithBadId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested todo id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  @Test
  void getTodoWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested todo was not found", exception.getMessage());
  }

  }

