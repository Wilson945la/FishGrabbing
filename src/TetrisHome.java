import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class TetrisHome extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);

    private JFrame homeFrame;
    private int userId;

    public void setUserId(int userId) { this.userId = userId; }
    public void setHomeFrame(JFrame homeFrame) {
        this.homeFrame = homeFrame;
    }

    public TetrisHome() {
        setTitle("\u4fc4\u7f57\u65af\u65b9\u5757");
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
        main.setPreferredSize(new Dimension(380, 500));

        JPanel titleArea = new JPanel();
        titleArea.setBackground(BG);
        titleArea.setLayout(new BoxLayout(titleArea, BoxLayout.Y_AXIS));
        titleArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleArea.setBorder(BorderFactory.createEmptyBorder(30, 0, 0, 0));

        JLabel title = new JLabel("\u4fc4\u7f57\u65af\u65b9\u5757");
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 32));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleArea.add(title);
        titleArea.add(Box.createVerticalStrut(10));

        main.add(titleArea, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setBackground(BG);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(40, 60, 10, 60));

        JButton classicBtn = gameBtn("\u7ecf\u5178\u6a21\u5f0f");
        classicBtn.addActionListener(e -> {
            setVisible(false);
            TetrisGame game = new TetrisGame(false, userId);
            game.setHomeFrame(this);
            game.setVisible(true);
        });
        center.add(classicBtn);
        center.add(Box.createVerticalStrut(10));

        JButton hardBtn = gameBtn("\u56f0\u96be\u6a21\u5f0f");
        hardBtn.addActionListener(e -> {
            setVisible(false);
            TetrisGame game = new TetrisGame(true, userId);
            game.setHomeFrame(this);
            game.setVisible(true);
        });
        center.add(hardBtn);
        center.add(Box.createVerticalStrut(10));

        JButton stackBtn = gameBtn("\u53e0\u53e0\u4e50\u6a21\u5f0f");
        stackBtn.addActionListener(e -> {
            setVisible(false);
            TetrisStack game = new TetrisStack(userId);
            game.setHomeFrame(this);
            game.setVisible(true);
        });
        center.add(stackBtn);
        center.add(Box.createVerticalStrut(10));

        JButton recordBtn = gameBtn("个人纪录");
        recordBtn.addActionListener(e -> {
            TetrisRecords.showRecords(this, userId);
        });
        center.add(recordBtn);
        center.add(Box.createVerticalStrut(10));

        JButton duelBtn = gameBtn("对 决");
        duelBtn.addActionListener(e -> showDuelDialog());
        center.add(duelBtn);

        main.add(center, BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setBackground(BG);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JLabel ver = new JLabel("摸鱼神器 v1.3", JLabel.CENTER);
        ver.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        ver.setForeground(new Color(120, 123, 126));
        bot.add(ver, BorderLayout.CENTER);
        JButton backBtn = txtBtn("\u8fd4\u56de\u4e3b\u9875", Color.WHITE, new Color(80, 83, 86), 100, 32);
        backBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { backBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { backBtn.setBackground(BTN_BASE); }
        });
        backBtn.addActionListener(e -> {
            setVisible(false);
            if (homeFrame != null) {
                homeFrame.setVisible(true);
            } else {
                new FishGrabbingHome().setVisible(true);
            }
        });
        JPanel backPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        backPanel.setBackground(BG);
        backPanel.add(backBtn);
        bot.add(backPanel, BorderLayout.EAST);
        main.add(bot, BorderLayout.SOUTH);

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);
    }

    /** 显示对决模式选择对话框 */
    private void showDuelDialog() {
        String currentUser = ServerClient.getCurrentUser();
        if (currentUser == null || currentUser.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog d = new JDialog(this, "俄罗斯方块对决 - 选择模式", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(new Color(60, 63, 65));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 0, 8, 0);
        g.gridx = 0;
        g.weightx = 1;

        String[] modes = {"经典", "困难", "叠叠乐"};
        for (int i = 0; i < modes.length; i++) {
            g.gridy = i;
            JButton btn = new JButton(modes[i] + "模式");
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(80, 83, 86));
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final String mode = modes[i];
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(0, 120, 215)); }
                public void mouseExited(MouseEvent e) { btn.setBackground(new Color(80, 83, 86)); }
            });
            btn.addActionListener(e -> {
                d.dispose();
                showDuelCountDialog(mode);
            });
            c.add(btn, g);
        }

        d.add(c, BorderLayout.CENTER);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    /** 选择人数 */
    private void showDuelCountDialog(String mode) {
        JDialog d = new JDialog(this, "俄罗斯方块对决 - 选择人数", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(new Color(60, 63, 65));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 0, 8, 0);
        g.gridx = 0;
        g.weightx = 1;

        int[] counts = {2, 3, 4};
        for (int i = 0; i < counts.length; i++) {
            g.gridy = i;
            JButton btn = new JButton(counts[i] + "人对决");
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(80, 83, 86));
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final int maxPlayers = counts[i];
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(0, 120, 215)); }
                public void mouseExited(MouseEvent e) { btn.setBackground(new Color(80, 83, 86)); }
            });
            btn.addActionListener(e -> {
                d.dispose();
                openMatchRoom(mode, maxPlayers);
            });
            c.add(btn, g);
        }

        d.add(c, BorderLayout.CENTER);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    /** 创建房间并打开匹配界面 */
    private void openMatchRoom(String mode, int maxPlayers) {
        setVisible(false);
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelCreate(username(), mode, maxPlayers, "俄罗斯方块");
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    // 解析 roomId
                    String data = resp.substring("SUCCESS|".length());
                    String[] parts = data.split("\\|");
                    int roomId = Integer.parseInt(parts[0]);
                    TetrisMatchRoom room = new TetrisMatchRoom(username(), userId, mode, maxPlayers,
                            true, roomId, this);
                    room.applyRoomState(room.parseRoomState(resp));
                    room.setVisible(true);
                } else {
                    setVisible(true);
                    JOptionPane.showMessageDialog(this, "创建房间失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private String username() { return ServerClient.getCurrentUser(); }

    private JButton gameBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        b.setForeground(Color.WHITE);
        b.setBackground(BTN_BASE);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(350, 56));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { b.setBackground(BTN_BASE); }
        });
        return b;
    }

    private JButton txtBtn(String text, Color fg, Color bg, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        b.setForeground(fg);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        return b;
    }
}
