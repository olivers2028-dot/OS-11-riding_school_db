import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;

public class DatabaseManager extends DatabaseQuery {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DatabaseManager() {
    }

    public static void initializeDatabase() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:riding_school.db");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS warning_overrides (
                        WarningKey TEXT PRIMARY KEY,
                        Reason TEXT NOT NULL,
                        OverriddenAt DATETIME NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to initialize database: " + e.getMessage(), e);
        }
    }
}
