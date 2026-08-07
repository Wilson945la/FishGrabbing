import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 登录界面
 */
public class LoginFrame extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);

    public LoginFrame() {
        setTitle("摸鱼中心 - 登录");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(380, 500));

        // 顶部标题
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(40, 0, 10, 0));
        top.add(createEmojiLabel("\uD83D\uDC1F", 50), BorderLayout.NORTH);
        JLabel title = new JLabel("摸鱼中心", JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        top.add(title, BorderLayout.CENTER);
        main.add(top, BorderLayout.NORTH);

        // 表单区
        JPanel fp = new JPanel();
        fp.setBackground(BG);
        fp.setLayout(new BoxLayout(fp, BoxLayout.Y_AXIS));
        fp.setBorder(BorderFactory.createEmptyBorder(10, 60, 10, 60));

        // 用户名/账号输入
        fp.add(makeLabel("用户名 / 账号"));
        JTextField accountField = makeTextField();
        fp.add(accountField);
        fp.add(Box.createVerticalStrut(10));

        // 密码输入
        fp.add(makeLabel("密码"));
        JPasswordField passField = new JPasswordField();
        styleField(passField);
        passField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passField.setMaximumSize(new Dimension(260, 40));
        fp.add(passField);
        fp.add(Box.createVerticalStrut(20));

        // 登录按钮
        JButton loginBtn = actionBtn("登  录");
        fp.add(loginBtn);
        fp.add(Box.createVerticalStrut(12));

        // 离线按钮
        JButton offlineBtn = actionBtn("离线使用");
        fp.add(offlineBtn);
        fp.add(Box.createVerticalStrut(15));

        // Enter键自动登录 — 两个输入框都有内容时按回车触发
        ActionListener enterAction = e -> {
            if (!accountField.getText().trim().isEmpty() && passField.getPassword().length > 0) {
                loginBtn.doClick();
            }
        };
        accountField.addActionListener(enterAction);
        passField.addActionListener(enterAction);

        // 注册链接
        JPanel regPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regPanel.setBackground(BG);
        JLabel regLink = new JLabel("还没有账号吗？点击注册");
        regLink.setForeground(new Color(255, 204, 0));
        regLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        regLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                setVisible(false);
                RegisterFrame reg = new RegisterFrame();
                reg.setVisible(true);
            }
        });
        regPanel.add(regLink);
        fp.add(regPanel);

        main.add(fp, BorderLayout.CENTER);

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);

        // 登录按钮事件
        loginBtn.addActionListener(e -> {
            String account = accountField.getText().trim();
            String password = new String(passField.getPassword());

            if (account.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入用户名或账号", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入密码", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            loginBtn.setText("登录中...");
            loginBtn.setEnabled(false);

            Thread lt = new Thread(() -> {
                String result = ServerClient.login(account, password);
                String[] parts = result.split("\\|");

                SwingUtilities.invokeLater(() -> {
                    loginBtn.setText("登  录");
                    loginBtn.setEnabled(true);

                    if ("SUCCESS".equals(parts[0])) {
                        int userId = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        String displayName = parts.length > 2 ? parts[2] : account;
                        int fish = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;

                        // 设置当前登录用户
                        ServerClient.setCurrentUser(displayName);
                        // 登录成功后设置状态为在线
                        ServerClient.setUserState(displayName, 1);

                        setVisible(false);
                        FishGrabbingHome home = new FishGrabbingHome(displayName, true, fish);
                        home.setUserId(userId);
                        home.setVisible(true);
                        // 登录成功后启动全局推送监听（即使消息中心未打开也能接收对决邀请）
                        MessageCenter.startGlobalPush(displayName, userId);
                    } else {
                        String msg = parts.length > 1 ? parts[1] : "登录失败";
                        JOptionPane.showMessageDialog(this, msg, "登录失败", JOptionPane.ERROR_MESSAGE);
                    }
                });
            });
            lt.setDaemon(true);
            lt.start();
        });

        // 离线按钮事件
        offlineBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "离线模式下个人纪录不会上传至服务器\n是否继续？",
                "提示",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
            if (result != JOptionPane.YES_OPTION) return;
            // 清除可能残留的登录用户状态，确保离线模式下匹配功能不可用
            ServerClient.setCurrentUser(null);
            setVisible(false);
            FishGrabbingHome home = new FishGrabbingHome(null, false, 0);
            home.setVisible(true);
        });
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        l.setForeground(Color.WHITE);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JTextField makeTextField() {
        JTextField tf = new JTextField();
        styleField(tf);
        tf.setAlignmentX(Component.CENTER_ALIGNMENT);
        tf.setMaximumSize(new Dimension(260, 40));
        return tf;
    }

    private void styleField(JComponent c) {
        c.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        c.setBackground(new Color(60, 63, 65));
        c.setForeground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
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
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
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

    public static void main(String[] args) {
        System.setProperty("sun.java2d.dpiaware", "true");
        System.setProperty("sun.java2d.uiScale", "1.0");
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
