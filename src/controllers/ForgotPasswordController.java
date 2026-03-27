package controllers;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import database.Database;
import java.io.IOException;

public class ForgotPasswordController {

    @FXML
    private TextField usernameField;
    @FXML
    private Label questionLabel;
    @FXML
    private TextField answerField;
    @FXML
    private TextField otpField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private Label step1Status;
    @FXML
    private Label step2Status;
    @FXML
    private Label step3Status;
    @FXML
    private StackPane liquidPane;
    @FXML
    private Rectangle liquidBackground;

    private boolean step1Done = false;
    private boolean step2Done = false;
    private double time = 0;

    @FXML
    private void initialize() {
        liquidBackground.setFill(createGradient(time));
        AnimationTimer shimmer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                time += 0.002;
                liquidBackground.setFill(createGradient(time));
            }
        };
        shimmer.start();
    }

    private LinearGradient createGradient(double t) {
        Color c1 = Color.hsb((t * 40) % 360, 0.6, 1.0);
        Color c2 = Color.hsb((t * 40 + 120) % 360, 0.6, 1.0);
        Color c3 = Color.hsb((t * 40 + 240) % 360, 0.6, 1.0);
        return new LinearGradient(0, 0, 1, 1, true, null,
                new Stop(0, c1), new Stop(0.5, c2), new Stop(1, c3));
    }

    // ----------------------------
    // Step 1: Enter username
    // ----------------------------
    @FXML
    private void handleStep1Next() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            step1Status.setText("⚠ Enter username!");
            return;
        }

        String question = Database.getSecurityQuestion(username);
        if (question != null) {
            questionLabel.setText(question);
            step1Done = true;
            step1Status.setText("✅ Account found! Answer the security question.");
            showStep("step2Box");
        } else {
            step1Status.setText("❌ Username not found!");
        }
    }

    // ----------------------------
    // Step 2: Verify answer + OTP
    // ----------------------------
    @FXML
    private void handleStep2Next() {
        String username = usernameField.getText().trim();
        String answer = answerField.getText().trim();
        String otp = otpField.getText().trim();

        if (answer.isEmpty() || otp.isEmpty()) {
            step2Status.setText("⚠ Fill in both answer and OTP");
            return;
        }

        if (!Database.verifyAnswer(username, answer)) {
            step2Status.setText("❌ Incorrect security answer");
            return;
        }

        if (!otp.equals("123456")) {
            step2Status.setText("❌ Invalid OTP (try 123456)");
            return;
        }

        step2Done = true;
        step2Status.setText("✅ Verified! Set new password below.");
        showStep("step3Box");
    }

    // ----------------------------
    // Step 3: Reset password only
    // ----------------------------
    @FXML
    private void handleResetPassword() {
        if (!step1Done || !step2Done) {
            step3Status.setText("⚠ Complete previous steps first!");
            return;
        }

        String username = usernameField.getText().trim();
        String newPass = newPasswordField.getText().trim();

        if (newPass.isEmpty()) {
            step3Status.setText("⚠ Enter new password!");
            return;
        }

        boolean success = Database.updatePassword(username, newPass);
        if (success) {
            step3Status.setText("✅ Password reset successful! Redirecting to login...");
            PauseTransition delay = new PauseTransition(Duration.seconds(3));
            delay.setOnFinished(e -> redirectToLogin());
            delay.play();
        } else {
            step3Status.setText("❌ Error updating password!");
        }
    }

    // ----------------------------
    // Navigation helper
    // ----------------------------
    private void showStep(String stepId) {
        liquidPane.lookupAll(".step-box").forEach(node -> {
            node.setVisible(false);
            node.setManaged(false);
        });
        Node step = liquidPane.lookup("#" + stepId);
        if (step != null) {
            step.setVisible(true);
            step.setManaged(true);
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        redirectToLogin();
    }

    private void redirectToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/login.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);
            Stage stage = (Stage) liquidPane.getScene().getWindow();
            stage.setTitle("Login");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
