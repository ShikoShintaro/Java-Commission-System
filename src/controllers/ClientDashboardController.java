package controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class ClientDashboardController {

    @FXML
    private Label balanceLabel;
    @FXML
    private TableView<Transaction> transactionTable;
    @FXML
    private TableColumn<Transaction, String> dateCol;
    @FXML
    private TableColumn<Transaction, String> typeCol;
    @FXML
    private TableColumn<Transaction, String> counterpartCol;
    @FXML
    private TableColumn<Transaction, String> messageCol;
    @FXML
    private TableColumn<Transaction, Double> amountCol;
    @FXML
    private TextField sendUsernameField;
    @FXML
    private TextField sendAmountField;
    @FXML
    private Label sendStatus;
    @FXML
    private ListView<String> commissionersList;

    private String username;
    private Connection conn;
    private ObservableList<String> commissionerNames = FXCollections.observableArrayList();

    // ---------------------------
    // Initialization
    // ---------------------------
    public void init(String username) {
        this.username = username;
        openUserDB();
        loadBalance();
        setupTableColumns();
        loadTransactions();
        loadCommissioners();
        setupCommissionerClick();

        // Table columns
        dateCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));
        typeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType()));
        counterpartCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCounterpart()));
        // Show [Attachment] automatically if type is "attachment"
        messageCol.setCellValueFactory(data -> {
            Transaction t = data.getValue();
            if ("attachment".equalsIgnoreCase(t.getType()) && !t.getMessage().isEmpty()) {
                return new javafx.beans.property.SimpleStringProperty("[Attachment] " + t.getMessage());
            }
            return new javafx.beans.property.SimpleStringProperty(t.getMessage());
        });
        amountCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getAmount()));
    }

    // ---------------------------
    // Database Setup
    // ---------------------------
    private void setupTableColumns() {
        dateCol.setCellValueFactory(data
                -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));
        typeCol.setCellValueFactory(data
                -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType()));
        counterpartCol.setCellValueFactory(data
                -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCounterpart()));

        // Show [Attachment] automatically if type is "attachment"
        messageCol.setCellValueFactory(data -> {
            Transaction t = data.getValue();
            if ("attachment".equalsIgnoreCase(t.getType()) && !t.getMessage().isEmpty()) {
                return new javafx.beans.property.SimpleStringProperty("[Attachment] " + t.getMessage());
            }
            return new javafx.beans.property.SimpleStringProperty(t.getMessage());
        });

        amountCol.setCellValueFactory(data
                -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getAmount()));

        // Add Action column for opening attachments
        TableColumn<Transaction, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Open");

            {
                btn.setOnAction(e -> {
                    Transaction tx = getTableView().getItems().get(getIndex());
                    if ("attachment".equalsIgnoreCase(tx.getType())) {
                        File file = new File(tx.getFilePath());  // Path stored in Transaction
                        if (file.exists()) {
                            try {
                                java.awt.Desktop.getDesktop().open(file);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || !"attachment".equalsIgnoreCase(getTableView().getItems().get(getIndex()).getType())) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });
        transactionTable.getColumns().add(actionCol);
    }

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
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
                    + "id INTEGER PRIMARY KEY, type TEXT, amount REAL, counterpart TEXT, message TEXT, timestamp TEXT)");
            stmt.execute("INSERT OR IGNORE INTO finance(id, balance) VALUES (1, 0)");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

        try {
            // Load all transactions (including messages and attachment references)
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM transactions ORDER BY id DESC")) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String message = rs.getString("message") != null ? rs.getString("message") : "";
                    String filePath = "";

                    if ("attachment".equalsIgnoreCase(type)) {
                        filePath = "userdb/" + username + "/attachments/" + message;
                    }

                    list.add(new Transaction(
                            rs.getString("timestamp"),
                            type,
                            rs.getString("counterpart"),
                            message,
                            rs.getDouble("amount"),
                            filePath
                    ));
                }
            }

            // Load any missing attachments directly from attachments table
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM attachments ORDER BY id DESC")) {
                while (rs.next()) {
                    String filename = rs.getString("filename");
                    String path = rs.getString("file_path");
                    String sender = rs.getString("sender");

                    // Avoid duplicates
                    boolean exists = list.stream().anyMatch(t -> t.getMessage().equals(filename) && t.getType().equals("attachment"));
                    if (!exists) {
                        list.add(new Transaction(
                                rs.getString("timestamp"),
                                "attachment",
                                sender,
                                filename,
                                0,
                                path
                        ));
                    }
                }
            }

            transactionTable.setItems(list);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ---------------------------
    // Commissioners List
    // ---------------------------
    private void loadCommissioners() {
        commissionerNames.clear();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:users.db"); Statement stmt = c.createStatement(); ResultSet rs = stmt.executeQuery("SELECT username FROM users WHERE account_type='commissioner'")) {

            while (rs.next()) {
                commissionerNames.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        commissionersList.setItems(commissionerNames);
    }

    private void setupCommissionerClick() {
        commissionersList.setOnMouseClicked(event -> {
            String selected = commissionersList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openCommissionerAction(selected);
            }
        });
    }

    private void openCommissionerAction(String commissionerUsername) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Send Task / Payment");
        dialog.setHeaderText("Send task or payment to " + commissionerUsername);
        dialog.setContentText("Enter amount or message:");

        dialog.showAndWait().ifPresent(input -> {
            try {
                double amount = Double.parseDouble(input);
                sendPaymentToCommissioner(commissionerUsername, amount);
            } catch (NumberFormatException e) {
                sendMessageToUser(commissionerUsername, input, false);
            }
        });
    }

    // ---------------------------
    // Send Payment
    // ---------------------------
    private void sendPaymentToCommissioner(String commissionerUsername, double amount) {
        if (amount <= 0) {
            sendStatus.setText("⚠ Invalid amount.");
            return;
        }

        Connection comConn = null;
        try {
            conn.setAutoCommit(false);

            double currentBalance;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT balance FROM finance WHERE id=1")) {
                currentBalance = rs.next() ? rs.getDouble("balance") : 0;
            }

            if (currentBalance < amount) {
                sendStatus.setText("❌ Not enough balance.");
                conn.setAutoCommit(true);
                return;
            }

            File dir = new File("userdb/" + commissionerUsername);
            dir.mkdirs();
            File dbFile = new File(dir, "data.db");
            comConn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            comConn.setAutoCommit(false);

            try (Statement stmt = comConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS finance (id INTEGER PRIMARY KEY, balance REAL DEFAULT 0)");
                stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
                        + "id INTEGER PRIMARY KEY, type TEXT, amount REAL, counterpart TEXT, message TEXT, timestamp TEXT)");
                stmt.execute("INSERT OR IGNORE INTO finance(id, balance) VALUES (1, 0)");
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE finance SET balance = balance - ? WHERE id=1")) {
                ps.setDouble(1, amount);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (type, amount, counterpart, timestamp) VALUES (?, ?, ?, datetime('now'))")) {
                ps.setString(1, "sent");
                ps.setDouble(2, amount);
                ps.setString(3, commissionerUsername);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = comConn.prepareStatement("UPDATE finance SET balance = balance + ? WHERE id=1")) {
                ps.setDouble(1, amount);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = comConn.prepareStatement(
                    "INSERT INTO transactions (type, amount, counterpart, timestamp) VALUES (?, ?, ?, datetime('now'))")) {
                ps.setString(1, "received");
                ps.setDouble(2, amount);
                ps.setString(3, username);
                ps.executeUpdate();
            }

            conn.commit();
            comConn.commit();
            sendStatus.setText("✅ Sent $" + amount + " to " + commissionerUsername);
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (Exception ignored) {
            }
            try {
                if (comConn != null) {
                    comConn.rollback();

                }
            } catch (Exception ignored) {
            }
            sendStatus.setText("❌ Failed to send payment.");
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (Exception ignored) {
            }
            try {
                if (comConn != null) {
                    comConn.setAutoCommit(true);
                    comConn.close();
                }
            } catch (Exception ignored) {
            }
            loadBalance();
            loadTransactions();
        }
    }

    // ---------------------------
    // Send Message / Attachment
    // ---------------------------
    private void sendMessageToUser(String recipientUsername, String message, boolean isAttachment) {
        String type = isAttachment ? "attachment" : "message";

        try {
            // Sender
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (type, amount, counterpart, message, timestamp) VALUES (?, ?, ?, ?, datetime('now'))")) {
                ps.setString(1, type);
                ps.setDouble(2, 0);
                ps.setString(3, recipientUsername);
                ps.setString(4, message);
                ps.executeUpdate();
            }

            // Recipient
            File recipientDir = new File("userdb/" + recipientUsername);
            recipientDir.mkdirs();
            File recipientDB = new File(recipientDir, "data.db");

            try (Connection recipientConn = DriverManager.getConnection("jdbc:sqlite:" + recipientDB.getAbsolutePath()); Statement stmt = recipientConn.createStatement()) {

                stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
                        + "id INTEGER PRIMARY KEY, type TEXT, amount REAL, counterpart TEXT, message TEXT, timestamp TEXT)");

                try (PreparedStatement ps2 = recipientConn.prepareStatement(
                        "INSERT INTO transactions (type, amount, counterpart, message, timestamp) VALUES (?, ?, ?, ?, datetime('now'))")) {
                    ps2.setString(1, type);
                    ps2.setDouble(2, 0);
                    ps2.setString(3, username);
                    ps2.setString(4, message);
                    ps2.executeUpdate();
                }
            }

            loadTransactions();
            sendStatus.setText("✉ " + (isAttachment ? "Attachment" : "Message") + " sent to " + recipientUsername);
        } catch (SQLException e) {
            e.printStackTrace();
            sendStatus.setText("❌ Failed to send " + (isAttachment ? "attachment" : "message"));
        }
    }

    // ---------------------------
    // Add Funds
    // ---------------------------
    @FXML
    private void handleAddFunds() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Funds");
        dialog.setHeaderText("Add Money to Your Balance");
        dialog.setContentText("Enter amount:");

        dialog.showAndWait().ifPresent(input -> {
            try {
                double amount = Double.parseDouble(input);
                if (amount <= 0) {
                    sendStatus.setText("⚠ Invalid amount.");
                    return;
                }

                try (PreparedStatement ps = conn.prepareStatement("UPDATE finance SET balance = balance + ? WHERE id=1")) {
                    ps.setDouble(1, amount);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO transactions (type, amount, counterpart, timestamp) VALUES (?, ?, ?, datetime('now'))")) {
                    ps.setString(1, "added");
                    ps.setDouble(2, amount);
                    ps.setString(3, "self");
                    ps.executeUpdate();
                }

                loadBalance();
                loadTransactions();
                sendStatus.setText("✅ Funds added: $" + amount);
            } catch (NumberFormatException e) {
                sendStatus.setText("⚠ Invalid number.");
            } catch (SQLException e) {
                e.printStackTrace();
                sendStatus.setText("❌ Failed to add funds.");
            }
        });
    }

    // ---------------------------
    // Send Money Button
    // ---------------------------
    @FXML
    private void handleSend() {
        String recipient = sendUsernameField.getText().trim();
        String amountText = sendAmountField.getText().trim();

        if (recipient.isEmpty() || amountText.isEmpty()) {
            sendStatus.setText("⚠ Please fill in all fields.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);
            sendPaymentToCommissioner(recipient, amount);
        } catch (NumberFormatException e) {
            sendStatus.setText("⚠ Invalid amount.");
        }
    }

    // ---------------------------
    // Logout
    // ---------------------------
    @FXML
    private void handleLogout() {
        try {
            Stage stage = (Stage) balanceLabel.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/views/Login.fxml"));
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------------------
    // Transaction Helper Class
    // ---------------------------
    public static class Transaction {

        private final String date, type, counterpart, message;
        private final double amount;
        private final String filePath;  // For attachments

        public Transaction(String date, String type, String counterpart, String message, double amount, String filePath) {
            this.date = date;
            this.type = type;
            this.counterpart = counterpart;
            this.message = message;
            this.amount = amount;
            this.filePath = filePath;
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

        public String getFilePath() {
            return filePath;
        }
    }
}
