import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * 修改密码对话框
 */
public class ChangePasswordDialog extends JDialog {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);

    private String username;
    private JFrame parentFrame;

    public ChangePasswordDialog(String username, JFrame parentFrame) {
        super(parentFrame, "修改密码", true);
        this.username = username;
        this.parentFrame = parentFrame;

        setResizable(false);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(360, 420));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        top.add(createEmojiLabel("\uD83D\uDD12", 40), BorderLayout.NORTH);
        JLabel title = new JLabel("修改密码", JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        top.add(title, BorderLayout.CENTER);
        main.add(top, BorderLayout.NORTH);

        JPanel fp = new JPanel();
        fp.setBackground(BG);
        fp.setLayout(new BoxLayout(fp, BoxLayout.Y_AXIS));
        fp.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        fp.add(makeLabel("旧密码"));
        JPasswordField oldPass = makePasswordField();
        fp.add(oldPass);
        fp.add(Box.createVerticalStrut(10));

        fp.add(makeLabel("新密码"));
        JPasswordField newPass = makePasswordField();
        fp.add(newPass);
        fp.add(Box.createVerticalStrut(10));

        fp.add(makeLabel("确认新密码"));
        JPasswordField confirmPass = makePasswordField();
        fp.add(confirmPass);
        fp.add(Box.createVerticalStrut(20));

        JButton submitBtn = actionBtn("确认修改");
        fp.add(submitBtn);
        fp.add(Box.createVerticalStrut(15));

        JButton cancelBtn = actionBtn("取消");
        cancelBtn.addActionListener(e -> dispose());
        fp.add(cancelBtn);

        main.add(fp, BorderLayout.CENTER);

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);

        submitBtn.addActionListener(e -> {
            String oldP = new String(oldPass.getPassword());
            String newP = new String(newPass.getPassword());
            String confirmP = new String(confirmPass.getPassword());

            if (oldP.isEmpty() || newP.isEmpty() || confirmP.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请填写所有字段", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!newP.equals(confirmP)) {
                JOptionPane.showMessageDialog(this, "两次输入的新密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            submitBtn.setText("修改中...");
            submitBtn.setEnabled(false);

            Thread ct = new Thread(() -> {
                String result = ServerClient.changePassword(username, oldP, newP);
                SwingUtilities.invokeLater(() -> {
                    submitBtn.setText("确认修改");
                    submitBtn.setEnabled(true);
                    if (result.startsWith("SUCCESS")) {
                        JOptionPane.showMessageDialog(this, "密码修改成功", "成功", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                        parentFrame.setVisible(true);
                    } else {
                        String msg = result.split("\\|").length > 1 ? result.split("\\|")[1] : "修改失败";
                        JOptionPane.showMessageDialog(this, msg, "修改失败", JOptionPane.ERROR_MESSAGE);
                    }
                });
            });
            ct.setDaemon(true);
            ct.start();
        });
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        l.setForeground(Color.WHITE);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JPasswordField makePasswordField() {
        JPasswordField pf = new JPasswordField();
        pf.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        pf.setBackground(new Color(60, 63, 65));
        pf.setForeground(Color.WHITE);
        pf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        pf.setMaximumSize(new Dimension(280, 40));
        pf.setAlignmentX(Component.CENTER_ALIGNMENT);
        return pf;
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
        b.setMaximumSize(new Dimension(280, 46));
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
