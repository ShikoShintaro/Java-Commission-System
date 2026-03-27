package controllers;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;

public class CommissionerDashboardController {

    @FXML
    private Label balanceLabel;
    @FXML
    private TableView<Transaction> transactionTable;
    @FXML
    private TableColumn<Transaction, String> dateCol;
    @FXML
    private TableColumn<Transaction, String> typeCol;
    @FXML
    private TableColumn<Transaction, String> clientCol;
    @FXML
    private TableColumn<Transaction, String> detailsCol;
    @FXML
    private TableColumn<Transaction, Double> amountCol;
    @FXML
    private ListView<String> clientsList;
    @FXML
    private TextField messageField;
    @FXML
    private Label statusLabel;

    private String username;
    private Connection conn;
    private ObservableList<String> clientNames = FXCollections.observableArrayList();

    // ---------------------------
    // Initialization
    // ---------------------------
    public void init(String username) {
        this.username = username;
        openUserDB();
        loadBalance();
        loadClients();

        // Set table columns
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDate()));
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        clientCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCounterpart()));
        detailsCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMessage()));
        amountCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().getAmount()).asObject());

        transactionTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        loadTransactions();
    }

    // ---------------------------
    // Database Setup
    // ---------------------------
    private void openUserDB() {
        try {
            File dir = new File("userdb/" + username);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File dbFile = new File(dir, "data.db");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS finance (id INTEGER PRIMARY KEY, balance REAL DEFAULT 0)");
            stmt.execute("INSERT OR IGNORE INTO finance(id, balance) VALUES (1, 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
                    + "id INTEGER PRIMARY KEY, type TEXT, amount REAL DEFAULT 0, counterpart TEXT, message TEXT DEFAULT '', timestamp TEXT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS attachments ("
                    + "id INTEGER PRIMARY KEY, filename TEXT, file_path TEXT, sender TEXT, timestamp TEXT)");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureClientDBTables(Connection clientConn) throws SQLException {
        Statement stmt = clientConn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
                + "id INTEGER PRIMARY KEY, type TEXT, amount REAL DEFAULT 0, counterpart TEXT, message TEXT DEFAULT '', timestamp TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS attachments ("
                + "id INTEGER PRIMARY KEY, filename TEXT, file_path TEXT, sender TEXT, timestamp TEXT)");
        stmt.close();
    }

    private Connection openClientDB(String clientUsername) throws SQLException {
        File dir = new File("userdb/" + clientUsername);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dbFile = new File(dir, "data.db");
        Connection clientConn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        ensureClientDBTables(clientConn);
        return clientConn;
    }

    // ---------------------------
    // Load Balance & Transactions
    // ---------------------------
    private void loadBalance() {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT balance FROM finance WHERE id=1")) {
            if (rs.next()) {
                balanceLabel.setText(String.format("Balance: $%.2f", rs.getDouble("balance")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadTransactions() {
        ObservableList<Transaction> list = FXCollections.observableArrayList();

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM transactions ORDER BY id DESC")) {

            while (rs.next()) {
                String type = rs.getString("type");
                String message = rs.getString("message") != null ? rs.getString("message") : "";
                if (type.equals("attachment")) {
                    message = "[Attachment] " + message;
                }

                list.add(new Transaction(
                        rs.getString("timestamp"),
                        type,
                        rs.getString("counterpart"),
                        message,
                        rs.getDouble("amount")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        transactionTable.setItems(list);
    }

    private void loadClients() {
        clientNames.clear();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:users.db"); Statement stmt = c.createStatement(); ResultSet rs = stmt.executeQuery("SELECT username FROM users WHERE account_type='client'")) {

            while (rs.next()) {
                clientNames.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        clientsList.setItems(clientNames);
    }

    // ---------------------------
    // Send Message / Attachment
    // ---------------------------
    @FXML
    private void handleSendMessage() {
        String client = clientsList.getSelectionModel().getSelectedItem();
        if (client == null) {
            statusLabel.setText("⚠ Select a client first.");
            return;
        }
        sendMessageToClient(client);
    }

    @FXML
    private void handleSendAttachment() {
        String client = clientsList.getSelectionModel().getSelectedItem();
        if (client == null) {
            statusLabel.setText("⚠ Select a client first.");
            return;
        }
        sendAttachmentToClient(client);
    }

    private void sendMessageToClient(String clientUsername) {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            statusLabel.setText("⚠ Enter a message.");
            return;
        }

        try {
            // Save in commissioner's DB
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (type, amount, counterpart, message, timestamp) VALUES ('message', 0, ?, ?, datetime('now'))")) {
                ps.setString(1, clientUsername);
                ps.setString(2, message);
                ps.executeUpdate();
            }

            // Save in client's DB
            try (Connection clientConn = openClientDB(clientUsername); PreparedStatement ps = clientConn.prepareStatement(
                    "INSERT INTO transactions (type, amount, counterpart, message, timestamp) VALUES ('message', 0, ?, ?, datetime('now'))")) {
                ps.setString(1, username);
                ps.setString(2, message);
                ps.executeUpdate();
            }

            statusLabel.setText("✉ Message sent to " + clientUsername);
            messageField.clear();
            loadTransactions();

        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("❌ Failed to send message.");
        }
    }

    private void sendAttachmentToClient(String clientUsername) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select File to Send");
        File file = chooser.showOpenDialog(new Stage());
        if (file == null) {
            return;
        }

        try {
            // Copy to commissioner's folder
            File dir = new File("userdb/" + username + "/attachments");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dest = new File(dir, file.getName());
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Save in commissioner's transactions
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (type, amount, counterpart, message, timestamp) VALUES ('attachment', 0, ?, ?, datetime('now'))")) {
                ps.setString(1, clientUsername);
                ps.setString(2, file.getName());
                ps.executeUpdate();
            }

            // Save in client's attachments table
            try (Connection clientConn = openClientDB(clientUsername); PreparedStatement ps = clientConn.prepareStatement(
                    "INSERT INTO attachments (filename, file_path, sender, timestamp) VALUES (?, ?, ?, datetime('now'))")) {
                ps.setString(1, file.getName());
                ps.setString(2, dest.getAbsolutePath());
                ps.setString(3, username);
                ps.executeUpdate();
            }

            statusLabel.setText("📎 Attachment sent to " + clientUsername);
            loadTransactions();

        } catch (IOException | SQLException e) {
            e.printStackTrace();
            statusLabel.setText("❌ Failed to send attachment.");
        }
    }

    // ---------------------------
    // Transaction Class
    // ---------------------------
    public static class Transaction {

        private final String date, type, counterpart, message;
        private final double amount;

        public Transaction(String date, String type, String counterpart, String message, double amount) {
            this.date = date;
            this.type = type;
            this.counterpart = counterpart;
            this.message = message;
            this.amount = amount;
        }

        public String getDate() {
            return date;
        }

        public String getType() {
            return type;
        }

        public String getCounterpart() {
            return counterpart;
        }

        public String getMessage() {
            return message;
        }

        public double getAmount() {
            return amount;
        }
    }

    @FXML
    private void handleLogout() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/views/Login.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) balanceLabel.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
