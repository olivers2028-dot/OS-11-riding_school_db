import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CoachesPanel extends JPanel {
    private final WarningsStrip warnings = new WarningsStrip("coach");
    private final DefaultTableModel coachModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable coachTable = UIStyles.styleTable(new JTable(coachModel));
    private final JPanel columnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    private final List<JCheckBox> columnChecks = new ArrayList<>();
    private final JComboBox<NamedEntity> coachCombo = new JComboBox<>();
    private final JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(7, 1, 365, 1));
    private final DefaultTableModel scheduleModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable scheduleTable = UIStyles.styleTable(new JTable(scheduleModel));
    private final Map<String, JTextField> formFields = new LinkedHashMap<>();

    public CoachesPanel() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UIStyles.pageColor());

        JPanel content = UIStyles.createSectionPanel("Coaches");
        content.add(buildMain(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        loadCoaches();
        loadCoachChoices();
        loadSchedule();
    }

    private Component buildMain() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setOpaque(false);
        root.add(warnings, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildRawPane(), buildDetailPane());
        split.setResizeWeight(0.55);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private Component buildRawPane() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton refresh = UIStyles.createButton("Refresh");
        refresh.addActionListener(e -> refreshAll());
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);

        columnPanel.setOpaque(false);
        panel.add(columnPanel, BorderLayout.SOUTH);
        panel.add(new JScrollPane(coachTable), BorderLayout.CENTER);

        coachTable.getSelectionModel().addListSelectionListener(e -> syncCoachSelection());
        return panel;
    }

    private Component buildDetailPane() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(buildCreatePane(), BorderLayout.NORTH);
        panel.add(buildSchedulePane(), BorderLayout.CENTER);
        return panel;
    }

    private Component buildSchedulePane() {
        JPanel panel = UIStyles.createSectionPanel("Coach Schedule");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton refresh = UIStyles.createButton("Refresh Schedule");
        refresh.addActionListener(e -> loadSchedule());
        coachCombo.addActionListener(e -> loadSchedule());
        daysSpinner.addChangeListener(e -> loadSchedule());
        controls.add(new JLabel("Coach:"));
        controls.add(coachCombo);
        controls.add(new JLabel("Past days:"));
        controls.add(daysSpinner);
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);
        return panel;
    }

    private Component buildCreatePane() {
        JPanel panel = UIStyles.createSectionPanel("New Coach");
        panel.setPreferredSize(new Dimension(0, 290));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        header.setOpaque(false);
        JButton submit = UIStyles.createButton("Submit Coach");
        submit.addActionListener(e -> createCoach());
        header.add(submit);
        panel.add(header, BorderLayout.NORTH);

        JPanel form = UIStyles.wrapForm(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        for (String field : List.of("First_name", "Last_name", "Height_cm", "Weight_kg", "Email", "Join_date", "Remarks")) {
            JTextField textField = new JTextField(16);
            formFields.put(field, textField);
            form.add(new JLabel(field), gbc);
            gbc.gridx = 1;
            form.add(textField, gbc);
            gbc.gridx = 0;
            gbc.gridy++;
        }
        panel.add(new JScrollPane(form), BorderLayout.CENTER);
        return panel;
    }

    private void loadCoaches() {
        try {
            columnPanel.removeAll();
            columnChecks.clear();
            List<String> columns = DatabaseManager.getVisibleColumns("people");
            for (String column : columns) {
                JCheckBox check = new JCheckBox(column, true);
                check.setOpaque(false);
                check.addActionListener(e -> refreshCoachTable());
                columnChecks.add(check);
                columnPanel.add(check);
            }
            columnPanel.revalidate();
            columnPanel.repaint();
            refreshCoachTable();
        } catch (Exception e) {
            showError("Unable to load coaches", e);
        }
    }

    private void refreshCoachTable() {
        try {
            QueryResult result = DatabaseManager.getTableData(
                    "people", "PersonID", selectedColumns(), "Type = ?", List.of("Coach"), "First_name, Last_name");
            loadTable(coachModel, result);
        } catch (Exception e) {
            showError("Unable to load coach table", e);
        }
    }

    private void loadCoachChoices() {
        try {
            fillCombo(coachCombo, DatabaseManager.getNamedPeople("Coach"));
        } catch (Exception e) {
            showError("Unable to load coach choices", e);
        }
    }

    private void loadSchedule() {
        NamedEntity coach = (NamedEntity) coachCombo.getSelectedItem();
        if (coach == null) {
            scheduleModel.setRowCount(0);
            scheduleModel.setColumnCount(0);
            return;
        }
        try {
            QueryResult result = DatabaseManager.getCoachSchedule(coach.getId(), (Integer) daysSpinner.getValue());
            loadTable(scheduleModel, result);
        } catch (Exception e) {
            showError("Unable to load coach schedule", e);
        }
    }

    private void createCoach() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Type", "Coach");
        for (Map.Entry<String, JTextField> entry : formFields.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getText());
        }
        try {
            DatabaseManager.insertPerson(values);
            clearForm();
            refreshAll();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Coach creation failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        for (JTextField field : formFields.values()) {
            field.setText("");
        }
    }

    private void refreshAll() {
        loadCoaches();
        loadCoachChoices();
        loadSchedule();
        warnings.loadWarnings();
    }

    private void syncCoachSelection() {
        int row = coachTable.getSelectedRow();
        if (row < 0 || coachModel.getColumnCount() == 0) {
            return;
        }
        Object idValue = coachTable.getValueAt(row, 0);
        if (!(idValue instanceof Number number)) {
            return;
        }
        for (int i = 0; i < coachCombo.getItemCount(); i++) {
            if (coachCombo.getItemAt(i).getId() == number.intValue()) {
                coachCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private List<String> selectedColumns() {
        List<String> columns = new ArrayList<>();
        for (JCheckBox check : columnChecks) {
            if (check.isSelected()) {
                columns.add(check.getText());
            }
        }
        if (columns.isEmpty()) {
            for (JCheckBox check : columnChecks) {
                columns.add(check.getText());
            }
        }
        return columns;
    }

    private void fillCombo(JComboBox<NamedEntity> combo, List<NamedEntity> items) {
        combo.removeAllItems();
        for (NamedEntity item : items) {
            combo.addItem(item);
        }
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
