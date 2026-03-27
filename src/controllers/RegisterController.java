package controllers;

import database.Database;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.Period;

public class RegisterController {

    // ---------------------------
    // FXML References
    // ---------------------------
    @FXML
    private StackPane liquidPane;
    @FXML
    private Rectangle liquidBackground;
    @FXML
    private Circle oilGlow;

    @FXML
    private VBox formContainer;
    @FXML
    private StackPane contentPane;

    @FXML
    private VBox step1Box;
    @FXML
    private VBox step2Box;
    @FXML
    private VBox otpBox;
    @FXML
    private VBox idUploadBox;

    @FXML
    private RadioButton clientRadio;
    @FXML
    private RadioButton commissionerRadio;

    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private ComboBox<String> securityQuestionBox;
    @FXML
    private TextField securityAnswerField;

    @FXML
    private DatePicker birthdayPicker;
    @FXML
    private Label ageWarningLabel;

    @FXML
    private TextField phoneField;
    @FXML
    private Label errorLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label otpStatusLabel;
    @FXML
    private Label idStatusLabel;

    @FXML
    private TextField otpField;
    @FXML
    private Button uploadIdButton;

    private String selectedAccountType;

    // Internal State
    private int userAge = 0;
    private boolean otpVerified = false;
    private boolean idUploaded = false;

    // Animations
    private Timeline shimmerAnimation;
    private Timeline followAnimation;
    private Timeline hueShiftAnimation;
    private PauseTransition fadeOutDelay;

    // ---------------------------
    // Initialization
    // ---------------------------
    @FXML
    public void initialize() {
        setupLiquidBackground();
        startLiquidShimmer();
        setupOilGlow();
        startIridescentGlow();

        // Hide all step boxes except Step 1
        showOnly(step1Box);
        ageWarningLabel.setVisible(false);
        errorLabel.setVisible(false);

        // Group radio buttons
        ToggleGroup accountTypeGroup = new ToggleGroup();
        clientRadio.setToggleGroup(accountTypeGroup);
        commissionerRadio.setToggleGroup(accountTypeGroup);

        // 🟢 Populate security questions
        securityQuestionBox.getItems().addAll(
                "What is your mother’s maiden name?",
                "What was the name of your first pet?",
                "What city were you born in?",
                "What is your favorite teacher’s name?",
                "What is your favorite color?"
        );
        securityQuestionBox.setPromptText("Select a security question");
    }

    // ---------------------------
    // Step Navigation
    // ---------------------------
    @FXML
    private void goToStep2() {
        if (!clientRadio.isSelected() && !commissionerRadio.isSelected()) {
            errorLabel.setText("⚠ Please select an account type.");
            errorLabel.setVisible(true);
            return;
        }

        errorLabel.setVisible(false);

        // 🟢 Save the chosen account type
        if (clientRadio.isSelected()) {
            selectedAccountType = "Client";
        } else if (commissionerRadio.isSelected()) {
            selectedAccountType = "Commissioner";
        }

        System.out.println("Selected Account Type: " + selectedAccountType); // for debug

        showStep(step1Box, step2Box);
    }

    @FXML
    private void backToStep1() {
        showStep(step2Box, step1Box);
    }

    @FXML
    private void backToStep2() {
        showStep(otpBox, step2Box);
    }

    @FXML
    private void backToOTP() {
        idUploaded = false;
        idStatusLabel.setText("");
        showStep(idUploadBox, otpBox);
    }

    @FXML
    private void validateStep2ThenGoToOTP() {
        if (!validateStep2Inputs()) {
            return;
        }
        showStep(step2Box, otpBox);
    }

    @FXML
    private void handleVerifyOTP() {
        String otp = otpField.getText().trim();
        if (otp.equals("123456")) {
            otpVerified = true;
            otpStatusLabel.setText("✅ OTP verified successfully!");
            showStep(otpBox, idUploadBox);
        } else {
            otpStatusLabel.setText("❌ Invalid OTP. Try 123456 for testing.");
        }
    }

    @FXML
    private void handleUploadID() {
        if (userAge < 18) {
            idStatusLabel.setText("❌ You must be at least 18 to upload an ID.");
            idUploaded = false;
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Valid ID Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = chooser.showOpenDialog(uploadIdButton.getScene().getWindow());
        if (file != null) {
            idUploaded = true;
            idStatusLabel.setText("✅ ID uploaded: " + file.getName());
        } else {
            idUploaded = false;
            idStatusLabel.setText("⚠ No file selected. Please upload a valid ID.");
        }
    }

    @FXML
    private void completeRegistration() {
        if (userAge < 18) {
            statusLabel.setText("❌ Registration failed: you are not old enough.");
            return;
        }
        if (!otpVerified) {
            statusLabel.setText("⚠ Please verify your email first.");
            return;
        }
        if (!idUploaded) {
            statusLabel.setText("⚠ Please upload your valid ID before completing registration.");
            return;
        }

        boolean success = Database.registerFullUser(
                usernameField.getText(),
                emailField.getText(),
                phoneField.getText(),
                passwordField.getText(),
                selectedAccountType,
                birthdayPicker.getValue().toString(),
                securityQuestionBox.getValue(),
                securityAnswerField.getText()
        );

        if (success) {
            statusLabel.setText("🎉 Registration complete! Redirecting to login...");
            statusLabel.setTextFill(javafx.scene.paint.Color.GREEN);

            // ⏳ Wait 2.5 seconds, then go back to login screen
            PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
            delay.setOnFinished(event -> {
                try {
                    Stage stage = (Stage) statusLabel.getScene().getWindow();
                    Parent root = FXMLLoader.load(getClass().getResource("/views/login.fxml"));
                    stage.setScene(new Scene(root));
                    stage.show();
                } catch (IOException e) {
                    e.printStackTrace();
                    statusLabel.setText("⚠ Error loading login screen.");
                }
            });
            delay.play();

        } else {
            statusLabel.setText("❌ Registration failed: user may already exist or database error.");
            statusLabel.setTextFill(javafx.scene.paint.Color.RED);
        }
    }

    // ---------------------------
    // Validation
    // ---------------------------
    private boolean validateStep2Inputs() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = passwordField.getText().trim();
        String confirm = confirmPasswordField.getText().trim();
        LocalDate birthday = birthdayPicker.getValue();

        if (birthday == null) {
            ageWarningLabel.setText("⚠ Please select your birthday.");
            ageWarningLabel.setVisible(true);
            return false;
        }

        userAge = Period.between(birthday, LocalDate.now()).getYears();
        if (userAge < 18) {
            ageWarningLabel.setText("⚠ You must be at least 18 years old.");
            ageWarningLabel.setVisible(true);
            return false;
        }

        if (username.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            statusLabel.setText("⚠ Please fill in all fields.");
            return false;
        }

        if (!pass.equals(confirm)) {
            statusLabel.setText("⚠ Passwords do not match.");
            return false;
        }

        ageWarningLabel.setVisible(false);
        statusLabel.setText("");
        return true;
    }

    // ---------------------------
    // Step Transitions
    // ---------------------------
    private void showOnly(Pane target) {
        step1Box.setVisible(false);
        step1Box.setManaged(false);
        step2Box.setVisible(false);
        step2Box.setManaged(false);
        otpBox.setVisible(false);
        otpBox.setManaged(false);
        idUploadBox.setVisible(false);
        idUploadBox.setManaged(false);

        target.setVisible(true);
        target.setManaged(true);
    }

    private void showStep(Pane current, Pane next) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), current);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), next);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        fadeOut.setOnFinished(e -> {
            current.setVisible(false);
            current.setManaged(false);
            next.setVisible(true);
            next.setManaged(true);
            fadeIn.play();
        });
        fadeOut.play();
    }

    // ---------------------------
    // Background & Glow Effects
    // ---------------------------
    private void setupLiquidBackground() {
        liquidBackground.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0096c7")),
                new Stop(1, Color.web("#48cae4"))
        ));
    }

    private void startLiquidShimmer() {
        final LinearGradient gradA = new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0096c7")),
                new Stop(1, Color.web("#48cae4"))
        );
        final LinearGradient gradB = new LinearGradient(
                1, 1, 0, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#48cae4")),
                new Stop(1, Color.web("#0096c7"))
        );

        final javafx.beans.property.DoubleProperty progress = new javafx.beans.property.SimpleDoubleProperty(0);
        progress.addListener((obs, oldV, newV) -> {
            double t = newV.doubleValue();
            Color c0 = gradA.getStops().get(0).getColor().interpolate(gradB.getStops().get(0).getColor(), t);
            Color c1 = gradA.getStops().get(1).getColor().interpolate(gradB.getStops().get(1).getColor(), t);
            liquidBackground.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, c0), new Stop(1, c1)));
        });

        shimmerAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0.0)),
                new KeyFrame(Duration.seconds(10), new KeyValue(progress, 1.0))
        );
        shimmerAnimation.setAutoReverse(true);
        shimmerAnimation.setCycleCount(Animation.INDEFINITE);
        shimmerAnimation.play();
    }

    private void setupOilGlow() {
        oilGlow.setOpacity(0.0);
        oilGlow.setCenterX(-500);
        oilGlow.setCenterY(-500);

        followAnimation = new Timeline();
        fadeOutDelay = new PauseTransition(Duration.seconds(1.0));

        liquidPane.setOnMouseMoved((MouseEvent e) -> {
            fadeOutDelay.stop();
            followAnimation.stop();

            oilGlow.setOpacity(1.0);
            KeyValue kvX = new KeyValue(oilGlow.centerXProperty(), e.getX(), Interpolator.EASE_BOTH);
            KeyValue kvY = new KeyValue(oilGlow.centerYProperty(), e.getY(), Interpolator.EASE_BOTH);
            followAnimation = new Timeline(new KeyFrame(Duration.seconds(0.9), kvX, kvY));
            followAnimation.play();

            fadeOutDelay.setOnFinished(ev -> {
                FadeTransition fade = new FadeTransition(Duration.seconds(1.2), oilGlow);
                fade.setToValue(0.0);
                fade.play();
            });
            fadeOutDelay.playFromStart();
        });

        liquidPane.setOnMouseExited(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(600), oilGlow);
            fade.setToValue(0.0);
            fade.play();
        });
    }

    private void startIridescentGlow() {
        hueShiftAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(oilGlow.fillProperty(), createGlowColor(0))),
                new KeyFrame(Duration.seconds(8), new KeyValue(oilGlow.fillProperty(), createGlowColor(360)))
        );
        hueShiftAnimation.setCycleCount(Animation.INDEFINITE);
        hueShiftAnimation.setAutoReverse(true);
        hueShiftAnimation.play();
    }

    private Paint createGlowColor(double hue) {
        Color base1 = Color.hsb(hue, 0.7, 1.0, 0.3);
        Color base2 = Color.hsb((hue + 120) % 360, 0.8, 1.0, 0.3);
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, base1), new Stop(1, base2));
    }
}
