import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 注册界面
 */
public class RegisterFrame extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);

    public RegisterFrame() {
        setTitle("摸鱼中心 - 注册");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(380, 550));

        // 顶部标题
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(30, 0, 10, 0));
        top.add(createEmojiLabel("\uD83D\uDC1F", 50), BorderLayout.NORTH);
        JLabel title = new JLabel("注册账号", JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        top.add(title, BorderLayout.CENTER);
        main.add(top, BorderLayout.NORTH);

        // 表单区
        JPanel fp = new JPanel();
        fp.setBackground(BG);
        fp.setLayout(new BoxLayout(fp, BoxLayout.Y_AXIS));
        fp.setBorder(BorderFactory.createEmptyBorder(10, 60, 10, 60));

        // 用户名
        fp.add(makeLabel("用户名"));
        JTextField nameField = makeTextField();
        fp.add(nameField);
        fp.add(Box.createVerticalStrut(10));

        // 账号
        fp.add(makeLabel("账号"));
        JTextField accountField = makeTextField();
        fp.add(accountField);
        fp.add(Box.createVerticalStrut(10));

        // 密码
        fp.add(makeLabel("密码"));
        JPasswordField passField = new JPasswordField();
        styleField(passField);
        passField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passField.setMaximumSize(new Dimension(260, 40));
        fp.add(passField);
        fp.add(Box.createVerticalStrut(10));

        // 确认密码
        fp.add(makeLabel("确认密码"));
        JPasswordField confirmField = new JPasswordField();
        styleField(confirmField);
        confirmField.setAlignmentX(Component.CENTER_ALIGNMENT);
        confirmField.setMaximumSize(new Dimension(260, 40));
        fp.add(confirmField);
        fp.add(Box.createVerticalStrut(20));

        // 注册按钮
        JButton regBtn = actionBtn("注  册");
        fp.add(regBtn);
        fp.add(Box.createVerticalStrut(15));

        // 返回登录链接
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginPanel.setBackground(BG);
        JLabel loginLink = new JLabel("已有账号？返回登录");
        loginLink.setForeground(new Color(255, 204, 0));
        loginLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                setVisible(false);
                new LoginFrame().setVisible(true);
            }
        });
        loginPanel.add(loginLink);
        fp.add(loginPanel);

        main.add(fp, BorderLayout.CENTER);

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);

        // 注册按钮事件
        regBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String account = accountField.getText().trim();
            String password = new String(passField.getPassword());
            String confirm = new String(confirmField.getPassword());

            if (name.isEmpty() || account.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请填写所有必填项", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!password.equals(confirm)) {
                JOptionPane.showMessageDialog(this, "两次输入的密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            regBtn.setText("注册中...");
            regBtn.setEnabled(false);

            Thread rt = new Thread(() -> {
                String result = ServerClient.register(name, account, password);
                String[] parts = result.split("\\|");

                SwingUtilities.invokeLater(() -> {
                    regBtn.setText("注  册");
                    regBtn.setEnabled(true);

                    if ("SUCCESS".equals(parts[0])) {
                        JOptionPane.showMessageDialog(this, "注册成功！请登录", "注册成功", JOptionPane.INFORMATION_MESSAGE);
                        setVisible(false);
                        new LoginFrame().setVisible(true);
                    } else {
                        String msg = parts.length > 1 ? parts[1] : "注册失败";
                        // 根据错误信息判断是用户名还是账号重复
                        if (msg.contains("用户名") || msg.contains("名称")) {
                            JOptionPane.showMessageDialog(this, "该用户名已被使用，请更换", "注册失败", JOptionPane.ERROR_MESSAGE);
                        } else if (msg.contains("账号")) {
                            JOptionPane.showMessageDialog(this, "该账号已被使用，请更换", "注册失败", JOptionPane.ERROR_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(this, msg, "注册失败", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
            });
            rt.setDaemon(true);
            rt.start();
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
}
