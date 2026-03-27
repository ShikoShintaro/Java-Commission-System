
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import utils.ThemeManager;

public class Main extends Application {

    @Override

    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("views/login.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);
        ThemeManager.applyTheme(scene);
        stage.setTitle("Freelance Buissness Manager");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
