import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AeroChessHome extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);

    private JFrame homeFrame;
    private int userId;

    public void setUserId(int userId) { this.userId = userId; }
    public void setHomeFrame(JFrame homeFrame) {
        this.homeFrame = homeFrame;
    }

    public AeroChessHome() {
        setTitle("飞行棋");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                String u = ServerClient.getCurrentUser();
                if (u != null) {
                    try { ServerClient.setUserState(u, 0); } catch (Exception ignored) {}
                }
                // 关闭飞行棋主页时恢复摸鱼中心
                FishGrabbingHome.showActiveInstance();
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

        JLabel title = new JLabel("飞行棋");
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

        JButton startBtn = gameBtn("开始游戏 (四人)");
        startBtn.addActionListener(e -> openMatchRoom());
        center.add(startBtn);

        center.add(Box.createVerticalStrut(12));

        JButton customBtn = gameBtn("自定义");
        customBtn.addActionListener(e -> openCustomDialog());
        center.add(customBtn);

        main.add(center, BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setBackground(BG);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JLabel ver = new JLabel("摸鱼神器 v1.3", JLabel.CENTER);
        ver.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        ver.setForeground(new Color(120, 123, 126));
        bot.add(ver, BorderLayout.CENTER);
        JButton backBtn = txtBtn("返回主页", Color.WHITE, new Color(80, 83, 86), 100, 32);
        backBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { backBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { backBtn.setBackground(BTN_BASE); }
        });
        backBtn.addActionListener(e -> {
            dispose();
            FishGrabbingHome.showActiveInstance();
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

    /** 自定义游戏设置对话框 */
    private void openCustomDialog() {
        String currentUser = ServerClient.getCurrentUser();
        if (currentUser == null || currentUser.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "自定义游戏设置", true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(BG);
        dialog.setSize(340, 420);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // 起飞点数标题
        JLabel takeoffTitle = new JLabel("选择起飞点数（可多选）", JLabel.LEFT);
        takeoffTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        takeoffTitle.setForeground(Color.WHITE);
        takeoffTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(takeoffTitle);
        panel.add(Box.createVerticalStrut(10));

        // 起飞点数复选框面板（2行3列）
        JPanel checkboxPanel = new JPanel(new GridLayout(2, 3, 8, 8));
        checkboxPanel.setBackground(BG);
        JCheckBox[] takeoffChecks = new JCheckBox[6];
        for (int i = 0; i < 6; i++) {
            takeoffChecks[i] = new JCheckBox(String.valueOf(i + 1));
            takeoffChecks[i].setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            takeoffChecks[i].setForeground(Color.WHITE);
            takeoffChecks[i].setBackground(BG);
            takeoffChecks[i].setFocusPainted(false);
            if (i == 5) takeoffChecks[i].setSelected(true); // 默认选中6
            checkboxPanel.add(takeoffChecks[i]);
        }
        panel.add(checkboxPanel);
        panel.add(Box.createVerticalStrut(25));

        // 行走圈数标题
        JLabel lapsTitle = new JLabel("行走圈数（进终点线前）", JLabel.LEFT);
        lapsTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        lapsTitle.setForeground(Color.WHITE);
        lapsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lapsTitle);
        panel.add(Box.createVerticalStrut(10));

        // 圈数选择
        JPanel lapsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        lapsPanel.setBackground(BG);
        SpinnerNumberModel lapsModel = new SpinnerNumberModel(1, 1, 10, 1);
        JSpinner lapsSpinner = new JSpinner(lapsModel);
        lapsSpinner.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        lapsSpinner.setPreferredSize(new Dimension(80, 30));
        lapsPanel.add(lapsSpinner);
        JLabel lapsHint = new JLabel("圈");
        lapsHint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        lapsHint.setForeground(new Color(180, 183, 186));
        lapsPanel.add(lapsHint);
        lapsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lapsPanel);
        panel.add(Box.createVerticalStrut(25));

        // 提示
        JLabel hint = new JLabel("<html>自定义模式仅支持邀请好友或添加机器人，<br>不支持系统匹配</html>", JLabel.LEFT);
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(new Color(150, 153, 156));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hint);

        // 确认按钮
        panel.add(Box.createVerticalStrut(20));
        JButton confirmBtn = new JButton("创建房间");
        confirmBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setBackground(BTN_HOVER);
        confirmBtn.setFocusPainted(false);
        confirmBtn.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        confirmBtn.setMaximumSize(new Dimension(300, 44));
        confirmBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        confirmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        confirmBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { confirmBtn.setBackground(new Color(0, 140, 235)); }
            public void mouseExited(MouseEvent e) { confirmBtn.setBackground(BTN_HOVER); }
        });
        confirmBtn.addActionListener(e -> {
            java.util.List<Integer> takeoffVals = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                if (takeoffChecks[i].isSelected()) takeoffVals.add(i + 1);
            }
            if (takeoffVals.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请至少选择一个起飞点数", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int laps = (Integer) lapsSpinner.getValue();
            dialog.dispose();
            openCustomRoom(takeoffVals, laps);
        });
        panel.add(confirmBtn);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    /** 创建自定义房间 */
    private void openCustomRoom(java.util.List<Integer> takeoffVals, int laps) {
        String currentUser = ServerClient.getCurrentUser();
        StringBuilder sb = new StringBuilder("自定义;");
        for (int i = 0; i < takeoffVals.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(takeoffVals.get(i));
        }
        sb.append(";").append(laps);
        String customMode = sb.toString();

        setVisible(false);
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelCreate(currentUser, customMode, 4, "飞行棋");
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    String data = resp.substring("SUCCESS|".length());
                    String[] parts = data.split("\\|");
                    int roomId = Integer.parseInt(parts[0]);
                    AeroChessMatchRoom room = new AeroChessMatchRoom(currentUser, userId, customMode, 4,
                            true, roomId, this);
                    room.applyRoomState(room.parseRoomState(resp));
                    room.setVisible(true);
                    dispose();
                } else {
                    setVisible(true);
                    JOptionPane.showMessageDialog(this, "创建房间失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 创建房间并打开匹配界面 */
    private void openMatchRoom() {
        String currentUser = ServerClient.getCurrentUser();
        if (currentUser == null || currentUser.isEmpty()) {
            // 离线模式：无需登录，默认添加 3 个机器人与玩家本地开局
            startOfflineGame("经典");
            return;
        }

        setVisible(false);
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelCreate(currentUser, "经典", 4, "飞行棋");
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    String data = resp.substring("SUCCESS|".length());
                    String[] parts = data.split("\\|");
                    int roomId = Integer.parseInt(parts[0]);
                    AeroChessMatchRoom room = new AeroChessMatchRoom(currentUser, userId, "经典", 4,
                            true, roomId, this);
                    room.applyRoomState(room.parseRoomState(resp));
                    room.setVisible(true);
                    dispose();
                } else {
                    setVisible(true);
                    JOptionPane.showMessageDialog(this, "创建房间失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 离线模式：本地开局，玩家 + 3 个机器人，无需服务器 */
    private void startOfflineGame(String mode) {
        setVisible(false);
        java.util.List<String> players = new java.util.ArrayList<>();
        players.add("玩家1");
        players.add("机器人1");
        players.add("机器人2");
        players.add("机器人3");
        long seed = System.currentTimeMillis();
        AeroChessGame game = new AeroChessGame("玩家1", 0, seed, mode, players, null);
        game.setVisible(true);
        dispose();
    }

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
