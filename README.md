# Sondhan - AI Fact Checker

Sondhan is an AI-powered fact-checking application.

## Key Highlights

### 1. AI-Powered Fact-Checking Engine
The `FactCheckerService` serves as the core of the application. It processes user claims and utilizes AI/external APIs to determine the authenticity of a statement, providing a robust tool for verifying information.

### 2. Project Evolution & Technology Migration
Development on Sondhan has been ongoing since June 2026. The project was originally conceptualized and developed using React and is now being actively converted into a robust desktop application using JavaFX.

### 3. User Authentication & Session Management
A complete user flow is implemented, including registration, login, and secure sessions (`LoginController`, `RegisterController`, and `SessionManager`). This ensures the application is personalized and secure for individual users.

### 4. Fact-Check Query History
The `HistoryController` allows users to view their past fact-check queries. This provides a great user experience by maintaining state and letting users revisit and manage their previous fact-checks.

### 5. Local Database Integration
The application uses a robust local database (`DatabaseService` connected to SQLite) to securely store user credentials, sessions, and query logs persistently across sessions.
