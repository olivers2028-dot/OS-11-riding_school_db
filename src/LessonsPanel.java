import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LessonsPanel extends JPanel {
    private final WarningsStrip warnings = new WarningsStrip("lesson");
    private final JComboBox<String> viewCombo = new JComboBox<>(new String[]{"All", "Past", "Now"});
    private final DefaultTableModel lessonModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable lessonTable = UIStyles.styleTable(new JTable(lessonModel));
    private final JTextField lessonIdField = new JTextField(8);
    private final JComboBox<NamedEntity> coachCombo = new JComboBox<>();
    private final JTextField lessonStartField = new JTextField(16);
    private final JTextField lessonDurationField = new JTextField("60", 8);
    private final JComboBox<NamedEntity> riderCombo = new JComboBox<>();
    private final JComboBox<NamedEntity> horseCombo = new JComboBox<>();
    private final JComboBox<NamedEntity> sessionHorseCombo = new JComboBox<>();
    private final JComboBox<NamedEntity> sessionTypeCombo = new JComboBox<>();
    private final JTextField sessionLessonField = new JTextField(8);
    private final JTextField sessionStartField = new JTextField(16);
    private final JTextField sessionDurationField = new JTextField("60", 8);
    private final JTextArea compatibilityArea = new JTextArea(5, 24);
    private int selectedLessonId = 0;

    public LessonsPanel() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UIStyles.pageColor());

        JPanel content = UIStyles.createSectionPanel("Lessons");
        content.add(buildMain(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        loadChoices();
        loadLessons();
    }

    private Component buildMain() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setOpaque(false);
        root.add(warnings, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLessonTablePane(), buildDetailPane());
        split.setResizeWeight(0.6);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private Component buildLessonTablePane() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton refresh = UIStyles.createButton("Refresh");
        refresh.addActionListener(e -> refreshAll());
        viewCombo.addActionListener(e -> loadLessons());
        controls.add(new JLabel("View:"));
        controls.add(viewCombo);
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);

        lessonTable.getSelectionModel().addListSelectionListener(e -> syncSelectedLesson());
        panel.add(new JScrollPane(lessonTable), BorderLayout.CENTER);
        return panel;
    }

    private Component buildDetailPane() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildLessonFormPane(), buildActionPane());
        split.setResizeWeight(0.45);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private Component buildLessonFormPane() {
        JPanel panel = UIStyles.createSectionPanel("Lesson Details");
        JPanel form = UIStyles.wrapForm(new GridBagLayout());
        GridBagConstraints gbc = constraints();
        lessonIdField.setEditable(false);
        addField(form, gbc, "Lesson ID", lessonIdField);
        addField(form, gbc, "Coach", coachCombo);
        addField(form, gbc, "StartTime", lessonStartField);
        addField(form, gbc, "Duration", lessonDurationField);
        panel.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton save = UIStyles.createButton("Save");
        JButton create = UIStyles.createButton("Create Lesson");
        save.addActionListener(e -> saveLesson());
        create.addActionListener(e -> createLesson());
        buttons.add(save);
        buttons.add(create);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private Component buildActionPane() {
        JPanel panel = UIStyles.createSectionPanel("Sessions and Riders");
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        inner.add(sectionTitle("Create Session"));
        inner.add(sessionForm());
        inner.add(Box.createVerticalStrut(10));
        inner.add(sectionTitle("Assign Rider and Horse"));
        inner.add(assignmentForm());
        inner.add(Box.createVerticalStrut(10));
        inner.add(sectionTitle("Compatibility"));
        compatibilityArea.setEditable(false);
        compatibilityArea.setLineWrap(true);
        compatibilityArea.setWrapStyleWord(true);
        inner.add(new JScrollPane(compatibilityArea));

        panel.add(new JScrollPane(inner), BorderLayout.CENTER);
        return panel;
    }

    private Component sessionForm() {
        JPanel form = UIStyles.wrapForm(new GridBagLayout());
        GridBagConstraints gbc = constraints();
        addField(form, gbc, "Horse", sessionHorseCombo);
        addField(form, gbc, "Session Type", sessionTypeCombo);
        sessionLessonField.setEditable(false);
        addField(form, gbc, "Lesson ID", sessionLessonField);
        addField(form, gbc, "StartTime", sessionStartField);
        addField(form, gbc, "Duration", sessionDurationField);
        JButton create = UIStyles.createButton("Create Session");
        create.addActionListener(e -> createSession());
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(create, gbc);
        return form;
    }

    private Component assignmentForm() {
        JPanel form = UIStyles.wrapForm(new GridBagLayout());
        GridBagConstraints gbc = constraints();
        addField(form, gbc, "Rider", riderCombo);
        addField(form, gbc, "Horse", horseCombo);
        JButton assign = UIStyles.createButton("Add Rider To Lesson");
        assign.addActionListener(e -> addParticipant());
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(assign, gbc);

        riderCombo.addActionListener(e -> updateCompatibility());
        horseCombo.addActionListener(e -> updateCompatibility());
        return form;
    }

    private void loadChoices() {
        try {
            fillCombo(coachCombo, DatabaseManager.getNamedPeople("Coach"));
            fillCombo(riderCombo, DatabaseManager.getNamedPeople("Rider"));
            fillCombo(horseCombo, DatabaseManager.getNamedHorses());
            fillCombo(sessionHorseCombo, DatabaseManager.getNamedHorses());
            fillCombo(sessionTypeCombo, DatabaseManager.getSessionTypes());
        } catch (Exception e) {
            showError("Unable to load choices", e);
        }
        updateCompatibility();
    }

    private void loadLessons() {
        try {
            QueryResult result = DatabaseManager.getLessonsOverview((String) viewCombo.getSelectedItem());
            loadTable(lessonModel, result);
        } catch (Exception e) {
            showError("Unable to load lessons", e);
        }
    }

    private void syncSelectedLesson() {
        int row = lessonTable.getSelectedRow();
        if (row < 0 || lessonModel.getColumnCount() == 0) {
            return;
        }
        Object id = lessonTable.getValueAt(row, 0);
        if (!(id instanceof Number number)) {
            return;
        }
        selectedLessonId = number.intValue();
        lessonIdField.setText(String.valueOf(selectedLessonId));
        sessionLessonField.setText(String.valueOf(selectedLessonId));
        lessonStartField.setText(textAt(row, "StartTime"));
        lessonDurationField.setText(textAt(row, "Duration"));
        selectComboByLabel(coachCombo, textAt(row, "Coach"));
        sessionStartField.setText(textAt(row, "StartTime"));
    }

    private void saveLesson() {
        if (selectedLessonId == 0) {
            JOptionPane.showMessageDialog(this, "Select a lesson first.");
            return;
        }
        NamedEntity coach = (NamedEntity) coachCombo.getSelectedItem();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("CoachID", coach == null ? null : coach.getId());
        values.put("StartTime", lessonStartField.getText());
        values.put("Duration", lessonDurationField.getText());
        try {
            DatabaseManager.updateRow("lessons", "LessonID", selectedLessonId, values);
            refreshAll();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Lesson update failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createLesson() {
        NamedEntity coach = (NamedEntity) coachCombo.getSelectedItem();
        if (coach == null) {
            JOptionPane.showMessageDialog(this, "Select a coach.");
            return;
        }
        try {
            DatabaseManager.insertLesson(coach.getId(), lessonStartField.getText(), lessonDurationField.getText());
            refreshAll();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Lesson creation failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createSession() {
        NamedEntity horse = (NamedEntity) sessionHorseCombo.getSelectedItem();
        NamedEntity type = (NamedEntity) sessionTypeCombo.getSelectedItem();
        Integer lessonId = selectedLessonId == 0 ? null : selectedLessonId;
        if (horse == null || type == null) {
            JOptionPane.showMessageDialog(this, "Select a horse and session type.");
            return;
        }
        try {
            List<String> compatibilityMessages = lessonId == null
                    ? List.of()
                    : DatabaseManager.getLessonCompatibilityMessages(lessonId, horse.getId());
            DatabaseManager.insertSession(horse.getId(), type.getId(), lessonId, sessionStartField.getText(),
                    sessionDurationField.getText());
            refreshAll();
            if (!compatibilityMessages.isEmpty()) {
                JOptionPane.showMessageDialog(this, String.join("\n", compatibilityMessages),
                        "Compatibility warning", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Session creation failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addParticipant() {
        if (selectedLessonId == 0) {
            JOptionPane.showMessageDialog(this, "Select a lesson first.");
            return;
        }
        NamedEntity rider = (NamedEntity) riderCombo.getSelectedItem();
        NamedEntity horse = (NamedEntity) horseCombo.getSelectedItem();
        if (rider == null || horse == null) {
            JOptionPane.showMessageDialog(this, "Select a rider and horse.");
            return;
        }
        try {
            List<String> compatibilityMessages = DatabaseManager.getCompatibilityMessages(rider.getId(), horse.getId());
            DatabaseManager.addLessonParticipant(selectedLessonId, rider.getId(), horse.getId());
            refreshAll();
            if (!compatibilityMessages.isEmpty()) {
                JOptionPane.showMessageDialog(this, String.join("\n", compatibilityMessages),
                        "Compatibility warning", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Assignment failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateCompatibility() {
        NamedEntity rider = (NamedEntity) riderCombo.getSelectedItem();
        NamedEntity horse = (NamedEntity) horseCombo.getSelectedItem();
        if (rider == null || horse == null) {
            compatibilityArea.setText("Select a rider and horse to evaluate compatibility.");
            return;
        }
        try {
            List<String> messages = DatabaseManager.getCompatibilityMessages(rider.getId(), horse.getId());
            compatibilityArea.setText(messages.isEmpty()
                    ? "No compatibility warnings."
                    : String.join("\n", messages));
            compatibilityArea.setCaretPosition(0);
        } catch (Exception e) {
            compatibilityArea.setText("Unable to evaluate compatibility: " + e.getMessage());
        }
    }

    private void refreshAll() {
        loadChoices();
        loadLessons();
        warnings.loadWarnings();
    }

    private GridBagConstraints constraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void addField(JPanel form, GridBagConstraints gbc, String label, JComponent field) {
        gbc.gridx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        form.add(field, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
    }

    private JPanel sectionTitle(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    private void fillCombo(JComboBox<NamedEntity> combo, List<NamedEntity> items) {
        combo.removeAllItems();
        for (NamedEntity item : items) {
            combo.addItem(item);
        }
    }

    private void selectComboByLabel(JComboBox<NamedEntity> combo, String label) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).toString().equals(label)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private String textAt(int row, String columnName) {
        for (int column = 0; column < lessonModel.getColumnCount(); column++) {
            if (lessonModel.getColumnName(column).equals(columnName)) {
                Object value = lessonTable.getValueAt(row, column);
                return value == null ? "" : value.toString();
            }
        }
        return "";
    }

    private void loadTable(DefaultTableModel target, QueryResult result) {
        target.setRowCount(0);
        target.setColumnCount(0);
        for (String column : result.getColumns()) {
            target.addColumn(column);
        }
        for (List<Object> row : result.getRows()) {
            target.addRow(row.toArray());
        }
    }

    private void showError(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + ": " + e.getMessage(), "Database error", JOptionPane.ERROR_MESSAGE);
    }
}
