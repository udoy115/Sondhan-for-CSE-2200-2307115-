package com.sondhan.service;
import com.sondhan.model.SearchHistory;
import com.sondhan.model.User;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Topic 3: Relational Database with SQLite.
 * Demonstrates JDBC connection, DDL (CREATE TABLE), DML (INSERT/SELECT/DELETE),
 * PreparedStatement, and Singleton pattern for shared DB access.
 */
public class DatabaseService {
    private static DatabaseService instance;
    private Connection connection;

    private DatabaseService() {}
    public static DatabaseService getInstance() {
        if (instance == null) instance = new DatabaseService();
        return instance;
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:sondhan.db");
            createTables();
            System.out.println("[DB] SQLite ready: sondhan.db");
        } catch (Exception e) { throw new RuntimeException("DB init failed", e); }
    }

    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS searches (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, input_type TEXT, original_input TEXT, claim TEXT, verdict TEXT, confidence INTEGER, explanation TEXT, sources_json TEXT, preloaded INTEGER DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id))");
        }
    }

    public User registerUser(String name, String email, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO users (name,email,password_hash) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, email); ps.setString(3, sha256(password));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return new User(rs.getInt(1), name, email);
        }
        return null;
    }

    public User loginUser(String email, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,name,email FROM users WHERE email=? AND password_hash=?")) {
            ps.setString(1, email); ps.setString(2, sha256(password));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
        }
        return null;
    }

    public int saveSearch(int uid, String type, String orig, String claim, String verdict,
                          int conf, String expl, String srcJson, boolean pre) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO searches (user_id,input_type,original_input,claim,verdict,confidence,explanation,sources_json,preloaded) VALUES (?,?,?,?,?,?,?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1,uid); ps.setString(2,type); ps.setString(3,orig);
            ps.setString(4,claim); ps.setString(5,verdict); ps.setInt(6,conf);
            ps.setString(7,expl); ps.setString(8,srcJson); ps.setInt(9,pre?1:0);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        }
        return -1;
    }

    public List<SearchHistory> getSearchHistory(int userId) throws SQLException {
        List<SearchHistory> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM searches WHERE user_id=? ORDER BY created_at DESC")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SearchHistory h = new SearchHistory();
                h.setId(rs.getInt("id")); h.setUserId(rs.getInt("user_id"));
                h.setInputType(rs.getString("input_type")); h.setOriginalInput(rs.getString("original_input"));
                h.setClaim(rs.getString("claim")); h.setVerdict(rs.getString("verdict"));
                h.setConfidence(rs.getInt("confidence")); h.setExplanation(rs.getString("explanation"));
                h.setSourcesJson(rs.getString("sources_json")); h.setPreloaded(rs.getInt("preloaded")==1);
                String ts = rs.getString("created_at");
                if (ts != null) try { h.setCreatedAt(LocalDateTime.parse(ts.replace(" ","T"))); } catch (Exception ignored) {}
                list.add(h);
            }
        }
        return list;
    }

    public void deleteSearch(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM searches WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate();
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void close() {
        try { if (connection!=null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }
}