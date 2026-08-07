import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class FriendsPage extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private String username;
    private JFrame homeFrame;
    private int userId;

    private JTextField searchField;
    private JButton addBtn;
    private JPanel friendsContainer;
    private JLabel placeholder;

    public FriendsPage(String username, JFrame homeFrame, int userId) {
        this.username = username;
        this.homeFrame = homeFrame;
        this.userId = userId;

        setTitle("我的好友");
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

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(30, 0, 10, 0));
        top.add(createEmojiLabel("\uD83E\uDD1D", 50), BorderLayout.NORTH);
        JLabel title = new JLabel("我的好友", JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        top.add(title, BorderLayout.CENTER);
        main.add(top, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setBackground(BG);
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        addPanel.setBackground(BG);
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(180, 32));
        searchField.setText("请输入好友名或好友账号");
        searchField.setForeground(new Color(150, 153, 156));
        searchField.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if ("请输入好友名或好友账号".equals(searchField.getText())) {
                    searchField.setText("");
                    searchField.setForeground(Color.WHITE);
                }
            }
        });
        styleField(searchField);
        addPanel.add(searchField);

        addBtn = new JButton("添加");
        addBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        addBtn.setForeground(Color.WHITE);
        addBtn.setBackground(new Color(0, 120, 215));
        addBtn.setFocusPainted(false);
        addBtn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { addBtn.setBackground(new Color(0, 100, 195)); }
            public void mouseExited(MouseEvent e) { addBtn.setBackground(new Color(0, 120, 215)); }
        });
        addPanel.add(addBtn);
        centerPanel.add(addPanel);
        centerPanel.add(Box.createVerticalStrut(15));

        friendsContainer = new JPanel();
        friendsContainer.setBackground(BG);
        friendsContainer.setLayout(new BoxLayout(friendsContainer, BoxLayout.Y_AXIS));
        friendsContainer.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));

        placeholder = new JLabel("暂无好友，搜索用户名添加吧~", JLabel.CENTER);
        placeholder.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        placeholder.setForeground(new Color(150, 153, 156));

        JScrollPane scrollPane = new JScrollPane(friendsContainer);
        scrollPane.setBackground(BG);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        scrollPane.getViewport().setBackground(BG);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(350, 320));
        centerPanel.add(scrollPane);

        main.add(centerPanel, BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bot.setBackground(BG);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JButton backBtn = txtBtn("返回主页", Color.WHITE, new Color(80, 83, 86), 100, 32);
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { backBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { backBtn.setBackground(BTN_BASE); }
        });
        backBtn.addActionListener(e -> {
            setVisible(false);
            if (homeFrame != null) homeFrame.setVisible(true);
            else new FishGrabbingHome().setVisible(true);
        });
        bot.add(backBtn);
        main.add(bot, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> {
            String target = searchField.getText().trim();
            // 提示文本没清空
            if (target.isEmpty() || "请输入好友名或好友账号".equals(target)) {
                JOptionPane.showMessageDialog(this, "请先输入好友的用户名或账号", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 不能添加自己
            if (target.equals(username)) {
                JOptionPane.showMessageDialog(this, "世界很大，多交交除自己外的朋友吧", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 检查是否已是好友
            String friendsResult = ServerClient.getFriends(username);
            if (friendsResult.startsWith("SUCCESS")) {
                String fData = friendsResult.substring("SUCCESS|".length());
                if (!fData.isEmpty()) {
                    for (String entry : fData.split(";")) {
                        if (entry.isEmpty()) continue;
                        String[] parts = entry.split(",");
                        if (parts.length >= 1 && parts[0].equals(target)) {
                            JOptionPane.showMessageDialog(this, "你们的友谊够深啦，不需要反复添加", "提示", JOptionPane.INFORMATION_MESSAGE);
                            return;
                        }
                    }
                }
            }
            String requestMsg = "FRIEND_REQUEST:" + username;
            String result = ServerClient.sendChatMessage(username, target, requestMsg);
            if (result.startsWith("SUCCESS")) {
                JOptionPane.showMessageDialog(this, "已发送好友申请给 " + target + "，请等待对方回复", "提示", JOptionPane.INFORMATION_MESSAGE);
                searchField.setText("请输入好友名或好友账号");
                searchField.setForeground(new Color(150, 153, 156));
            } else {
                String msg = result.contains("|") ? result.split("\\|")[1] : "发送失败";
                JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.ERROR_MESSAGE);
            }
        });

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);

        loadFriends();
    }

    private void loadFriends() {
        Thread ft = new Thread(() -> {
            String result = ServerClient.getFriends(username);
            SwingUtilities.invokeLater(() -> {
                friendsContainer.removeAll();
                if (result.startsWith("SUCCESS")) {
                    String data = result.substring("SUCCESS|".length());
                    if (data.isEmpty()) {
                        friendsContainer.setLayout(new GridBagLayout());
                        friendsContainer.add(placeholder);
                    } else {
                        friendsContainer.setLayout(new BoxLayout(friendsContainer, BoxLayout.Y_AXIS));
                        String[] entries = data.split(";");
                        for (String entry : entries) {
                            String[] parts = entry.split(",");
                            if (parts.length == 2) {
                                String friendName = parts[0];
                                int state = Integer.parseInt(parts[1]);
                                friendsContainer.add(createFriendCard(friendName, state));
                                friendsContainer.add(Box.createVerticalStrut(8));
                            }
                        }
                    }
                } else {
                    friendsContainer.setLayout(new GridBagLayout());
                    JLabel errLabel = new JLabel("加载好友列表失败", JLabel.CENTER);
                    errLabel.setForeground(new Color(255, 100, 100));
                    friendsContainer.add(errLabel);
                }
                friendsContainer.revalidate();
                friendsContainer.repaint();
            });
        });
        ft.setDaemon(true);
        ft.start();
    }

    private JPanel createFriendCard(String friendName, int state) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(new Color(60, 63, 65));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(90, 93, 96), 1),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        card.setMaximumSize(new Dimension(340, 56));

        JPanel left = new JPanel();
        left.setBackground(new Color(60, 63, 65));
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(friendName);
        nameLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        nameLabel.setForeground(Color.WHITE);

        Color stateColor = state == 1 ? new Color(100, 255, 100) : new Color(180, 180, 180);
        String stateText = state == 1 ? "\u25CF 在线" : "\u25CF 离线";
        JLabel stateLabel = new JLabel(stateText);
        stateLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        stateLabel.setForeground(stateColor);

        left.add(nameLabel);
        left.add(stateLabel);

        // 右侧按钮组
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBtns.setBackground(new Color(60, 63, 65));

        JButton recordBtn = new JButton("个人纪录");
        recordBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
        recordBtn.setForeground(Color.WHITE);
        recordBtn.setBackground(BTN_BASE);
        recordBtn.setFocusPainted(false);
        recordBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        recordBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        recordBtn.setPreferredSize(new Dimension(80, 26));
        recordBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { recordBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { recordBtn.setBackground(BTN_BASE); }
        });
        recordBtn.addActionListener(e -> {
            new FriendRecordsDialog(this, friendName).setVisible(true);
        });

        rightBtns.add(recordBtn);

        // moyu官方 不允许删除
        if (!"moyu官方".equals(friendName)) {
            JButton deleteBtn = new JButton("删除");
            deleteBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
            deleteBtn.setForeground(Color.WHITE);
            deleteBtn.setBackground(new Color(160, 40, 40));
            deleteBtn.setFocusPainted(false);
            deleteBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            deleteBtn.setPreferredSize(new Dimension(60, 26));
            deleteBtn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { deleteBtn.setBackground(new Color(200, 50, 50)); }
                public void mouseExited(MouseEvent e) { deleteBtn.setBackground(new Color(160, 40, 40)); }
            });
            deleteBtn.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(this,
                    "确认要删除好友 " + friendName + " 吗？",
                    "删除好友",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    String res = ServerClient.deleteFriend(username, friendName);
                    if (res.startsWith("SUCCESS")) {
                        loadFriends();
                    } else {
                        String msg = res.contains("|") ? res.split("\\|")[1] : "删除失败";
                        JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            rightBtns.add(deleteBtn);
        }

        card.add(left, BorderLayout.WEST);
        card.add(rightBtns, BorderLayout.EAST);
        return card;
    }

    private void styleField(JComponent c) {
        c.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        c.setBackground(new Color(60, 63, 65));
        c.setForeground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100), 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
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

    private JLabel createEmojiLabel(String emoji, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
