import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class PersonalCenter extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private JFrame homeFrame;
    private String username;

    public PersonalCenter(String username, JFrame homeFrame) {
        this.username = username;
        this.homeFrame = homeFrame;

        setTitle("个人中心");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                String u = ServerClient.getCurrentUser();
                if (u != null) {
                    try { ServerClient.setUserState(u, 0); } catch (Exception ignored) {}
                }
            }
        });
        setResizable(false);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(380, 400));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(30, 0, 10, 0));
        top.add(createEmojiLabel("\uD83D\uDC64", 50), BorderLayout.NORTH);
        JLabel title = new JLabel("个人中心", JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        top.add(title, BorderLayout.CENTER);
        main.add(top, BorderLayout.NORTH);

        JPanel ip = new JPanel();
        ip.setBackground(BG);
        ip.setLayout(new BoxLayout(ip, BoxLayout.Y_AXIS));
        ip.setBorder(BorderFactory.createEmptyBorder(20, 60, 10, 60));

        JPanel namePanel = new JPanel(new BorderLayout());
        namePanel.setBackground(BG);
        namePanel.add(makeLabel("用户名"), BorderLayout.WEST);
        JLabel nameValue = new JLabel(username);
        nameValue.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        nameValue.setForeground(Color.WHITE);
        namePanel.add(nameValue, BorderLayout.EAST);
        ip.add(namePanel);
        ip.add(Box.createVerticalStrut(20));

        JButton changePwBtn = actionBtn("修改密码");
        changePwBtn.addActionListener(e -> {
            setVisible(false);
            new ChangePasswordDialog(username, this).setVisible(true);
        });
        ip.add(changePwBtn);
        ip.add(Box.createVerticalStrut(15));

        JButton backBtn = actionBtn("返回主页");
        backBtn.addActionListener(e -> {
            setVisible(false);
            homeFrame.setVisible(true);
        });
        ip.add(backBtn);

        main.add(ip, BorderLayout.CENTER);

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        l.setForeground(new Color(150, 153, 156));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JButton actionBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        b.setForeground(Color.WHITE);
        b.setBackground(BTN_BASE);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(260, 46));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { b.setBackground(BTN_BASE); }
        });
        return b;
    }

    private JLabel createEmojiLabel(String emoji, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Font f = new Font("Segoe UI Emoji", Font.PLAIN, size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(emoji)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(emoji, x, y);
        g.dispose();
        return new JLabel(new ImageIcon(img));
    }
}
