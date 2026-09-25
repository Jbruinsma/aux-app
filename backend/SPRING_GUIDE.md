# Spring Boot guide for FastAPI developers

Reference for porting `../backend` (FastAPI + SQLAlchemy) to this Spring Boot app.
Everything here is written against this repo's actual code.

---

## 1. The big picture

A Spring Boot app is one Java process with an embedded web server (Tomcat). At startup Spring
scans every class under `com.aux`, finds the ones with special annotations (`@RestController`,
`@Configuration`, repository interfaces, ...), creates one instance of each, and wires them
together by passing them into each other's constructors. You never call `new UserController(...)`
yourself.

```
HTTP request
  -> controller/UserController      (routes: like an APIRouter)
  -> repository/UserRepository      (DB queries: interface only, Spring writes the body)
  -> entity/UserEntity              (one row of the `users` table: like a SQLAlchemy model)
  -> ../backend/aux.db              (same SQLite file the Python backend uses)
```

## 2. Project layout

```
backend-java/
  pom.xml                               dependencies (like requirements.txt)
  mvnw                                  Maven wrapper; downloads Maven on first run
  src/main/resources/application.properties   config (port, DB URL, upload dir)
  src/main/java/com/aux/
    AuxApplication.java                 entry point (main); like `app = FastAPI()`
    config/WebConfig.java               CORS + serving /uploads/** static files
    controller/*Controller.java         routes
    repository/*Repository.java         DB access
    entity/*Entity.java                 table mappings (DB shape)
    dto/*.java                          request/response records (API JSON shape)
```

Python equivalents: `routes/*.py` -> `controller/`, `models.py` -> `entity/`,
`db.py` + session queries -> `repository/`, `config.py` -> `application.properties`,
Pydantic models -> `dto/`.

## 3. Translation table

| FastAPI / SQLAlchemy                          | Spring Boot                                                   |
|-----------------------------------------------|---------------------------------------------------------------|
| `app = FastAPI()` + `uvicorn`                 | `AuxApplication.main()`; Tomcat is built in                   |
| `requirements.txt` / `pip install`            | `pom.xml` / `./mvnw` (downloads automatically)                |
| `config.py`, env vars                         | `application.properties`, read with `@Value("${key}")`        |
| `APIRouter()` mounted at `/api/users`         | class with `@RestController` + `@RequestMapping("/api/users")`|
| `@router.get("/profile/{username}")`          | `@GetMapping("/profile/{username}")`                          |
| `@router.post(...)`                           | `@PostMapping(...)`                                           |
| path param `username: str`                    | `@PathVariable String username`                               |
| query param `q: str = "x"`                    | `@RequestParam(defaultValue = "x") String q`                  |
| body `data: dict`                             | `@RequestBody Map<String, Object> data`                       |
| Pydantic model                                | `record` in `dto/` (section 6)                                |
| body as Pydantic model                        | `@RequestBody LoginRequest req` where `LoginRequest` is a `record` |
| `Form(None)`                                  | `@RequestParam(required = false) String name`                 |
| `UploadFile = File(...)`                      | `@RequestParam MultipartFile file`                            |
| `return {"exists": True}`                     | `return Map.of("exists", true);` (auto-serialized to JSON)    |
| `JSONResponse(x, status_code=404)`            | `return ResponseEntity.status(404).body(x);`                  |
| `FileResponse(path)`                          | handled by `WebConfig.addResourceHandlers` for `/uploads/**`  |
| `Depends(get_db)`                             | constructor parameter; Spring passes it in                    |
| SQLAlchemy model class                        | `@Entity` class                                               |
| `session.query(...).filter_by(...).first()`   | repository method like `findByUsername(...)`                  |
| `session.add(x); session.commit()`            | `repository.save(x)`                                          |
| `session.delete(x)`                           | `repository.delete(x)`                                        |
| `None`                                        | `null`, or better `Optional<T>`                               |
| `dict[str, str]`                              | `Map<String, String>`                                         |
| `list[str]`                                   | `List<String>`                                                |
| `@dataclass`                                  | `record`                                                      |
| Alembic                                       | still Alembic (see section 8)                                 |
| `CORSMiddleware`                              | `WebConfig.addCorsMappings`                                   |

## 4. Dev loop

```bash
cd backend-java
./mvnw spring-boot:run      # starts on port 5000; stop the Python backend first
```

```bash
curl localhost:5000/api/users/check-username/someone
curl -X POST localhost:5000/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"a","password":"b"}'
```

- **No auto-reload.** After editing, Ctrl+C and rerun. (Optional: add the
  `spring-boot-devtools` dependency to get restart-on-compile.)
- **Compile errors show up at startup.** Java is compiled; typos fail before the server starts,
  not when the route is hit. Read the first `[ERROR]` line, it has file and line number.
- **Use IntelliJ.** Open `backend-java/pom.xml` as a project. Run `AuxApplication` with the green
  arrow. Autocomplete and auto-imports (Alt+Enter) remove most of the Java verbosity pain.
- **Just compile, don't run:** `./mvnw compile`.

## 5. Controllers (routes)

The working example in this repo, `controller/UserController.java`:

```java
@RestController                       // this class holds routes; return values become JSON
@RequestMapping("/api/users")         // prefix for every route below
public class UserController {

    private final UserRepository users;

    // Spring sees this constructor needs a UserRepository and passes one in (like Depends).
    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/check-username/{username}")
    public Map<String, Boolean> checkUsernameExists(@PathVariable String username) {
        return Map.of("exists", users.existsByUsername(username));
    }
}
```

### JSON body

```java
@PostMapping("/login")
public Map<String, String> login(@RequestBody Map<String, String> data) {
    String username = data.get("username");   // null if missing, like dict.get()
    ...
}
```

Typed alternative (like a Pydantic model; see section 6 for where records live):

```java
record LoginRequest(String username, String password) {}

@PostMapping("/login")
public Map<String, String> login(@RequestBody LoginRequest req) {
    req.username(); req.password();
}
```

### Multipart form + file upload

```java
@PostMapping("/{username}/update/profile-picture")
public Map<String, String> updatePfp(@PathVariable String username,
                                     @RequestParam("profile_picture") MultipartFile file) throws IOException {
    Path target = uploadsDir.resolve("pfps").resolve(StringUtils.cleanPath(file.getOriginalFilename()));
    file.transferTo(target);
    ...
}
```

Get `uploadsDir` the same way `WebConfig` does: constructor param `@Value("${aux.uploads-dir}") String uploadsDir`.
`StringUtils.cleanPath` (from `org.springframework.util`) plays the role of Werkzeug's
`secure_filename`, but is less strict. Reject names containing `..` or `/` yourself.

### Status codes

Python returns `200` even for error payloads. To match, just return a `Map`. When you actually
need a different status:

```java
return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
```

Return type becomes `ResponseEntity<Map<String, String>>` (or `ResponseEntity<?>`).

## 6. DTOs: typed request/response JSON (Pydantic equivalent)

A Java `record` plays the role of a Pydantic model. Jackson (Spring's JSON library, already
included) converts records to and from JSON automatically. Prefer records over `Map` for new code:
the JSON shape is fixed by the compiler, and only records produce real types in the OpenAPI spec
(see "TypeScript types" below).

### Where they live

```
src/main/java/com/aux/dto/
    UserExistence.java
    RegisterRequest.java
    UserProfile.java
```

- Named `dto` ("data transfer object": the shape that goes over the wire), the conventional
  Spring name. Not `records` (names the language feature, not the role) and not `models`
  (ambiguous with `entity/`; in the Python code "model" meant a DB table).
- `entity/` = DB shape, `dto/` = API shape. Keeping them separate means a DB column change does
  not silently change the API.
- A directory is a package. Every file starts with `package com.aux.dto;` and controllers
  `import com.aux.dto.UserProfile;`. IntelliJ does both: right-click `dto/` > New > Java Class > Record.
- File name must equal the public type name (`UserExistence.java` holds `UserExistence`).
  Rename with Refactor > Rename so both change together.
- One record used by only one controller can also be declared inside that controller class.
- Split into `dto/request/` and `dto/response/` only once `dto/` passes ~20 files.

### Response model

```java
package com.aux.dto;

public record UserExistence(boolean exists) {}
```

```java
@GetMapping("/check-username/{username}")
public UserExistence checkUsernameExists(@PathVariable String username) {
    return new UserExistence(users.existsByUsername(username));
}
```

Returns `{"exists": true}`. Field name = JSON key; Jackson builds the object for you.

**Common mistake:** wrapping a `Map` in the record, or leaving the old return type:

```java
// WRONG: record takes a boolean, not a Map; method still declares Map<String, Boolean>
public Map<String, Boolean> checkUsernameExists(...) {
    return new UserExistence(Map.of("exists", users.existsByUsername(username)));
}
```

Compiler error: `incompatible types: ... java.util.Map<K,V> conforms to boolean`.
Fix: pass the field values directly, and change the method return type to the record.

### Nesting, renaming keys, nulls

```java
public record UserProfile(
        String username,
        @JsonProperty("profile_picture") String profilePicture,   // JSON key differs from Java name
        List<PlaylistSummary> playlists) {}                       // nested records / lists just work

public record PlaylistSummary(String uuid, String name, boolean isPublic) {}
```

```json
{"username":"a","profile_picture":null,"playlists":[{"uuid":"...","name":"x","isPublic":true}]}
```

- `@JsonProperty("snake_name")` renames per field. Don't use the global
  `spring.jackson.property-naming-strategy=SNAKE_CASE`: this API mixes `playlist_uuid` and
  `isPublic`, and the global setting would break the camelCase keys.
- `@JsonInclude(JsonInclude.Include.NON_NULL)` on the record drops null fields (Pydantic's
  `exclude_none`).
- Annotations import from `com.fasterxml.jackson.annotation.*`.

### Request model with validation (Pydantic `Field(...)` constraints)

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

```java
public record RegisterRequest(
        @NotBlank @Size(max = 32) String username,
        @NotBlank @Size(min = 8) String password) {}

@PostMapping("/register")
public Map<String, String> register(@Valid @RequestBody RegisterRequest req) { ... }
```

- Invalid body -> automatic `400` (FastAPI gives `422`).
- Constraints from `jakarta.validation.constraints.*`: `@NotNull`, `@NotBlank`, `@Size`, `@Email`,
  `@Pattern`, `@Min`, `@Max`, ... Nested record fields need `@Valid` too.
- Unknown JSON fields are ignored by default. Pydantic `extra="forbid"` equivalent, in
  `application.properties`: `spring.jackson.deserialization.fail-on-unknown-properties=true`.
- **Porting caution:** the Python routes silently accept missing fields. Adding `@Valid` to a
  ported route changes behavior (frontend starts getting `400`s). Use it on new routes, or on old
  ones only on purpose.

### TypeScript types (FastAPI `/docs` + codegen equivalent)

Add springdoc-openapi to `pom.xml` (the 3.x line targets Spring Boot 4; check its compatibility
table for the exact version):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.0</version>
</dependency>
```

- Spec: `http://localhost:5000/v3/api-docs`. Swagger UI: `http://localhost:5000/swagger-ui.html`.
- Generate TS types:
  ```bash
  npx openapi-typescript http://localhost:5000/v3/api-docs -o frontend/src/api.d.ts
  ```
- Endpoints returning `Map` appear as untyped objects; only record return types give real TS types.

## 7. Repositories (DB queries)

`repository/UserRepository.java`:

```java
public interface UserRepository extends JpaRepository<UserEntity, String> {
    boolean existsByUsername(String username);
}
```

`JpaRepository<UserEntity, String>` = "repository for `UserEntity`, whose primary key type is
`String`". You get these for free: `save`, `findById`, `findAll`, `delete`, `deleteById`,
`existsById`, `count`.

**Derived queries:** Spring generates SQL from the method name. Field names are the *Java*
field names in the entity (`profilePicture`, not `profile_picture`).

```java
Optional<UserEntity> findByUsername(String username);         // WHERE username = ?
List<PlaylistEntity> findByOwnerId(String ownerId);           // WHERE owner_id = ?
List<PlaylistEntity> findByIsPublic(Integer isPublic);
List<MusicPieceEntity> findByPlaylistUuidOrderByPositionAsc(String playlistUuid);
boolean existsByUsername(String username);
long countByOwnerId(String ownerId);

@Transactional                                                 // required for derived deletes
void deleteByUsername(String username);
```

If a method name gets absurd, write the query yourself (JPQL uses entity/field names):

```java
@Query("select p from PlaylistEntity p where p.ownerId = :owner and p.isPublic = 1")
List<PlaylistEntity> publicPlaylistsOf(@Param("owner") String owner);
```

A typo in a derived method name fails at **startup**, with a message naming the bad property.

### Optional

```java
Optional<UserEntity> maybe = users.findByUsername(name);
if (maybe.isEmpty()) return Map.of("error", "User not found");
UserEntity user = maybe.get();

// or in one expression
UserEntity user = users.findByUsername(name).orElse(null);
```

## 8. Entities (tables)

**Alembic owns the schema.** `application.properties` has `spring.jpa.hibernate.ddl-auto=validate`:
Hibernate never creates or alters tables, it only checks at startup that every `@Entity` matches
the real table. A mismatch = app refuses to start with a "Schema-validation" error naming the
table/column. Source of truth: `../backend/models.py` and the Alembic migrations.

Schema change workflow: write an Alembic migration in `../backend`, run it, then update the Java
entity to match.

`entity/UserEntity.java` is the pattern:

```java
@Entity
@Table(name = "users")
public class UserEntity {

    @Id                                           // primary key
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "profile_picture")             // Java camelCase field -> snake_case column
    private String profilePicture;

    @Column(name = "password_hash")
    private byte[] passwordHash;                  // bytes in Python -> byte[] in Java

    protected UserEntity() {}                     // JPA requires a no-arg constructor

    public String getId() { return id; }
    ...
}
```

To create new rows, add a public constructor (and setters for fields you update):

```java
public UserEntity(String id, String username, byte[] passwordHash) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
}

public void setUsername(String username) { this.username = username; }
```

Type mapping:

| SQLAlchemy / SQLite           | Java field type       |
|-------------------------------|-----------------------|
| `str` / TEXT                  | `String`              |
| `bytes` / BLOB                | `byte[]`              |
| `int` / INTEGER (nullable)    | `Integer`             |
| `int` / INTEGER (not null)    | `int`                 |
| `bool` stored as INTEGER 0/1  | `Integer` (keep it simple; compare with `== 1`) |
| JSON stored as TEXT           | `String`              |

**Foreign keys:** start with a plain column (`@Column(name = "owner_id") private String ownerId;`)
and look up the related row via its repository. Only add `@ManyToOne` / `@OneToMany` relationships
when that becomes painful; they add lazy-loading behavior you don't need yet.

**Ordering:** tables with a `position` column (music pieces, shared_with, saved_by) must be read
with `...OrderByPositionAsc` to keep Python's list order.

**Composite primary keys:** if a join table's PK is two columns, either add `@IdClass` or
check `models.py` for a single-column PK first. Look at the real table before deciding.

## 9. Passwords (BCrypt)

Python's `bcrypt` wrote `$2b$` hashes as bytes. Spring Security's `BCryptPasswordEncoder`
reads them:

```java
private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

// check
boolean ok = user.getPasswordHash() != null
        && bcrypt.matches(rawPassword, new String(user.getPasswordHash(), StandardCharsets.UTF_8));

// create
byte[] hash = bcrypt.encode(rawPassword).getBytes(StandardCharsets.UTF_8);
```

Only `spring-security-crypto` is on the classpath, not full Spring Security, so there is no login
filter or auto-generated password page.

## 10. Worked example: auth routes

Port of `../backend/routes/auth.py`. Add to `UserRepository`:

```java
Optional<UserEntity> findByUsername(String username);
```

`controller/AuthController.java`:

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> data) {
        UserEntity user = users.findByUsername(data.get("username")).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !bcrypt.matches(data.get("password"), new String(user.getPasswordHash(), StandardCharsets.UTF_8))) {
            return Map.of("error", "Username or password incorrect");
        }
        return Map.of("message", "Login successful");
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody Map<String, String> data) {
        String username = data.get("username");
        if (users.existsByUsername(username)) {
            return Map.of("error", "Username already exists");
        }
        byte[] hash = bcrypt.encode(data.get("password")).getBytes(StandardCharsets.UTF_8);
        users.save(new UserEntity(UUID.randomUUID().toString(), username, hash));
        return Map.of("message", "Registration successful");
    }
}
```

(`register` needs the `UserEntity` constructor from section 8. Check `../backend/classes/user.py`
for any other defaults Python sets on a new user, e.g. `profile_picture`, `last_playback`.)

## 11. Suggested porting order

1. `AuthController`: login, then register (section 10).
2. `UserController` TODOs (same pieces: path vars, JSON body, repository lookups).
3. Playlist entities in `entity/`: fill in columns from the comment in each stub, add `@Entity`
   and `@Table`, start the app to validate.
4. `PlaylistController` (multipart uploads, multiple repositories).

For each route: open the Python version, copy the path and method, return the exact same JSON
keys and status code. The Vue frontend must not notice the switch.

## 12. Java gotchas coming from Python

- **Semicolons, braces, types.** `var x = ...;` lets the compiler infer local variable types.
- **String equality:** `a.equals(b)`, never `a == b` (`==` compares object identity).
- **`Map.of(...)` is immutable and rejects `null` values.** For a map that may contain nulls or
  needs modifying: `var m = new HashMap<String, Object>(); m.put("k", v);`
- **`Map.of` takes at most 10 pairs.** Use `HashMap` beyond that.
- **Checked exceptions:** methods doing file I/O must `throws IOException` or catch it.
- **Imports:** IntelliJ adds them with Alt+Enter. `jakarta.persistence.*` for entities,
  `org.springframework.web.bind.annotation.*` for controller annotations.
- **Null checks:** accessing a method on `null` throws `NullPointerException` (Python's
  `AttributeError: 'NoneType'...`). Repository `find...` methods return `Optional` to force you
  to handle "not found".
- **Unhandled exceptions** become a `500` JSON error response automatically.
- **Changes to `application.properties` need a restart.**

## 13. Common startup errors

| Message contains                          | Meaning                                                   |
|-------------------------------------------|-----------------------------------------------------------|
| `Schema-validation: missing column`       | Entity field/column name doesn't match the real table     |
| `Schema-validation: wrong column type`    | Java field type doesn't match the SQLite column type      |
| `No property 'xyz' found for type`        | Typo in a derived repository method name                  |
| `Port 5000 was already in use`            | Python backend (or an old Java run) is still running      |
| `No default constructor for entity`       | Entity is missing `protected Foo() {}`                    |
| `release version 25 not supported` / no `javac` | Only a JRE is installed; install the JDK (see README) |

## 14. Further reading

- https://spring.io/guides/gs/rest-service (controllers)
- https://spring.io/guides/gs/accessing-data-jpa (entities + repositories)
- https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html (derived query naming rules)
