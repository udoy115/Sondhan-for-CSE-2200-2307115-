package com.sondhan.service;
import com.sondhan.model.User;
/** Holds current logged-in user and Anthropic API key for the session. */
public class SessionManager {
    private static User   currentUser;
    private static String apiKey = "";
    public static User   getCurrentUser()       { return currentUser; }
    public static void   setCurrentUser(User u) { currentUser = u; }
    public static String getApiKey()            { return apiKey; }
    public static void   setApiKey(String key)  { apiKey = key != null ? key.trim() : ""; }
    public static boolean isGuest()             { return currentUser == null; }
    public static boolean hasApiKey()           { return apiKey != null && !apiKey.isBlank(); }
}