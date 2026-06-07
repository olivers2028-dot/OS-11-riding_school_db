//GENERATED WITH AI
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;

public class WarningsStrip extends JPanel {
    private final String scope;
    private final DefaultListModel<WarningRecord> model = new DefaultListModel<>();
    private final JList<WarningRecord> list = new JList<>(model);
    private final JCheckBox includeOverridden = new JCheckBox("Show overridden", true);

    public WarningsStrip(String scope) {
        this.scope = scope.toLowerCase(Locale.ROOT);
        setLayout(new BorderLayout(8, 8));
        setOpaque(false);

        JLabel title = new JLabel("Warnings");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.WEST);

        list.setVisibleRowCount(3);
        list.setCellRenderer(new WarningRenderer());
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        JButton refresh = UIStyles.createButton("Refresh");
        JButton override = UIStyles.createButton("Override Selected");
        refresh.addActionListener(e -> loadWarnings());
        override.addActionListener(e -> overrideSelected());
        includeOverridden.addActionListener(e -> loadWarnings());
        controls.add(includeOverridden);
        controls.add(refresh);
        controls.add(override);
        add(controls, BorderLayout.EAST);

        loadWarnings();
    }

    public void loadWarnings() {
        model.clear();
        try {
            List<WarningRecord> warnings = DatabaseManager.analyzeDiscrepancies(includeOverridden.isSelected());
            for (WarningRecord warning : warnings) {
                if (matchesScope(warning.getMessage())) {
                    model.addElement(warning);
                }
            }
            if (model.isEmpty()) {
                model.addElement(new WarningRecord("", "Info", "No current warnings for this screen.", false, null));
            }
        } catch (Exception e) {
            model.addElement(new WarningRecord("", "Error", "Unable to load warnings: " + e.getMessage(), false, null));
        }
    }

    private void overrideSelected() {
        WarningRecord warning = list.getSelectedValue();
        if (warning == null || warning.getKey() == null || warning.getKey().isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a warning first.");
            return;
        }
        String reason = JOptionPane.showInputDialog(this, "Override reason:");
        if (reason == null || reason.isBlank()) {
            return;
        }
        try {
            DatabaseManager.overrideWarning(warning.getKey(), reason);
            loadWarnings();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Override failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean matchesScope(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return switch (scope) {
            case "horse" -> lower.contains("horse") || lower.contains("rider") || lower.contains("session")
                    || lower.contains("stable") || lower.contains("jump");
            case "coach" -> lower.contains("coach") || lower.contains("lesson");
            case "lesson" -> lower.contains("lesson") || lower.contains("rider") || lower.contains("horse")
                    || lower.contains("duration") || lower.contains("session");
            default -> true;
        };
    }

    private static final class WarningRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            WarningRecord warning = (WarningRecord) value;
            setText(warning.getSeverity() + ": " + warning.getMessage()
                    + (warning.isOverridden() ? " [overridden]" : ""));
            if (warning.isOverridden()) {
                setForeground(new Color(107, 114, 128));
            }
            return this;
        }
    }
}
