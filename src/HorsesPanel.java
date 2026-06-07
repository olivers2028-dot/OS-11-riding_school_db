import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HorsesPanel extends JPanel {
    private final WarningsStrip warnings = new WarningsStrip("horse");
    private final DefaultTableModel horseModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable horseTable = UIStyles.styleTable(new JTable(horseModel));
    private final JPanel columnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    private final List<JCheckBox> columnChecks = new ArrayList<>();
    private final JComboBox<NamedEntity> horseCombo = new JComboBox<>();
    private final DefaultTableModel scheduleModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable scheduleTable = UIStyles.styleTable(new JTable(scheduleModel));
    private final Map<String, JTextField> formFields = new LinkedHashMap<>();

    public HorsesPanel() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UIStyles.pageColor());

        JPanel content = UIStyles.createSectionPanel("Horses");
        content.add(buildMain(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        loadHorses();
        loadHorseChoices();
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
        panel.add(new JScrollPane(horseTable), BorderLayout.CENTER);

        horseTable.getSelectionModel().addListSelectionListener(e -> syncHorseSelection());
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
        JPanel panel = UIStyles.createSectionPanel("Horse Schedule");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton refresh = UIStyles.createButton("Refresh Schedule");
        refresh.addActionListener(e -> loadSchedule());
        horseCombo.addActionListener(e -> loadSchedule());
        controls.add(new JLabel("Horse:"));
        controls.add(horseCombo);
        controls.add(new JLabel("Past week"));
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JLabel("Includes lessons and unridden sessions."), BorderLayout.SOUTH);
        panel.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);
        return panel;
    }

    private Component buildCreatePane() {
        JPanel panel = UIStyles.createSectionPanel("New Horse");
        panel.setPreferredSize(new Dimension(0, 330));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        header.setOpaque(false);
        JButton submit = UIStyles.createButton("Submit Horse");
        submit.addActionListener(e -> createHorse());
        header.add(submit);
        panel.add(header, BorderLayout.NORTH);

        JPanel form = UIStyles.wrapForm(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        for (String field : List.of("Import_number", "Name", "Bridle_number", "HorseLevelID", "Height_cm",
                "Weight_kg", "Color", "Stable_number", "Join_date", "Remarks")) {
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

    private void loadHorses() {
        try {
            columnPanel.removeAll();
            columnChecks.clear();
            List<String> columns = DatabaseManager.getVisibleColumns("horses");
            for (String column : columns) {
                JCheckBox check = new JCheckBox(column, true);
                check.setOpaque(false);
                check.addActionListener(e -> refreshHorseTable());
                columnChecks.add(check);
                columnPanel.add(check);
            }
            columnPanel.revalidate();
            columnPanel.repaint();
            refreshHorseTable();
        } catch (Exception e) {
            showError("Unable to load horses", e);
        }
    }

    private void refreshHorseTable() {
        try {
            List<String> columns = selectedColumns();
            QueryResult result = DatabaseManager.getTableData("horses", "HorseID", columns, null, null, "Name");
            loadTable(horseModel, result);
        } catch (Exception e) {
            showError("Unable to load horse table", e);
        }
    }

    private void loadHorseChoices() {
        try {
            fillCombo(horseCombo, DatabaseManager.getNamedHorses());
        } catch (Exception e) {
            showError("Unable to load horse choices", e);
        }
    }

    private void loadSchedule() {
        NamedEntity horse = (NamedEntity) horseCombo.getSelectedItem();
        if (horse == null) {
            scheduleModel.setRowCount(0);
            scheduleModel.setColumnCount(0);
            return;
        }
        try {
            QueryResult result = DatabaseManager.getHorseSchedule(horse.getId(), 7);
            loadTable(scheduleModel, result);
        } catch (Exception e) {
            showError("Unable to load horse schedule", e);
        }
    }

    private void createHorse() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JTextField> entry : formFields.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getText());
        }
        try {
            DatabaseManager.insertHorse(values);
            clearForm();
            refreshAll();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Horse creation failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        for (JTextField field : formFields.values()) {
            field.setText("");
        }
    }

    private void refreshAll() {
        loadHorses();
        loadHorseChoices();
        loadSchedule();
        warnings.loadWarnings();
    }

    private void syncHorseSelection() {
        int row = horseTable.getSelectedRow();
        if (row < 0 || horseModel.getColumnCount() == 0) {
            return;
        }
        Object idValue = horseTable.getValueAt(row, 0);
        if (!(idValue instanceof Number number)) {
            return;
        }
        for (int i = 0; i < horseCombo.getItemCount(); i++) {
            if (horseCombo.getItemAt(i).getId() == number.intValue()) {
                horseCombo.setSelectedIndex(i);
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
