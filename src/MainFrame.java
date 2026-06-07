import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    public MainFrame() {
        DatabaseManager.initializeDatabase();
        UIStyles.applyFrame(this);
        setTitle("Riding School System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1500, 950);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        UIStyles.styleNavigationTabs(tabs);
        tabs.addTab("Horses", new HorsesPanel());
        tabs.addTab("Coaches", new CoachesPanel());
        tabs.addTab("Lessons", new LessonsPanel());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIStyles.pageColor());
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
        setVisible(true);
    }
}
