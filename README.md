# সন্ধান (Sondhan) v2.0 – AI Fact Checker & Citation Engine

Intelligent Truth Verification Desktop Application
Built with Java 17+ / 21+, JavaFX, Multithreading (ExecutorService), SQLite Database, and JSON Processing (org.json).

## 🌟 Executive Summary
সন্ধান (Sondhan) is an academic course project desktop application that fact-checks news screenshots, social media images, and textual claims. It cross-references claims against verified newspaper articles, published reference books, and peer-reviewed scientific journals using advanced AI models.

## 🎯 Course Curriculum Topics Implementation

| Topic | Curriculum Requirement | Sondhan v2.0 Implementation |
|---|---|---|
| **Version Control** | Demonstrate regular usage of GitHub and commits, starting from September 6th. | • Consistent commit history tracking the project's evolution from idea submission.<br>• Branching and structured commits for features and bug fixes. |
| **Advanced OOP Concepts** | Classes, Interfaces, Abstract Classes, etc. | • MVC architecture with dedicated Controller, Model, and Service classes.<br>• Implementation of Interfaces for database and API services.<br>• Encapsulation of user data and search history models. |
| **JavaFX UI Design** | Wide range of layout panes and UI controls (BorderPane, StackPane, PasswordField, etc.) | • Mixed Dark/Light theme (`styles.css`).<br>• Use of `VBox`, `HBox`, `BorderPane`, `StackPane`, `TableView`, and `PasswordField`.<br>• Custom styled citation cards and status indicators. |
| **Layout Responsiveness** | Dynamic and responsive using property constraints. | • UI components dynamically resize based on window dimensions.<br>• Extensive use of JavaFX property bindings (e.g., `prefWidthProperty().bind(...)`) for fluid layouts. |
| **Concurrency** | Multi-threading and Thread Pools. | • Fixed daemon thread pool (`Executors.newFixedThreadPool(4)`).<br>• Asynchronous `javafx.concurrent.Task<T>` with live message & progress binding.<br>• Thread-safe UI updates using `Platform.runLater()`.<br>• Non-blocking SQLite I/O and HTTP network calls. |
| **Database Integration** | SQLite database setup, table structures, and relationships. | • SQLite JDBC (`sondhan.db`) with WAL mode enabled.<br>• Tables: `users` (credentials hashed with SHA-256) and `searches` (history).<br>• Foreign key relationship linking search history to specific users. |
| **Data Manipulation** | Complete CRUD operations. | • **Create**: Registering users, saving new search histories.<br>• **Read**: Fetching user profiles, loading history into TableView.<br>• **Update**: Modifying settings or API keys.<br>• **Delete**: Single record deletion and bulk purge of history. |
| **Networking & Data Parsing** | HTTP requests to fetch JSON data, parsing JSON. | • Secure HTTPS requests via `java.net.http.HttpClient`.<br>• `org.json` (`JSONObject`, `JSONArray`) used for constructing dynamic API requests.<br>• Parsing complex JSON responses from Claude & ChatGPT to extract verdicts, summaries, and citations. |

## 🚀 How to Run
### Option 1: IntelliJ IDEA / Eclipse
1. Open the project folder in IntelliJ IDEA.
2. Set Project SDK to Java 17 or higher.
3. Run `com.sondhan.Main`.

### Option 2: Maven via Terminal
Open Terminal in the project folder and run:
```bash
mvn clean javafx:run
```

## 🔑 AI Models Architecture
Sondhan utilizes live AI to verify claims.

1. Launch the app and click the ⚙ Settings button in the header.
2. Enter your Anthropic Claude API Key (`sk-ant-...`) and/or OpenAI ChatGPT API Key (`sk-...`).
3. Click Save Settings.
4. The status pill instantly changes to 🟢 Claude Live or 🟢 ChatGPT Live.
5. Select your desired model from the header dropdown:
   - Claude 3.5 Sonnet
   - ChatGPT (GPT-4o)
   - Auto / Smart Engine
6. Live multimodal vision and text requests are dispatched over secure HTTPS via `java.net.http.HttpClient` with `org.json`.

## 🧪 Quick Walkthrough
1. **Sign In**: Register a new account or sign in with existing credentials.
2. **Verify Image**:
   - Upload an image.
   - Observe the Image Inspector: File name, dimensions, size, format, and SHA-256 hash.
   - Click 🔎 Verify Claim with AI.
   - Note the Multithreaded Progress Bar and live status updates.
3. **Inspect the Result**:
   - Large colored verdict badge: ✓ VERIFIED TRUE / ✕ FALSE CLAIM / ⚠ MISLEADING.
   - Confidence percentage meter.
   - Categorized Sources: Click any newspaper or book citation to launch it in your default browser.
   - 10-Point Executive Summary: Complete breakdown of the fact-check.
4. **Test Text Claim**: Switch to the Text Statement tab, input a claim, and verify it.
5. **Inspect History**:
   - Click 📜 History in the header.
   - Filter claims dynamically using the search bar or verdict filter dropdown.
   - Click any table row to see its complete details and interactive source links.
   - Manage your history by deleting selected rows or clearing all.

## 📂 Project Architecture
```text
SondhanJavaFX/
├── pom.xml                             # Maven POM configuration
├── sondhan.db                          # SQLite relational database
└── src/
    ├── main/
    │   ├── java/com/sondhan/
    │   │   ├── Main.java               # Application bootstrap & Scene navigator
    │   │   ├── controller/
    │   │   │   ├── LoginController.java
    │   │   │   ├── RegisterController.java
    │   │   │   ├── HomeController.java # Main UI coordinator & task orchestration
    │   │   │   └── HistoryController.java # TableView, analytics & SQLite queries
    │   │   ├── model/
    │   │   │   ├── User.java           # Authenticated user entity
    │   │   │   ├── FactCheckResult.java# Fact-check domain model & Sources
    │   │   │   └── SearchHistory.java  # SQLite searches entity
    │   │   ├── service/
    │   │   │   ├── DatabaseService.java# SQLite JDBC singleton & schema
    │   │   │   ├── FactCheckerService.java # AI Engine integration
    │   │   │   └── SessionManager.java # In-memory session & API keys
    │   │   └── util/
    │   │       └── ImageHashUtil.java  # Image inspection & SHA-256 hashing
    │   └── resources/com/sondhan/
    │       ├── login.fxml              # Styled login screen
    │       ├── register.fxml           # Registration screen
    │       ├── home.fxml               # Main fact-checking workbench
    │       ├── history.fxml            # SQLite archive & analytics screen
    │       └── styles.css              # Mixed dark/light theme
```

## 🛡️ Security & Reliability
- **Password Protection**: Passwords hashed with standard SHA-256 before storage.
- **SQL Injection Prevention**: All SQL queries use parameterized PreparedStatement.
- **API Key Privacy**: API keys remain strictly in volatile memory (SessionManager) and are never written to database tables or log files.
- **Thread Isolation**: All network operations and database transactions run on background daemon threads, guaranteeing that the JavaFX UI remains completely responsive.
