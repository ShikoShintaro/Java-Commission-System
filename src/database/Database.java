package database;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Database {

    private static final String URL = "jdbc:sqlite:users.db";

    //  Automatically creates the table with all fields needed for registration
    static {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    email TEXT,
                    phone TEXT,
                    password TEXT NOT NULL,
                    account_type TEXT,
                    birthday TEXT,
                    security_question TEXT,
                    security_answer TEXT
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Connection
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    // Password Hashing (SHA-256)
    public static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Registration with lowercase account type
    public static boolean registerFullUser(String username, String email, String phone,
            String password, String accountType,
            String birthday, String securityQuestion,
            String securityAnswer) {
        String sql = """
            INSERT INTO users
            (username, email, password, account_type, birthday, security_question, security_answer)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, hash(password));
            stmt.setString(4, accountType.toLowerCase()); // normalize account type
            stmt.setString(5, birthday);
            stmt.setString(6, securityQuestion);
            stmt.setString(7, hash(securityAnswer.toLowerCase()));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.err.println("⚠ Username already exists: " + username);
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    private void createAttachmentsTable() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS attachments ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "transaction_id INTEGER NOT NULL,"
                    + "file_path TEXT NOT NULL,"
                    + "file_name TEXT,"
                    + "FOREIGN KEY (transaction_id) REFERENCES transactions(id))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Validate user login
    public static boolean validateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username=? AND password=?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hash(password));
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // LoginController helper
    public static boolean verifyLogin(String username, String password) {
        return validateUser(username, password);
    }

    public static String getUserRole(String username) {
        String sql = "SELECT account_type FROM users WHERE username=?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("account_type"); // "client" or "commissioner"
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Security question / password recovery
    public static String getSecurityQuestion(String username) {
        String sql = "SELECT security_question FROM users WHERE username=?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("security_question");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean verifyAnswer(String username, String answer) {
        String sql = "SELECT * FROM users WHERE username=? AND security_answer=?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hash(answer.toLowerCase()));
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updatePassword(String username, String newPassword) {
        String sql = "UPDATE users SET password=? WHERE username=?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash(newPassword));
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

}
