package controllers;

import javafx.event.ActionEvent;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import utils.ThemeManager;
import javafx.scene.input.MouseEvent;
import java.io.IOException;

import database.Database;
import utils.ThemeManager;

public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private StackPane liquidPane;
    @FXML
    private Rectangle liquidBackground;
    @FXML
    private Circle oilGlow;
    @FXML
    private VBox formBox;

    private Timeline shimmerAnimation;
    private PauseTransition fadeOutDelay;
    private boolean isDarkMode = false;

    @FXML
    private void initialize() {
        formBox.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                ThemeManager.applyTheme(newScene);
            }
        });

        setupLiquidBackground();
        startLiquidShimmer();
        setupOilGlow();
    }

    private void setupLiquidBackground() {
        liquidBackground.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.REPEAT,
                new Stop(0, Color.web("#0096c7")),
                new Stop(1, Color.web("#48cae4"))));
    }

    private void startLiquidShimmer() {
        shimmerAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(liquidBackground.fillProperty(),
                                new LinearGradient(0, 0, 1, 1, true, CycleMethod.REPEAT,
                                        new Stop(0, Color.web("#0096c7")),
                                        new Stop(1, Color.web("#48cae4"))))),
                new KeyFrame(Duration.seconds(10),
                        new KeyValue(liquidBackground.fillProperty(),
                                new LinearGradient(1, 1, 0, 0, true, CycleMethod.REPEAT,
                                        new Stop(0, Color.web("#48cae4")),
                                        new Stop(1, Color.web("#0096c7"))))));
        shimmerAnimation.setCycleCount(Animation.INDEFINITE);
        shimmerAnimation.setAutoReverse(true);
        shimmerAnimation.play();
    }

    private void setupOilGlow() {
        fadeOutDelay = new PauseTransition(Duration.seconds(1));

        liquidPane.setOnMouseMoved((MouseEvent e) -> {
            fadeOutDelay.stop();
            oilGlow.setOpacity(1.0);

            Timeline move = new Timeline(
                    new KeyFrame(Duration.seconds(0.6),
                            new KeyValue(oilGlow.centerXProperty(), e.getX(), Interpolator.EASE_BOTH),
                            new KeyValue(oilGlow.centerYProperty(), e.getY(), Interpolator.EASE_BOTH)
                    )
            );
            move.play();

            fadeOutDelay.setOnFinished(ev -> {
                FadeTransition fade = new FadeTransition(Duration.seconds(1.2), oilGlow);
                fade.setToValue(0.0);
                fade.play();
            });
            fadeOutDelay.playFromStart();
        });
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("⚠ Enter username and password!");
            return;
        }

        if (!Database.verifyLogin(username, password)) {
            errorLabel.setText("❌ Invalid credentials!");
            return;
        }

        // Detect account type
        String role = Database.getUserRole(username);

        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            switch (role.toLowerCase()) {
                // checks the database if what is the account type
                case "client" -> {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ClientDashboard.fxml"));
                    Scene scene = new Scene(loader.load(), 950, 600);

                    // ✅ Here is the important line
                    controllers.ClientDashboardController ctrl = loader.getController();
                    ctrl.init(username);

                    stage.setTitle("Client Dashboard");
                    stage.setScene(scene);
                    stage.setResizable(false);
                    stage.centerOnScreen();
                }

                // case commissioner will logged in here
                case "commissioner" -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/CommissionerDashboard.fxml"));
                        Scene scene = new Scene(loader.load(), 950, 600);

                        // Initialize commissioner controller
                        controllers.CommissionerDashboardController ctrl = loader.getController();
                        ctrl.init(username);

                        stage.setTitle("Commissioner Dashboard");
                        stage.setScene(scene);
                        stage.setResizable(false);
                        stage.centerOnScreen();
                    } catch (IOException e) {
                        e.printStackTrace();
                        errorLabel.setText("⚠ Failed to load Commissioner Dashboard.");
                    }
                }
                default -> {
                    errorLabel.setText("⚠ Unknown account type!");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            errorLabel.setText("❌ Error loading dashboard.");
        }
    }

    @FXML
    private void handleRegister(ActionEvent event) throws IOException {
        switchScene(event, "/views/register.fxml", "Register");
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) throws IOException {
        switchScene(event, "/views/forgot_password.fxml", "Forgot Password");
    }

    private void switchScene(ActionEvent event, String fxmlPath, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Scene scene = new Scene(loader.load(), 800, 700);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
    }

    @FXML
    private void toggleTheme() {
        Scene scene = formBox.getScene();
        ThemeManager.toggleTheme(scene);
    }
}
