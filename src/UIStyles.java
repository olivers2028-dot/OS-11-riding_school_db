//GENERATED WITH AI
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;

public final class UIStyles {
    private static final Color PAGE = new Color(241, 245, 249);
    private static final Color PANEL = new Color(255, 255, 255);
    private static final Color PANEL_ALT = new Color(247, 250, 252);
    private static final Color ACCENT = new Color(30, 64, 175);
    private static final Color ACCENT_DARK = new Color(15, 23, 42);
    private static final Color ACCENT_LIGHT = new Color(219, 234, 254);
    private static final Color TEXT = new Color(15, 23, 42);
    private static final Font TITLE_FONT = new Font("Calibri", Font.BOLD, 24);
    private static final Font BODY_FONT = new Font("Calibri", Font.PLAIN, 15);

    private UIStyles() {
    }

    public static Color pageColor() {
        return PAGE;
    }

    public static void applyFrame(JFrame frame) {
        UIManager.put("Label.font", BODY_FONT);
        UIManager.put("Button.font", BODY_FONT);
        UIManager.put("Table.font", BODY_FONT);
        UIManager.put("TableHeader.font", BODY_FONT.deriveFont(Font.BOLD));
        UIManager.put("TextField.font", BODY_FONT);
        UIManager.put("ComboBox.font", BODY_FONT);
        UIManager.put("TabbedPane.font", BODY_FONT.deriveFont(Font.BOLD, 15f));
        UIManager.put("Table.selectionBackground", ACCENT_LIGHT);
        UIManager.put("Table.selectionForeground", TEXT);
        frame.getContentPane().setBackground(PAGE);
    }

    public static JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(PANEL);
        panel.setBorder(new CompoundBorder(
                new LineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(16, 16, 16, 16)
        ));
        JLabel label = new JLabel(title);
        label.setFont(TITLE_FONT);
        label.setForeground(TEXT);
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    public static JTable styleTable(JTable table) {
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(ACCENT_LIGHT);
        table.setSelectionForeground(TEXT);
        table.setGridColor(new Color(226, 232, 240));
        table.setForeground(TEXT);
        table.setBackground(Color.WHITE);
        JTableHeader header = table.getTableHeader();
        header.setBackground(ACCENT_DARK);
        header.setForeground(Color.WHITE);
        return table;
    }

    public static JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setFont(BODY_FONT.deriveFont(Font.BOLD));
        button.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_DARK, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        return button;
    }

    public static JPanel wrapForm(GridBagLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PANEL_ALT);
        panel.setBorder(new CompoundBorder(
                new LineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        return panel;
    }

    public static void styleNavigationTabs(JTabbedPane tabs) {
        tabs.setBackground(PAGE);
        tabs.setForeground(TEXT);
        tabs.setOpaque(true);
        tabs.setBorder(new EmptyBorder(10, 10, 10, 10));
        tabs.setFocusable(false);
    }
}
