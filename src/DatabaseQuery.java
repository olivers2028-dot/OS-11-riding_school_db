import java.sql.Connection;
import java.sql.DriverManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;

import java.sql.SQLException;

import java.sql.Statement;

import java.sql.Types;

import java.time.LocalDateTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.Objects;
import java.util.Set;

/**
 * Central database helper
 * with the SQL, validation, inserts, updates, and  checks
 *
 * PreparedStatemetnt documentation: <a href="https://docs.oracle.com/javase/8/docs/api/java/sql/PreparedStatement.html">...</a>
 *
 * kind of like StringBuilder for SQL queries
 **/
public class DatabaseQuery {

    //Define object ColumnInfo
    public record ColumnInfo(String name, String type, boolean notNull, boolean primaryKey) {
    }


    //protected = same package/subclass
    protected DatabaseQuery() {
    }

    /** ESTABLISH  CONNECTION**/

    private static Connection connectdatabase() throws SQLException {
        try {
            String DB_URL = "jdbc:sqlite:riding_school.db"; //DB URL
            Connection connection = DriverManager.getConnection(DB_URL);
            try (Statement statement = connection.createStatement()) {

                //Good practice to turn on foreign key constraint enforcement
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return connection;
        } catch (SQLException e) {
            throw new SQLException("Unable to connectdatabase to database", e);
        }
    }

    /** Reads SQLite column metadata for the named table */
    public static List<ColumnInfo> getColumnMetadata(String tableName) throws SQLException {

        List<ColumnInfo> listcolumns = new ArrayList<>();

        try (
                Connection connection = connectdatabase();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ")")
        ) {

            while (rs.next()) {
                //Read column name
                String columnName = rs.getString("name");

                //Read column type. replacing null with an empty string
                String columnType = rs.getString("type");
                if (columnType == null) {
                    columnType = "";
                }

                //Determine whether the column is NOT NULL
                boolean isNotNull = rs.getInt("notnull") == 1;

                // whether the column is part of the primary key
                boolean isPrimaryKey = rs.getInt("pk") > 0;

                //Create a ColumnInfo object
                ColumnInfo column = new ColumnInfo(
                        columnName,
                        columnType,
                        isNotNull,
                        isPrimaryKey
                );

                //Add it to the list
                listcolumns.add(column);
            }
        }

        return listcolumns;
    }

    //adds query results to arraylist object
    private static List<NamedEntity> queryNamedEntities(String sql, String idColumn, SqlBinder binder)
            throws SQLException {
        //create arraylist
        List<NamedEntity> results = new ArrayList<>();

        //calls function withResultSet to make a NamedEntity list with ordered ID-field-field via lambda function
        withResultSet(sql, binder, rs -> {
            while (rs.next()) {
                results.add(new NamedEntity(rs.getInt(idColumn), rs.getString("Label")));
            }
        });
        return results;
    }

    //connects to database -- generic function
    private static void withResultSet(String sql, SqlBinder binder, ResultSetConsumer consumer) throws SQLException {
        try (Connection connection = connectdatabase();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            //fills in information if statement is null
            if (binder != null) {
                binder.bind(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                consumer.accept(rs);
            }
        }
    }

    /** Returns the primary key column for a table, or rowid if the table has no single primary key. */
    public static String getPrimaryKeyColumn(String tableName) throws SQLException {

        //Get column metadata for columns
        List<ColumnInfo> columns = getColumnMetadata(tableName);

        //get arraylist
        List<String> primaryKeys = new ArrayList<>();
        String integerPrimaryKey = null;


        for (ColumnInfo column : columns) {

            if (column.primaryKey()) {

                primaryKeys.add(column.name());

                String type = column.type().toUpperCase(Locale.ROOT);

                if (type.contains("INT")) {
                    integerPrimaryKey = column.name();
                }
            }
        }

        // Simple primary key
        if (primaryKeys.size() == 1) {
            return primaryKeys.get(0);
        }

        // Composite key: prefer an integer column
        if (integerPrimaryKey != null) {
            return integerPrimaryKey;
        }

        // No primary key found
        return "rowid";
    }

    /** Returns the columns that should be shown to users, excluding the internal ID field. */
    public static List<String> getVisibleColumns(String tableName) throws SQLException {
        String idColumn = getPrimaryKeyColumn(tableName);

        //create new arraylist
        List<String> columns = new ArrayList<>();

        //onlu add relevant cells to arraylist, not ID field
        for (ColumnInfo column : getColumnMetadata(tableName)) {
            if (!column.name().equals(idColumn)) {
                columns.add(column.name());
            }
        }

        //return user view
        return columns;
    }

    /** Loads rows into the shared table model structure used by the GUI. */
    //GETTABLEDATA takes the table name, idColumn, visibleColumns list, where, list params, order by
    //Basically a customizable SQL statement as a function, which prepares + return the statement
    public static QueryResult getTableData(String tableName, String idColumn, List<String> visibleColumns, String whereClause, List<Object> params, String orderBy) throws SQLException {

        //Use stringbuilder here, more intuitive compared to concat
        StringBuilder sql = new StringBuilder("SELECT ").append(idColumn);


        //build statement
        for (String column : visibleColumns) {
            sql.append(", ").append(column);
        }

        sql.append(" FROM ").append(tableName);
        if (whereClause != null && !whereClause.isBlank()) {
            sql.append(" WHERE ").append(whereClause);
        }
        if (orderBy != null && !orderBy.isBlank()) {
            sql.append(" ORDER BY ").append(orderBy);
        }

        //return the created query object
        return query(sql.toString(), visibleColumns, idColumn, params, null);
    }

    /** Returns horse labels in the form {import-number - name}. */
    /** Used in Horses, Lessons panels*/
    public static List<NamedEntity> getNamedHorses() throws SQLException {
        return queryNamedEntities("""
                SELECT HorseID,
                       COALESCE(NULLIF(Import_number, ''), 'Horse #' || HorseID) || ' - ' || Name AS Label
                FROM horses
                ORDER BY Name
                """, "HorseID", null);
    }

    /** Returns label/id pairs for either coaches or riders. */
    public static List<NamedEntity> getNamedPeople(String type) throws SQLException {
        return queryNamedEntities("""
                SELECT PersonID,
                       First_name || ' ' || Last_name AS Label
                FROM people
                WHERE Type = ?
                ORDER BY First_name, Last_name
                """, "PersonID", ps -> ps.setString(1, type));
    }

    /** Returns the available session types. */
    public static List<NamedEntity> getSessionTypes() throws SQLException {
        return queryNamedEntities("""
                SELECT TypeID, Name AS Label
                FROM session_types
                ORDER BY Name
                """, "TypeID", null);
    }


    /** TODO: PRESENT THIS PART */
    public static QueryResult getHorseSchedule(int horseId, int days) throws SQLException {
        String sql = """
                SELECT s.SessionID,
                       s.StartTime,
                       s.Duration,
                       st.Name AS Session_type,
                       CASE
                           WHEN l.LessonID IS NULL THEN 'Unridden session'
                           ELSE 'Lesson #' || l.LessonID
                       END AS Lesson,
                       COALESCE(c.First_name || ' ' || c.Last_name, '') AS Coach,
                       COALESCE((
                           SELECT GROUP_CONCAT(Name, ', ')
                           FROM (
                               SELECT DISTINCT p.First_name || ' ' || p.Last_name AS Name
                               FROM lesson_participants lp
                               JOIN people p ON p.PersonID = lp.PersonID
                               WHERE lp.LessonID = l.LessonID
                               ORDER BY Name
                           )
                       ), '') AS Riders
                FROM sessions s
                JOIN session_types st ON st.TypeID = s.TypeID
                LEFT JOIN lessons l ON l.LessonID = s.LessonID
                LEFT JOIN people c ON c.PersonID = l.CoachID
                WHERE s.HorseID = ?
                  AND datetime(s.StartTime) >= datetime('now', ?)
                ORDER BY datetime(s.StartTime)
                """;
        return query(sql, List.of("StartTime", "Duration", "Session_type", "Lesson", "Coach", "Riders"),
                "SessionID", ps -> {
                    ps.setInt(1, horseId);
                    ps.setString(2, "-" + days + " days");
                });
    }

    // Gets coach schedule with SQL query, then returns ps object
    public static QueryResult getCoachSchedule(int coachId, int days) throws SQLException {
        String sql = """
                SELECT l.LessonID,
                       l.StartTime,
                       l.Duration,
                       COALESCE((
                           SELECT GROUP_CONCAT(Name, ', ')
                           FROM (
                               SELECT DISTINCT h.Name AS Name
                               FROM sessions s
                               JOIN horses h ON h.HorseID = s.HorseID
                               WHERE s.LessonID = l.LessonID
                               ORDER BY Name
                           )
                       ), '') AS Horses,
                       COALESCE((
                           SELECT GROUP_CONCAT(Name, ', ')
                           FROM (
                               SELECT DISTINCT p.First_name || ' ' || p.Last_name AS Name
                               FROM lesson_participants lp
                               JOIN people p ON p.PersonID = lp.PersonID
                               WHERE lp.LessonID = l.LessonID
                               ORDER BY Name
                           )
                       ), '') AS Riders
                FROM lessons l
                WHERE l.CoachID = ?
                  AND datetime(l.StartTime) >= datetime('now', ?)
                ORDER BY datetime(l.StartTime)
                """;
        return query(sql, List.of("StartTime", "Duration", "Horses", "Riders"), "LessonID", ps -> {
            ps.setInt(1, coachId);
                    ps.setString(2, "-" + days + " days");
                });
    }

    //Public method that returns a QueryResult.
    //Takes a filter string such as "past" or "now" in input, then can filter with datetime sql
    public static QueryResult getLessonsOverview(String filter) throws SQLException {
        //no filtering default
        String time = "";
        if ("past".equalsIgnoreCase(filter)) {
            time = "WHERE datetime(l.StartTime) < datetime('now')";


        } else if ("now".equalsIgnoreCase(filter)) {
            time = """
                    WHERE datetime('now') BETWEEN datetime(l.StartTime)
                      AND datetime(l.StartTime, '+' || l.Duration || ' minutes')
                    """;


        }
        return queryLessonSummary(time, null, "datetime(l.StartTime) DESC");
    }


    //if a string is empty or " ", return sql null value.
    //Trims values for whitespace
    private static void nullastring(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value.trim());
        }
    }

    //if a string is empty or " ", return SQL null value.
    //Trims values for whitespace
    private static void nullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    //if a string is empty or " " when an int is expected, return SQL null value.
    //Trims values for whitespace,  convert String to int
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    //get compatibility message for horse/rider combinations

    public static List<String> getCompatibilityMessages(int riderId, int horseId) throws SQLException {
        List<String> messages = new ArrayList<>();
        String sql = """
                SELECT p.Height_cm AS RiderHeight,
                       p.Weight_kg AS RiderWeight,
                       p.RidersLevelID AS RiderLevel,
                       rl.Description AS RiderLevelName,
                       h.Height_cm AS HorseHeight,
                       h.Weight_kg AS HorseWeight,
                       h.HorseLevelID AS HorseLevel,
                       hl.Description AS HorseLevelName
                FROM people p
                JOIN horses h ON h.HorseID = ?
                LEFT JOIN rider_level rl ON rl.RidersLevelID = p.RidersLevelID
                LEFT JOIN horse_level hl ON hl.HorseLevelID = h.HorseLevelID
                WHERE p.PersonID = ?
                """;
        withResultSet(sql, ps -> {
            ps.setInt(1, horseId);
            ps.setInt(2, riderId);
        }, rs -> {
            if (!rs.next()) {
                return;
            }

            //define nullableInt
            Integer riderLevel = nullableInt(rs, "RiderLevel");
            Integer horseLevel = nullableInt(rs, "HorseLevel");
            Integer riderHeight = nullableInt(rs, "RiderHeight");
            Integer horseHeight = nullableInt(rs, "HorseHeight");
            Integer riderWeight = nullableInt(rs, "RiderWeight");
            Integer horseWeight = nullableInt(rs, "HorseWeight");
            String riderLevelName = rs.getString("RiderLevelName");
            String horseLevelName = rs.getString("HorseLevelName");

            //check horse/rider level compat
            if (riderLevel != null && horseLevel != null && riderLevel < horseLevel) {
                messages.add("Compatibility warning: rider level " + labelOrNumber(riderLevelName, riderLevel)
                        + " is below horse requirement " + labelOrNumber(horseLevelName, horseLevel) + ".");
            }

            //check rider/horse height compat
            if (riderHeight != null && horseHeight != null && horseHeight - riderHeight > 55) {
                messages.add("Compatibility warning: horse is much taller than the rider.");
            }

            //check horse/rider weight compat
            if (riderWeight != null && horseWeight != null && riderWeight > horseWeight * 0.2) {
                messages.add("Compatibility warning: rider weight is above the recommended carrying limit.");
            }
        });
        return messages;
    }

    //* Find all compatibility messages for LESSONS, eg:
    // Compatibility warning: Alice Smith (Beginner) is below horse requirement Thunder (Advanced).
    // Compatibility warning: Thunder is much taller than Alice Smith.*/

    public static List<String> getLessonCompatibilityMessages(int lessonId, int horseId) throws SQLException {
        List<String> messages = new ArrayList<>();
        String sql = """
                SELECT lp.PersonID,
                       p.First_name || ' ' || p.Last_name AS RiderName,
                       p.RidersLevelID AS RiderLevel,
                       rl.Description AS RiderLevelName,
                       h.HorseLevelID AS HorseLevel,
                       hl.Description AS HorseLevelName,
                       p.Height_cm AS RiderHeight,
                       h.Height_cm AS HorseHeight,
                       p.Weight_kg AS RiderWeight,
                       h.Weight_kg AS HorseWeight,
                       h.Name AS HorseName
                FROM lesson_participants lp
                JOIN people p ON p.PersonID = lp.PersonID
                JOIN horses h ON h.HorseID = ?
                LEFT JOIN rider_level rl ON rl.RidersLevelID = p.RidersLevelID
                LEFT JOIN horse_level hl ON hl.HorseLevelID = h.HorseLevelID
                WHERE lp.LessonID = ?
                """;
        withResultSet(sql, ps -> {
            ps.setInt(1, horseId);
            ps.setInt(2, lessonId);
        }, rs -> {
            while (rs.next()) {

                //define parameters as nullableInts. If getInt was used 0 would be returned instead of NULL, avoiding
                //checking missing data
                Integer riderLevel = nullableInt(rs, "RiderLevel");
                Integer horseLevel = nullableInt(rs, "HorseLevel");
                Integer riderHeight = nullableInt(rs, "RiderHeight");
                Integer horseHeight = nullableInt(rs, "HorseHeight");
                Integer riderWeight = nullableInt(rs, "RiderWeight");
                Integer horseWeight = nullableInt(rs, "HorseWeight");
                String riderLevelName = rs.getString("RiderLevelName");
                String horseLevelName = rs.getString("HorseLevelName");
                String riderName = rs.getString("RiderName");
                String horseName = rs.getString("HorseName");

                //horse requirement level
                if (riderLevel != null && horseLevel != null && riderLevel < horseLevel) {
                    messages.add("Compatibility warning: " + riderName + " (" + labelOrNumber(riderLevelName, riderLevel)
                            + ") is below horse requirement " + horseName + " ("
                            + labelOrNumber(horseLevelName, horseLevel) + ").");

                }

                //compatibility warnings for height
                if (riderHeight != null && horseHeight != null && horseHeight - riderHeight > 55) {
                    messages.add("Compatibility warning: " + horseName + " is much taller than " + riderName + ".");
                }

                //and for weight
                if (riderWeight != null && horseWeight != null && riderWeight > horseWeight * 0.2) {
                    messages.add("Compatibility warning: " + riderName + " may be too heavy for " + horseName + ".");
                }
            }
        });
        return messages;
    }

    //some validation classes for the Insertion methods:
    //Class takes string-string map, and returns exception IS REQUIRED if it doesn't exist
    private static String required(Map<String, String> values, String field) throws ValidationException {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required.");
        }
        return value.trim();
    }

    //returns null if empty string is returned, and takes field for use in error message
    //returns trimmed Integer object if one is passed into the function
    private static Integer optionalInt(String raw, String field) throws ValidationException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a number.");
        }
    }

    //Mandatory equivalent of `required` for ints
    private static int requireInt(String raw, String field) throws ValidationException {
        Integer value = optionalInt(raw, field);
        if (value == null) {
            throw new ValidationException(field + " is required.");
        }
        return value;
    }

    //Mandatory equivalent of `required` for duration -- Used in sessions
    private static int requireDuration(String raw, String field) throws ValidationException {
        int value = requireInt(raw, field);
        if (value < 30 || value > 60) {
            throw new ValidationException(field + " must be between 30 and 60 minutes.");
        }
        return value;
    }

    //Mandatory equivalent of `required` for startdatetime -- Used when inputting sessions, lessons etc
    private static String requireDateTime(String raw, String field) throws ValidationException {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(field + " is required.");
        }
        try {
            //Can format date-time string
            return LocalDateTime.parse(raw.trim(), DatabaseManager.DATE_TIME_FORMATTER)
                    .format(DatabaseManager.DATE_TIME_FORMATTER);
        } catch (Exception e) {
            throw new ValidationException(field + " must use yyyy-MM-dd HH:mm format.");
        }
    }

    //generic method for comparing integer object with a minimum and maximum. used for some field inputs
    //throws validation exception
    private static void validateRange(Integer value, int min, int max, String field) throws ValidationException {
        if (value != null && (value < min || value > max)) {
            throw new ValidationException(field + " must be between " + min + " and " + max + ".");
        }
    }

    //Mandatory equivalent of `required` for horse object -- Used when inputting new horse
    private static void validateHorse(Map<String, String> values) throws ValidationException {
        required(values, "Name");
        validateRange(optionalInt(values.get("HorseLevelID"), "HorseLevelID"), 1, 4, "HorseLevelID");
        validateRange(optionalInt(values.get("Height_cm"), "Height_cm"), 50, 300, "Height_cm");
        validateRange(optionalInt(values.get("Weight_kg"), "Weight_kg"), 50, 500, "Weight_kg");
    }

    //Mandatory equivalent of `required` for person object -- Used when inputting new person
    private static void validatePerson(Map<String, String> values) throws ValidationException {
        String type = required(values, "Type");
        if (!Set.of("Coach", "Rider").contains(type)) {
            throw new ValidationException("Type must be Coach or Rider.");
        }
        required(values, "First_name");
        required(values, "Last_name");
        validateRange(optionalInt(values.get("RidersLevelID"), "RidersLevelID"), 1, 4, "RidersLevelID");
        validateRange(optionalInt(values.get("Height_cm"), "Height_cm"), 50, 250, "Height_cm");
        validateRange(optionalInt(values.get("Weight_kg"), "Weight_kg"), 50, 250, "Weight_kg");
    }

    //Mandatory equivalent of `required` for lesson  -- Used when inputting new lesson
    private static void validateLesson(Map<String, String> values) throws ValidationException {
        requireInt(values.get("CoachID"), "CoachID");
        requireDateTime(values.get("StartTime"), "StartTime");
        requireDuration(values.get("Duration"), "Duration");
    }
    //Mandatory equivalent of `required` for session object -- Used when inputting new session
    private static void validateSession(Map<String, String> values) throws ValidationException {
        requireInt(values.get("HorseID"), "HorseID");
        requireInt(values.get("TypeID"), "TypeID");


        if (values.containsKey("LessonID") && values.get("LessonID") != null && !values.get("LessonID").isBlank()) {
            requireInt(values.get("LessonID"), "LessonID");
        }

        requireDateTime(values.get("StartTime"), "StartTime");
        requireDuration(values.get("Duration"), "Duration");
    }

    //check if a row exists in any table
    private static boolean rowExists(String tableName, String idColumn, int id) throws SQLException {
        try (Connection connection = connectdatabase(); PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM " + tableName + " WHERE " + idColumn + " = ? LIMIT 1")) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    //method for checking if something exists; If not, throws validationmessage
    private static void validateExists(String tableName, String idColumn, int id, String message)
            throws SQLException, ValidationException {
        if (!rowExists(tableName, idColumn, id)) {
            throw new ValidationException(message);
        }
    }

    //Checks if a lesson exists using validateExists using lessonID
    private static void validateLessonExists(int lessonId) throws SQLException, ValidationException {
        validateExists("lessons", "LessonID", lessonId, "Lesson does not exist.");
    }
    //Checks if a horse exists using validateExists using horseID
    private static void validateHorseExists(int horseId) throws SQLException, ValidationException {
        validateExists("horses", "HorseID", horseId, "Horse does not exist.");
    }

    //Checks if a rider exists using validateExists using personID
    private static void validateRiderExists(int personId) throws SQLException, ValidationException {
        validateExists("people", "PersonID", personId, "Rider does not exist.");
        try (Connection connection = connectdatabase(); PreparedStatement ps = connection.prepareStatement(
                "SELECT Type FROM people WHERE PersonID = ?")) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"Rider".equals(rs.getString("Type"))) {
                    throw new ValidationException("Selected person is not a rider.");
                }
            }
        }
    }

    //executes SQL update
    private static void executeUpdate(String sql, SqlBinder binder) throws SQLException {
        try (Connection connection = connectdatabase(); PreparedStatement ps = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(ps);
            }
            ps.executeUpdate();
        }
    }

    //insert horse into database
    //uses Map instead of a long method signature -- good practice
    //ex map:
    //{
    //    "Name" : "Thunder",
    //    "HorseLevelID" : "3",
    //    "Height_cm" : "165",
    //    "Weight_kg" : "500"
    //}

    //Inserts a new horse into the Horses table; values Map comes from UI input
    public static void insertHorse(Map<String, String> values) throws SQLException, ValidationException {
        validateHorse(values);
        String importNumber = values.get("Import_number");

        //uses Required here as name is mandatory
        String name = required(values, "Name");

        //uses optionalInt here to enforce integer use
        Integer bridleNumber = optionalInt(values.get("Bridle_number"), "Bridle_number");
        Integer horseLevel = optionalInt(values.get("HorseLevelID"), "HorseLevelID");
        Integer height = optionalInt(values.get("Height_cm"), "Height_cm");
        Integer weight = optionalInt(values.get("Weight_kg"), "Weight_kg");
        String color = values.get("Color");
        String stableNumber = values.get("Stable_number");
        String joinDate = values.get("Join_date");
        String remarks = values.get("Remarks");
        executeUpdate("""
                INSERT INTO horses (Import_number, Name, Bridle_number, HorseLevelID, Height_cm, Weight_kg, Color,
                                     Stable_number, Last_update, Join_date, Remarks)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), ?, ?)
                """, ps -> {

            //trim inpt
            nullastring(ps, 1, importNumber);
            ps.setString(2, name);
            nullableInt(ps, 3, bridleNumber);
            nullableInt(ps, 4, horseLevel);
            nullableInt(ps, 5, height);
            nullableInt(ps, 6, weight);
            nullastring(ps, 7, color);
            nullastring(ps, 8, stableNumber);
            nullastring(ps, 9, joinDate);
            nullastring(ps, 10, remarks);
        });
    }

    //method to insert a person -- Same as previous method
    public static void insertPerson(Map<String, String> values) throws SQLException, ValidationException {
        validatePerson(values);
        String type = required(values, "Type");
        String firstName = required(values, "First_name");
        String lastName = required(values, "Last_name");
        Integer height = optionalInt(values.get("Height_cm"), "Height_cm");
        Integer weight = optionalInt(values.get("Weight_kg"), "Weight_kg");
        Integer riderLevel = optionalInt(values.get("RidersLevelID"), "RidersLevelID");
        String joinDate = values.get("Join_date");
        String email = values.get("Email");
        String remarks = values.get("Remarks");
        executeUpdate("""
                
                INSERT INTO people (Type, First_name, Last_name, Height_cm, Weight_kg, RidersLevelID, Last_update,
                                     Join_date, Email, Remarks)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'), ?, ?, ?)
                """, ps -> {

            //uses lambda function defined in @FunctionalInterface at the bottom of this script

            ps.setString(1, type);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            nullableInt(ps, 4, height);
            nullableInt(ps, 5, weight);
            nullableInt(ps, 6, riderLevel);
            nullastring(ps, 7, joinDate);
            nullastring(ps, 8, email);
            nullastring(ps, 9, remarks);
        });
    }

    //Same for inserting lessons
    public static void insertLesson(int coachId, String startTime, String duration) throws SQLException, ValidationException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CoachID", String.valueOf(coachId));
        values.put("StartTime", startTime);
        values.put("Duration", duration);
        validateLesson(values);
        String parsedStartTime = requireDateTime(startTime, "StartTime");
        int parsedDuration = requireDuration(duration, "Duration");
        executeUpdate("INSERT INTO lessons (CoachID, StartTime, Duration) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, coachId);
            ps.setString(2, parsedStartTime);
            ps.setInt(3, parsedDuration);
        });
    }

    //same for inserting sessions
    public static void insertSession(int horseId, int typeId, Integer lessonId, String startTime, String duration)
            throws SQLException, ValidationException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("HorseID", String.valueOf(horseId));
        values.put("TypeID", String.valueOf(typeId));
        values.put("LessonID", lessonId == null ? "" : String.valueOf(lessonId));
        values.put("StartTime", startTime);
        values.put("Duration", duration);
        validateSession(values);
        String parsedStartTime = requireDateTime(startTime, "StartTime");
        int parsedDuration = requireDuration(duration, "Duration");
        executeUpdate("INSERT INTO sessions (HorseID, TypeID, LessonID, StartTime, Duration) VALUES (?, ?, ?, ?, ?)",
                ps -> {
                    ps.setInt(1, horseId);
                    ps.setInt(2, typeId);
                    if (lessonId == null) {
                        ps.setNull(3, Types.INTEGER);
                    } else {
                        ps.setInt(3, lessonId);
                    }
                    ps.setString(4, parsedStartTime);
                    ps.setInt(5, parsedDuration);
                });
    }

    //Adds a lesson participant to a lesson
    public static void addLessonParticipant(int lessonId, int personId, int horseId)
            throws SQLException, ValidationException {

        //validate lessson, horse, rider exixts
        validateLessonExists(lessonId);
        validateHorseExists(horseId);
        validateRiderExists(personId);

        //uses lambda function from @FunctionalInterface
        executeUpdate("INSERT OR REPLACE INTO lesson_participants (LessonID, PersonID, HorseID) VALUES (?, ?, ?)",
                ps -> {
                    ps.setInt(1, lessonId);
                    ps.setInt(2, personId);
                    ps.setInt(3, horseId);
                });
    }

    //converts a String-Object Map into a String-String Map
    private static Map<String, String> toStringMap(Map<String, Object> values) {
        Map<String, String> result = new LinkedHashMap<>();

        //Iterate through all objects and convert to string
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            result.put(entry.getKey(), Objects.toString(entry.getValue(), ""));
        }
        return result;
    }

    //validate a row after it has been merged, used in updateRow
    private static void validateMergedRow(String tableName, Map<String, Object> mergedValues) throws ValidationException {
        //Convert String-Object mergedValues to a String-String Map
        Map<String, String> values = toStringMap(mergedValues);
        switch (tableName) {

            //Check all cases in case the horse/person/lesson/session doesn't exist
            case "horses" -> validateHorse(values);
            case "people" -> validatePerson(values);
            case "lessons" -> validateLesson(values);
            case "sessions" -> validateSession(values);
            default -> {
            }
        }
    }

    //method to update row in a table
    public static void updateRow(String tableName, String idColumn, int id, Map<String, Object> values)
            throws SQLException, ValidationException {
        //end if there is nothing to add
        if (values.isEmpty()) {
            return;
        }

        //load existing row
        Map<String, Object> merged = new LinkedHashMap<>(loadRow(tableName, idColumn, id));

        //apply changes
        merged.putAll(values);

        //use validation method to validate new row
        validateMergedRow(tableName, merged);

        //SQL updating code
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        List<Object> params = new ArrayList<>();

        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {

            //ignore if primary key is the same
            if (entry.getKey().equals(idColumn)) {
                continue;
            }
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(entry.getKey()).append(" = ?");
            params.add(entry.getValue());
        }
        sql.append(" WHERE ").append(idColumn).append(" = ?");
        params.add(id);

        //execute SQL update
        executeUpdate(sql.toString(), ps -> bindParams(ps, params));
    }

    //A couple of warning helper functions for use in analyzeDependencies method

    //Find stable exit warnings based on two horses
    private static void addStableExitWarnings(List<WarningRecord> warnings, Map<String, String> overrides,
                                              boolean includeOverridden) throws SQLException {
        String sql = """
                SELECT h.HorseID,
                       h.Name,
                       date(s.StartTime) AS SessionDay,
                       SUM(CASE WHEN CAST(strftime('%H', s.StartTime) AS INTEGER) < 12 THEN 1 ELSE 0 END) AS MorningCount,
                       SUM(CASE WHEN CAST(strftime('%H', s.StartTime) AS INTEGER) >= 12 THEN 1 ELSE 0 END) AS AfternoonCount
                FROM horses h
                LEFT JOIN sessions s ON s.HorseID = h.HorseID
                GROUP BY h.HorseID, h.Name, SessionDay
                HAVING SessionDay IS NOT NULL
                ORDER BY h.Name, SessionDay
                """;
        withResultSet(sql, null, rs -> {
            while (rs.next()) {
                int horseId = rs.getInt("HorseID");
                String day = rs.getString("SessionDay");
                int morning = rs.getInt("MorningCount");
                int afternoon = rs.getInt("AfternoonCount");
                if (morning != 1) {
                    addWarning(warnings, overrides, includeOverridden,
                            key("horse-morning", horseId, day), "Warning",
                            rs.getString("Name") + " has " + morning + " morning stable exits on " + day + ".");
                }
                if (afternoon != 1) {
                    addWarning(warnings, overrides, includeOverridden,
                            key("horse-afternoon", horseId, day), "Warning",
                            rs.getString("Name") + " has " + afternoon + " afternoon stable exits on " + day + ".");
                }
            }
        });
    }

    //Add warnings if a coach is double-booked
    private static void addCoachOverlapWarnings(List<WarningRecord> warnings, Map<String, String> overrides,
                                                boolean includeOverridden) throws SQLException {
        String sql = """
                WITH commitments AS (
                    SELECT l.CoachID AS PersonID,
                           'teaching' AS Role,
                           'teach:' || l.LessonID AS CommitmentKey,
                           l.LessonID AS EventID,
                           l.StartTime AS StartTime,
                           l.Duration AS Duration
                    FROM lessons l
                    UNION ALL
                    SELECT lp.PersonID AS PersonID,
                           'riding' AS Role,
                           'ride:' || lp.LessonID || ':' || lp.PersonID AS CommitmentKey,
                           lp.LessonID AS EventID,
                           l.StartTime AS StartTime,
                           l.Duration AS Duration
                    FROM lesson_participants lp
                    JOIN lessons l ON l.LessonID = lp.LessonID
                )
                SELECT c1.PersonID,
                       p.First_name || ' ' || p.Last_name AS PersonName,
                       c1.Role AS RoleA,
                       c1.EventID AS EventA,
                       c2.Role AS RoleB,
                       c2.EventID AS EventB
                FROM commitments c1
                JOIN commitments c2 ON c1.PersonID = c2.PersonID
                                   AND c1.CommitmentKey < c2.CommitmentKey
                JOIN people p ON p.PersonID = c1.PersonID
                WHERE datetime(c1.StartTime) < datetime(c2.StartTime, '+' || c2.Duration || ' minutes')
                  AND datetime(c2.StartTime) < datetime(c1.StartTime, '+' || c1.Duration || ' minutes')
                """;

        //Run function
        withResultSet(sql, null, rs -> {
            while (rs.next()) {

                //Add warning
                addWarning(warnings, overrides, includeOverridden,
                        key("coach-overlap", rs.getInt("PersonID"), rs.getString("RoleA"), rs.getInt("EventA"),
                                rs.getString("RoleB"), rs.getInt("EventB")),
                        "Error",
                        rs.getString("PersonName") + " has overlapping teaching/riding commitments (" +
                                rs.getString("RoleA") + " lesson #" + rs.getInt("EventA") + " and " +
                                rs.getString("RoleB") + " lesson #" + rs.getInt("EventB") + ").");
            }
        });
    }

    //Identify rider compatibility warnings
    private static void addRiderCompatibilityWarnings(List<WarningRecord> warnings, Map<String, String> overrides,
                                                      boolean includeOverridden) throws SQLException {
        String sql = """
                SELECT lp.LessonID, lp.PersonID, lp.HorseID,
                       p.First_name || ' ' || p.Last_name AS RiderName,
                       h.Name AS HorseName,
                       p.RidersLevelID AS RiderLevel,
                       rl.Description AS RiderLevelName,
                       h.HorseLevelID AS HorseLevel,
                       hl.Description AS HorseLevelName,
                       p.Height_cm AS RiderHeight,
                       h.Height_cm AS HorseHeight,
                       p.Weight_kg AS RiderWeight,
                       h.Weight_kg AS HorseWeight
                FROM lesson_participants lp
                JOIN people p ON p.PersonID = lp.PersonID
                JOIN horses h ON h.HorseID = lp.HorseID
                LEFT JOIN rider_level rl ON rl.RidersLevelID = p.RidersLevelID
                LEFT JOIN horse_level hl ON hl.HorseLevelID = h.HorseLevelID
                """;

        //run function
        withResultSet(sql, null, rs -> {
            while (rs.next()) {

                //define integer, string values from completed SQL
                Integer riderLevel = nullableInt(rs, "RiderLevel");
                Integer horseLevel = nullableInt(rs, "HorseLevel");
                Integer riderHeight = nullableInt(rs, "RiderHeight");
                Integer horseHeight = nullableInt(rs, "HorseHeight");
                Integer riderWeight = nullableInt(rs, "RiderWeight");
                Integer horseWeight = nullableInt(rs, "HorseWeight");
                String riderLevelName = rs.getString("RiderLevelName");
                String horseLevelName = rs.getString("HorseLevelName");

                // Rider skill discrepancies
                if (riderLevel != null && horseLevel != null && riderLevel < horseLevel) {
                    addWarning(warnings, overrides, includeOverridden,
                            key("rider-skill", rs.getInt("LessonID"), rs.getInt("PersonID"), rs.getInt("HorseID")),
                            "Error",
                            rs.getString("RiderName") + " (" + labelOrNumber(riderLevelName, riderLevel) + ") is below the required level for "
                                    + rs.getString("HorseName") + " (" + labelOrNumber(horseLevelName, horseLevel) + ").");
                }

                //rider height requirement
                if (riderHeight != null && horseHeight != null && horseHeight - riderHeight > 55) {
                    addWarning(warnings, overrides, includeOverridden,
                            key("rider-size", rs.getInt("LessonID"), rs.getInt("PersonID"), rs.getInt("HorseID")),
                            "Warning",
                            rs.getString("HorseName") + " may be too large for " + rs.getString("RiderName") + ".");
                }

                //rider weight requirement
                if (riderWeight != null && horseWeight != null && riderWeight > horseWeight * 0.2) {
                    addWarning(warnings, overrides, includeOverridden,
                            key("rider-weight", rs.getInt("LessonID"), rs.getInt("PersonID"), rs.getInt("HorseID")),
                            "Warning",
                            rs.getString("RiderName") + " may be too heavy for " + rs.getString("HorseName") + ".");
                }
            }
        });
    }

    //rest hour warnings (11:00 - 2:00)
    private static void addRestHourWarnings(List<WarningRecord> warnings, Map<String, String> overrides,
                                            boolean includeOverridden) throws SQLException {
        String sql = """
                SELECT s.SessionID,
                       h.Name AS HorseName,
                       s.StartTime,
                       COALESCE((
                           SELECT GROUP_CONCAT(Name, ', ')
                           FROM (
                               SELECT DISTINCT p.First_name || ' ' || p.Last_name AS Name
                               FROM lesson_participants lp
                               JOIN people p ON p.PersonID = lp.PersonID
                               WHERE lp.LessonID = s.LessonID
                               ORDER BY Name
                           )
                       ), '') AS Riders
                FROM sessions s
                JOIN horses h ON h.HorseID = s.HorseID
                WHERE CAST(strftime('%H', s.StartTime) AS INTEGER) IN (12, 13)
                """;
        withResultSet(sql, null, rs -> {
            while (rs.next()) {
                String riders = Objects.toString(rs.getString("Riders"), "");
                addWarning(warnings, overrides, includeOverridden,
                        key("rest-hours", rs.getInt("SessionID")), "Error",
                        rs.getString("HorseName")
                                + (riders.isBlank() ? "" : " with riders " + riders)
                                + " has a session during stable rest hours at " + rs.getString("StartTime") + ".");
            }
        });
    }

    //Lessons over 60 minutes or under 30 minutes
    private static void addDurationWarnings(List<WarningRecord> warnings, Map<String, String> overrides,
                                            boolean includeOverridden) throws SQLException {
        String sql = """
                SELECT 'lesson' AS Source, LessonID AS RowID, Duration FROM lessons
                UNION ALL
                SELECT 'session' AS Source, SessionID AS RowID, Duration FROM sessions
                """;
        withResultSet(sql, null, rs -> {
            while (rs.next()) {
                int duration = rs.getInt("Duration");
                if (duration < 30 || duration > 60) {
                    addWarning(warnings, overrides, includeOverridden,
                            key("duration", rs.getString("Source"), rs.getInt("RowID")), "Error",
                            rs.getString("Source") + " #" + rs.getInt("RowID") + " has invalid duration " + duration + " minutes.");
                }
            }
        });
    }

    //Horses jumping too many times in a week
    private static void addJumpWarnings(List<WarningRecord> warnings, Map<String, String> overrides,
                                        boolean includeOverridden) throws SQLException {
        String sql = """
                SELECT s.HorseID, h.Name, COUNT(*) AS JumpCount
                FROM sessions s
                JOIN horses h ON h.HorseID = s.HorseID
                JOIN session_types st ON st.TypeID = s.TypeID
                WHERE lower(st.Name) LIKE '%jump%'
                  AND datetime(s.StartTime) >= datetime('now', '-7 days')
                GROUP BY s.HorseID, h.Name
                HAVING COUNT(*) > 3
                """;
        withResultSet(sql, null, rs -> {
            while (rs.next()) {
                addWarning(warnings, overrides, includeOverridden,
                        key("jump-load", rs.getInt("HorseID")), "Warning",
                        rs.getString("Name") + " has " + rs.getInt("JumpCount") + " jump sessions in the last 7 days.");
            }
        });
    }

    //Analyzes discrepancies in the code
    public static List<WarningRecord> analyzeDiscrepancies(boolean includeOverridden) throws SQLException {
        Map<String, String> overrides = getOverrides();
        List<WarningRecord> warnings = new ArrayList<>();
        addStableExitWarnings(warnings, overrides, includeOverridden);
        addCoachOverlapWarnings(warnings, overrides, includeOverridden);
        addRiderCompatibilityWarnings(warnings, overrides, includeOverridden);
        addRestHourWarnings(warnings, overrides, includeOverridden);
        addDurationWarnings(warnings, overrides, includeOverridden);
        addJumpWarnings(warnings, overrides, includeOverridden);
        return warnings;
    }

    //Override discrepancies in the code
    public static void overrideWarning(String warningKey, String reason) throws SQLException, ValidationException {
        //if nothing is selected
        if (warningKey == null || warningKey.isBlank()) {
            throw new ValidationException("Select a warning before overriding it.");
        }

        //if no reason is given
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("An override reason is required.");
        }

        //execute update
        executeUpdate("""
                INSERT INTO warning_overrides (WarningKey, Reason, OverriddenAt)
                VALUES (?, ?, datetime('now'))
                ON CONFLICT(WarningKey) DO UPDATE SET -- On Conflict resolves primary key issues gracefully
                    Reason = excluded.Reason,
                    OverriddenAt = excluded.OverriddenAt
                """, ps -> {
            ps.setString(1, warningKey);
            ps.setString(2, reason.trim());
        });
    }

    //Execute a query, optionally bind parameters, read the results, and return them as a QueryResult
    //Overload
    private static QueryResult query(String sql, List<String> columns, String idColumn, SqlBinder binder)
            throws SQLException {
        return query(sql, columns, idColumn, null, binder);
    }
    private static QueryResult query(String sql, List<String> columns, String idColumn, List<Object> params,
                                     SqlBinder binder) throws SQLException {
        try (Connection connection = connectdatabase(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            if (binder != null) {
                binder.bind(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return readResultSet(rs, columns, idColumn);
            }
        }
    }

    //Load a row into
    private static Map<String, Object> loadRow(String tableName, String idColumn, int id) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        try (Connection connection = connectdatabase(); PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Row not found.");
                }
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
            }
        }
        return row;
    }
    private static QueryResult readResultSet(ResultSet rs, List<String> columns, String idColumn) throws SQLException {
        List<List<Object>> rows = new ArrayList<>();
        List<Integer> rowIds = new ArrayList<>();
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        while (rs.next()) {
            rowIds.add(rs.getInt(idColumn));
            List<Object> row = new ArrayList<>();
            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (String column : columns) {
                Object value = rs.getObject(column);
                row.add(value);
                rowMap.put(column, value);
            }
            rows.add(row);
            rowMaps.add(rowMap);
        }
        return new QueryResult(columns, rows, rowIds, rowMaps);
    }
    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }


    //Gets warning overrides from SQL database
    private static Map<String, String> getOverrides() throws SQLException {
        Map<String, String> overrides = new LinkedHashMap<>();
        withResultSet("SELECT WarningKey, Reason FROM warning_overrides", null, rs -> {
            while (rs.next()) {
                overrides.put(rs.getString("WarningKey"), rs.getString("Reason"));
            }
        });
        return overrides;
    }

    //adds WarningRecord object to warnings list
    private static void addWarning(List<WarningRecord> warnings, Map<String, String> overrides, boolean includeOverridden,
                                   String key, String severity, String message) {
        boolean overridden = overrides.containsKey(key);
        if (!includeOverridden && overridden) {
            return;
        }
        warnings.add(new WarningRecord(key, severity, message, overridden, overrides.get(key)));
    }

    //Returns label (value), used in some user notification functions
    private static String labelOrNumber(String label, Integer value) {
        if (label == null || label.isBlank()) {
            return String.valueOf(value);
        }
        return label + " (" + value + ")";
    }

    //Varargs parameter Object...
    //Returns Object:Object:Object... string
    private static String key(Object... parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    //returns query for summary of all lessons
    private static QueryResult queryLessonSummary(String whereClause, SqlBinder binder, String orderBy) throws SQLException {
        String sql = """
                SELECT l.LessonID,
                       l.StartTime,
                       l.Duration,
                       p.First_name || ' ' || p.Last_name AS Coach,
                       COALESCE((
                           SELECT GROUP_CONCAT(Name, ', ')
                           FROM (
                               SELECT DISTINCT h.Name AS Name
                               FROM sessions s
                               JOIN horses h ON h.HorseID = s.HorseID
                               WHERE s.LessonID = l.LessonID
                               ORDER BY Name
                           )
                       ), '') AS Horses,
                       COALESCE((
                           SELECT GROUP_CONCAT(Name, ', ')
                           FROM (
                               SELECT DISTINCT p2.First_name || ' ' || p2.Last_name AS Name
                               FROM lesson_participants lp
                               JOIN people p2 ON p2.PersonID = lp.PersonID
                               WHERE lp.LessonID = l.LessonID
                               ORDER BY Name
                           )
                       ), '') AS Riders
                FROM lessons l
                JOIN people p ON p.PersonID = l.CoachID
                """
                + (whereClause == null ? "" : whereClause + "\n")
                + "ORDER BY " + orderBy;
        return query(sql, List.of("StartTime", "Duration", "Coach", "Horses", "Riders"), "LessonID", binder);
    }
    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
    @FunctionalInterface
    private interface ResultSetConsumer {
        void accept(ResultSet rs) throws SQLException;
    }
}
