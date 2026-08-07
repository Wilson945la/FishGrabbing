import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 好友个人纪录查看对话框
 * 支持选择不同游戏（扫雷、俄罗斯方块），从服务器读取好友的个人纪录
 */
public class FriendRecordsDialog extends JDialog {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color CARD_BG = new Color(60, 63, 65);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final Color BTN_ACTIVE = new Color(0, 90, 170);
    private static final Color RECORD_COLOR = new Color(100, 255, 100);

    // 各游戏模式名称
    private static final String[] MINESWEEPER_MODES = {"初级模式", "中级模式", "高级模式", "自定义模式"};
    private static final String[] TETRIS_MODES  = {"经典模式", "困难模式", "叠叠乐模式"};

    private String friendName;
    private int friendUserId;
    private JPanel recordsPanel;
    private JButton selectedBtn;
    private volatile int loadSeq = 0;  // 计数器，取消旧请求结果

    public FriendRecordsDialog(JFrame parent, String friendName) {
        super(parent, friendName + " 的个人纪录", true);
        this.friendName = friendName;

        // 先获取好友的 userId
        loadFriendId();

        setResizable(false);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // 顶部标题
        JLabel titleLabel = new JLabel(friendName + " 的个人纪录", JLabel.CENTER);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 中间：游戏按钮 + 纪录展示区
        JPanel centerPanel = new JPanel();
        centerPanel.setBackground(BG);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // 游戏选择按钮栏（横向滚动，方便后续扩展更多游戏）
        JPanel gameBtnInner = new JPanel();
        gameBtnInner.setBackground(BG);
        gameBtnInner.setLayout(new BoxLayout(gameBtnInner, BoxLayout.X_AXIS));
        gameBtnInner.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JButton minesweeperBtn = createGameButton("扫雷");
        JButton tetrisBtn  = createGameButton("俄罗斯方块");

        gameBtnInner.add(minesweeperBtn);
        gameBtnInner.add(Box.createHorizontalStrut(12));
        gameBtnInner.add(tetrisBtn);

        JScrollPane gameBtnScroll = new JScrollPane(gameBtnInner);
        gameBtnScroll.setBackground(BG);
        gameBtnScroll.getViewport().setBackground(BG);
        gameBtnScroll.setBorder(BorderFactory.createEmptyBorder());
        gameBtnScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        gameBtnScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        gameBtnScroll.setPreferredSize(new Dimension(300, 48));
        centerPanel.add(gameBtnScroll);

        // 纪录展示区域
        recordsPanel = new JPanel();
        recordsPanel.setBackground(CARD_BG);
        recordsPanel.setBorder(BorderFactory.createLineBorder(new Color(90, 93, 96), 1));
        recordsPanel.setLayout(new GridBagLayout());
        centerPanel.add(recordsPanel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 底部关闭按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        bottomPanel.setBackground(BG);

        JButton closeBtn = new JButton("关闭");
        closeBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(BTN_BASE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new Dimension(80, 32));
        closeBtn.addActionListener(e -> dispose());
        closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { closeBtn.setBackground(BTN_HOVER); }
            public void mouseExited(java.awt.event.MouseEvent e) { closeBtn.setBackground(BTN_BASE); }
        });
        bottomPanel.add(closeBtn);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 游戏按钮点击事件
        minesweeperBtn.addActionListener(e -> {
            highlightButton(minesweeperBtn);
            loadGameRecords("扫雷", MINESWEEPER_MODES);
        });

        tetrisBtn.addActionListener(e -> {
            highlightButton(tetrisBtn);
            loadGameRecords("俄罗斯方块", TETRIS_MODES);
        });

        add(mainPanel);
        setSize(420, 460);
        setLocationRelativeTo(parent);

        // 默认选中俄罗斯方块
        tetrisBtn.doClick();
    }

    /** 加载好友的 userId */
    private void loadFriendId() {
        try {
            String resp = ServerClient.getUserId(friendName);
            if (resp.startsWith("SUCCESS|")) {
                friendUserId = Integer.parseInt(resp.substring("SUCCESS|".length()));
            } else {
                friendUserId = -1;
            }
        } catch (Exception e) {
            friendUserId = -1;
        }
    }

    /** 从服务器加载指定游戏的纪录 */
    private void loadGameRecords(String gameName, String[] modes) {
        // 递增序号，使旧请求的结果被忽略
        final int mySeq = ++loadSeq;

        // 立即清空面板并显示加载状态
        recordsPanel.removeAll();
        recordsPanel.setLayout(new GridBagLayout());
        JLabel loadingLabel = new JLabel("加载中...", JLabel.CENTER);
        loadingLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        loadingLabel.setForeground(Color.GRAY);
        recordsPanel.add(loadingLabel);
        recordsPanel.revalidate();
        recordsPanel.repaint();

        if (friendUserId <= 0) {
            SwingUtilities.invokeLater(() -> {
                if (mySeq != loadSeq) return;
                showEmptyState("无法获取好友信息");
            });
            return;
        }

        // 在后台线程中加载纪录
        Thread frt = new Thread(() -> {
            Map<String, String> records = fetchRecords(gameName, modes);
            SwingUtilities.invokeLater(() -> {
                if (mySeq != loadSeq) return;  // 旧请求，丢弃
                displayRecords(gameName, modes, records);
            });
        });
        frt.setDaemon(true);
        frt.start();
    }

    /** 从服务器取特定游戏的纪录——服务器端按 Game_name 过滤 */
    private Map<String, String> fetchRecords(String gameName, String[] modes) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try {
            String resp = ServerClient.getRecords(friendUserId, gameName);
            if (resp.startsWith("SUCCESS|")) {
                String data = resp.substring("SUCCESS|".length());
                if (!data.isEmpty()) {
                    for (String triple : data.split(",")) {
                        String[] parts = triple.split("\\|");
                        if (parts.length >= 3) {
                            String mode = parts[1].trim();
                            String rec  = parts[2].trim();
                            String displayMode = toDisplayName(gameName, mode);
                            m.put(displayMode, rec);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return m;
    }

    /** 秒数转 MM:SS 格式，与 MinesweeperRecords 保持一致 */
    private static String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /** 将数据库模式名转为显示名 */
    private String toDisplayName(String gameName, String dbMode) {
        if ("俄罗斯方块".equals(gameName)) {
            switch (dbMode) {
                case "经典": return "经典模式";
                case "困难": return "困难模式";
                case "叠叠乐": return "叠叠乐模式";
                default: return dbMode;
            }
        }
        if ("扫雷".equals(gameName)) {
            switch (dbMode) {
                case "初级": return "初级模式";
                case "中级": return "中级模式";
                case "高级": return "高级模式";
                case "自定义": return "自定义模式";
                default: return dbMode;
            }
        }
        return dbMode;
    }

    /** 在面板中展示纪录，参考 TetrisRecords 和 MinesweeperRecords 的显示风格 */
    private void displayRecords(String gameName, String[] modes, Map<String, String> records) {
        recordsPanel.removeAll();
        recordsPanel.setLayout(new GridBagLayout());
        recordsPanel.setBackground(CARD_BG);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 20, 10, 20);
        gbc.gridx = 0;

        // 游戏名称列头
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel gameHeader = new JLabel("— " + gameName + " —");
        gameHeader.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        gameHeader.setForeground(new Color(180, 180, 180));
        recordsPanel.add(gameHeader, gbc);
        gbc.gridwidth = 1;

        // 各模式纪录
        for (int i = 0; i < modes.length; i++) {
            gbc.gridy = i + 1;
            gbc.gridx = 0;
            gbc.weightx = 1;
            gbc.anchor = GridBagConstraints.WEST;

            JLabel modeLabel = new JLabel(modes[i]);
            modeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 15));
            modeLabel.setForeground(Color.WHITE);
            recordsPanel.add(modeLabel, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.EAST;

            String value = records.getOrDefault(modes[i], null);
            // 扫雷存的是秒数，需要转为 MM:SS 格式显示
            String displayValue = value;
            if (value != null && "扫雷".equals(gameName)) {
                try { displayValue = formatTime(Integer.parseInt(value)); }
                catch (NumberFormatException ignored) {}
            }
            JLabel scoreLabel = new JLabel(displayValue == null ? "暂无纪录" : displayValue);
            scoreLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
            scoreLabel.setForeground(value == null ? Color.GRAY : RECORD_COLOR);
            recordsPanel.add(scoreLabel, gbc);
        }

        recordsPanel.revalidate();
        recordsPanel.repaint();

        // 强制父容器也刷新布局，防止纪录内容被截断
        Container parent = recordsPanel.getParent();
        if (parent instanceof JComponent) {
            ((JComponent) parent).revalidate();
            parent.repaint();
        }
    }

    /** 显示空状态提示 */
    private void showEmptyState(String message) {
        recordsPanel.removeAll();
        recordsPanel.setLayout(new GridBagLayout());
        JLabel emptyLabel = new JLabel(message, JLabel.CENTER);
        emptyLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        emptyLabel.setForeground(Color.GRAY);
        recordsPanel.add(emptyLabel);
        recordsPanel.revalidate();
        recordsPanel.repaint();
        Container parent = recordsPanel.getParent();
        if (parent instanceof JComponent) {
            ((JComponent) parent).revalidate();
            parent.repaint();
        }
    }

    /** 高亮当前选中的游戏按钮 */
    private void highlightButton(JButton btn) {
        if (selectedBtn != null) {
            selectedBtn.setBackground(BTN_BASE);
        }
        selectedBtn = btn;
        selectedBtn.setBackground(BTN_ACTIVE);
    }

    /** 创建游戏选择按钮 */
    private JButton createGameButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(BTN_BASE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn != selectedBtn) btn.setBackground(BTN_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (btn != selectedBtn) btn.setBackground(BTN_BASE);
            }
        });
        return btn;
    }
}
