import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 扫雷对决匹配房间
 */
public class MineMatchRoom extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color CARD_BG = new Color(60, 63, 65);
    private static final Color ACCENT = new Color(0, 120, 215);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final Color READY_COLOR = new Color(40, 180, 40);
    private static final Color READY_HOVER = new Color(30, 160, 30);

    private String username;
    private int userId;
    private String mode;
    private int maxPlayers;
    private int roomId;
    private boolean isCreator;
    private boolean isReady;
    private boolean matching;
    private boolean gameStarting;
    private boolean gamePageStarted; // 防重复调用 startGamePage

    // 返回父窗口（扫雷主页）引用
    private JFrame parentMinesweeper;

    // 头像槽位
    private JPanel[] slotPanels;
    private JLabel[] slotAvatars;
    private JLabel[] slotNameLabels;
    private boolean[] slotOccupied;

    // 匹配/准备/开始按钮
    private JButton actionBtn;
    private JLabel statusLabel;

    // 轮询定时器
    private javax.swing.Timer pollTimer;
    private long lastMaxChangeTime = 0; // 防竞态：最近修改人数的时间戳

    // 匹配倒计时
    private javax.swing.Timer matchTimer;
    private int matchCountdown = 60;

    // 人数显示标签
    private JLabel countDisplayLabel;

    public MineMatchRoom(String username, int userId, String mode, int maxPlayers, boolean isCreator, int roomId, JFrame parentMinesweeper) {
        this.username = username;
        this.userId = userId;
        this.mode = mode;
        this.maxPlayers = maxPlayers;
        this.isCreator = isCreator;
        this.roomId = roomId;
        this.parentMinesweeper = parentMinesweeper;
        this.isReady = false;

        // 确保全局推送已启动（用户可能从未打开过消息中心）
        MessageCenter.startGlobalPush(username, userId);

        // 维持全局扫雷主页引用（用于通过邀请加入后能回到主页）
        if (parentMinesweeper != null) {
            globalMinesweeper = parentMinesweeper;
        }

        // 注册活跃房间用于接收推送
        if (roomId > 0) {
            activeRooms.put(roomId, this);
        }

        setTitle("扫雷对决 — " + mode + "模式");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                MessageCenter.disposeActive();
                stopPoll();
                activeRooms.remove(roomId);
                if (roomId > 0) {
                    ServerClient.duelLeave(roomId, username);
                }
                // 恢复扫雷主页
                if (parentMinesweeper != null && parentMinesweeper.isDisplayable()) {
                    parentMinesweeper.setVisible(true);
                    parentMinesweeper.setLocationRelativeTo(null);
                } else if (globalMinesweeper != null && globalMinesweeper.isDisplayable()) {
                    globalMinesweeper.setVisible(true);
                    globalMinesweeper.setLocationRelativeTo(null);
                } else {
                    // 没有父窗口则彻底退出
                    System.exit(0);
                }
            }
        });

        buildUI();
        pack();
        setLocationRelativeTo(null);

        if (roomId > 0) {
            startPoll();
        }
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(420, 480));

        // 标题栏
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        JLabel title = new JLabel("扫雷对决 · " + mode, JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);
        main.add(header, BorderLayout.NORTH);

        // 中间内容区
        JPanel center = new JPanel();
        center.setBackground(BG);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(5, 20, 10, 20));

        // 人数显示（只读）
        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        countPanel.setBackground(BG);
        countDisplayLabel = new JLabel("对决人数：" + maxPlayers + " 人");
        countDisplayLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        countDisplayLabel.setForeground(new Color(255, 200, 50));
        countPanel.add(countDisplayLabel);
        center.add(countPanel);
        center.add(Box.createVerticalStrut(15));

        // 玩家头像槽位
        JPanel slotsWrapper = new JPanel(new GridBagLayout());
        slotsWrapper.setBackground(BG);
        JPanel slotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        slotsPanel.setBackground(BG);

        slotPanels = new JPanel[4];
        slotAvatars = new JLabel[4];
        slotNameLabels = new JLabel[4];
        slotOccupied = new boolean[4];

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            JPanel slot = new JPanel();
            slot.setLayout(new BoxLayout(slot, BoxLayout.Y_AXIS));
            slot.setBackground(BG);
            slot.setPreferredSize(new Dimension(80, 90));
            slot.setMinimumSize(new Dimension(80, 90));
            slot.setMaximumSize(new Dimension(80, 90));

            // 头像（空位时显示+号，点击可邀请好友）
            JLabel avatar = new JLabel("+", JLabel.CENTER);
            avatar.setPreferredSize(new Dimension(64, 64));
            avatar.setMinimumSize(new Dimension(64, 64));
            avatar.setMaximumSize(new Dimension(64, 64));
            avatar.setFont(new Font("Microsoft YaHei", Font.BOLD, 30));
            avatar.setForeground(new Color(150, 153, 156));
            avatar.setBackground(new Color(70, 73, 76));
            avatar.setOpaque(true);
            avatar.setBorder(BorderFactory.createLineBorder(new Color(100, 103, 106), 2));
            avatar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            avatar.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (!slotOccupied[idx]) {
                        showFriendListForInvite(idx);
                    }
                }
                public void mouseEntered(MouseEvent e) {
                    if (!slotOccupied[idx]) {
                        avatar.setBackground(new Color(85, 88, 91));
                    }
                }
                public void mouseExited(MouseEvent e) {
                    if (!slotOccupied[idx]) {
                        avatar.setBackground(new Color(70, 73, 76));
                    }
                }
            });

            // 名字标签
            JLabel nameLabel = new JLabel("", JLabel.CENTER);
            nameLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            nameLabel.setForeground(new Color(180, 183, 186));
            nameLabel.setPreferredSize(new Dimension(80, 16));
            nameLabel.setMaximumSize(new Dimension(80, 16));

            slot.add(avatar);
            slot.add(Box.createVerticalStrut(4));
            slot.add(nameLabel);

            slotPanels[i] = slot;
            slotAvatars[i] = avatar;
            slotNameLabels[i] = nameLabel;
            slotOccupied[i] = false;

            if (i < maxPlayers) {
                slotsPanel.add(slot);
            }
        }
        slotsWrapper.add(slotsPanel);
        center.add(slotsWrapper);
        center.add(Box.createVerticalStrut(15));

        // 状态标签
        statusLabel = new JLabel("等待玩家加入...", JLabel.CENTER);
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 183, 186));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(10));

        // 匹配/准备 按钮
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrap.setBackground(BG);
        actionBtn = new JButton("匹配");
        actionBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        actionBtn.setForeground(Color.WHITE);
        actionBtn.setBackground(ACCENT);
        actionBtn.setFocusPainted(false);
        actionBtn.setBorder(BorderFactory.createEmptyBorder(12, 40, 12, 40));
        actionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        actionBtn.setEnabled(true);
        actionBtn.addActionListener(e -> {
            String text = actionBtn.getText();
            if (text.equals("匹配")) {
                // 发送匹配请求给服务器
                startMatching();
            } else if (text.equals("取消匹配")) {
                // 取消匹配
                cancelMatching();
            } else if (text.equals("准备") || text.equals("取消准备")) {
                toggleReady();
            }
        });
        btnWrap.add(actionBtn);
        center.add(btnWrap);

        main.add(center, BorderLayout.CENTER);

        // 底部返回按钮
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.setBackground(BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JButton backBtn = txtBtn("退出房间", Color.WHITE, BTN_BASE, 100, 30);
        backBtn.addActionListener(e -> {
            stopPoll();
            stopMatchTimer();
            if (matching) {
                ServerClient.duelMatchCancel(roomId, username);
            }
            activeRooms.remove(roomId);
            if (roomId > 0) ServerClient.duelLeave(roomId, username);
            if (parentMinesweeper != null && parentMinesweeper.isDisplayable()) {
                parentMinesweeper.setVisible(true);
                parentMinesweeper.setLocationRelativeTo(null);
            } else if (globalMinesweeper != null && globalMinesweeper.isDisplayable()) {
                globalMinesweeper.setVisible(true);
                globalMinesweeper.setLocationRelativeTo(null);
            } else {
                // 都没有则回到摸鱼中心
                FishGrabbingHome.showActiveInstance();
            }
            dispose();
        });
        bottom.add(backBtn);
        main.add(bottom, BorderLayout.SOUTH);

        getContentPane().add(main);

        // 如果是创建者，把自己加到第一个槽位
        if (isCreator && roomId > 0) {
            setSlotPlayer(0, username, false);
            updateSlotsVisibility();
        }
    }

    private int countOccupied() {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            if (slotOccupied[i]) count++;
        }
        return count;
    }

    /** 显示好友列表用于邀请 */
    private void showFriendListForInvite(int slotIndex) {
        JDialog dialog = new JDialog(this, "邀请好友", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(280, 350);
        dialog.setLocationRelativeTo(this);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        list.setBackground(CARD_BG);
        list.setForeground(Color.WHITE);
        list.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        list.setSelectionBackground(ACCENT);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        dialog.add(sp, BorderLayout.CENTER);

        JButton inviteBtn = new JButton("邀请");
        inviteBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        inviteBtn.setForeground(Color.WHITE);
        inviteBtn.setBackground(ACCENT);
        inviteBtn.setFocusPainted(false);
        inviteBtn.setEnabled(false);
        inviteBtn.addActionListener(e -> {
            String friend = list.getSelectedValue();
            if (friend != null) {
                dialog.dispose();
                sendDuelInvite(friend);
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setBackground(CARD_BG);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        btnPanel.add(inviteBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        list.addListSelectionListener(e -> inviteBtn.setEnabled(list.getSelectedValue() != null));

        // 加载好友列表
        Thread loader = new Thread(() -> {
            String result = ServerClient.getFriends(username);
            SwingUtilities.invokeLater(() -> {
                if (result.startsWith("SUCCESS")) {
                    String data = result.substring("SUCCESS|".length());
                    if (!data.isEmpty()) {
                        for (String entry : data.split(";")) {
                            String[] parts = entry.split(",");
                            if (parts.length >= 1 && !"moyu官方".equals(parts[0]) && !parts[0].equals(username)) {
                                listModel.addElement(parts[0]);
                            }
                        }
                    }
                }
                if (listModel.isEmpty()) {
                    listModel.addElement("暂无好友");
                }
            });
        });
        loader.setDaemon(true);
        loader.start();

        dialog.setVisible(true);
    }

    /** 发送对决邀请 */
    private void sendDuelInvite(String friendName) {
        String inviteMsg = "DUEL_INVITE:" + roomId + ":" + mode + ":" + maxPlayers + ":扫雷:" + System.currentTimeMillis();
        Thread t = new Thread(() -> {
            String result = ServerClient.sendChatMessage(username, friendName, inviteMsg);
            SwingUtilities.invokeLater(() -> {
                if (result.startsWith("SUCCESS")) {
                    JOptionPane.showMessageDialog(this, "已向 " + friendName + " 发送对决邀请！", "邀请已发送", JOptionPane.INFORMATION_MESSAGE);
                    // 自动打开消息中心并导航到该好友聊天
                    MessageCenter.openAndNavigate(username, userId, friendName, parentMinesweeper);
                } else {
                    String msg = result.contains("|") ? result.split("\\|")[1] : "发送失败";
                    JOptionPane.showMessageDialog(this, msg, "邀请失败", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 设置槽位玩家 */
    private void setSlotPlayer(int index, String name, boolean ready) {
        if (index >= 0 && index < 4) {
            slotOccupied[index] = true;
            slotAvatars[index].setText(name.substring(0, 1).toUpperCase());
            slotAvatars[index].setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
            slotAvatars[index].setForeground(Color.WHITE);
            slotAvatars[index].setBackground(ready ? READY_COLOR : ACCENT);
            slotAvatars[index].setBorder(BorderFactory.createLineBorder(ready ? new Color(30, 150, 30) : new Color(0, 100, 200), 3));
            slotNameLabels[index].setText(name);
        }
    }

    /** 清理槽位 */
    private void clearSlot(int index) {
        if (index >= 0 && index < 4) {
            slotOccupied[index] = false;
            slotAvatars[index].setText("+");
            slotAvatars[index].setFont(new Font("Microsoft YaHei", Font.BOLD, 30));
            slotAvatars[index].setForeground(new Color(150, 153, 156));
            slotAvatars[index].setBackground(new Color(70, 73, 76));
            slotAvatars[index].setBorder(BorderFactory.createLineBorder(new Color(100, 103, 106), 2));
            slotNameLabels[index].setText("");
        }
    }

    private void resetSlots() {
        for (int i = 0; i < 4; i++) {
            if (i == 0 && isCreator && roomId > 0) {
                setSlotPlayer(0, username, false);
            } else {
                clearSlot(i);
            }
        }
        updateSlotsVisibility();
    }

    private void updateSlotsVisibility() {
        JPanel slotsPanel = (JPanel) slotPanels[0].getParent();
        slotsPanel.removeAll();
        for (int i = 0; i < maxPlayers; i++) {
            slotsPanel.add(slotPanels[i]);
        }
        slotsPanel.revalidate();
        slotsPanel.repaint();
    }

    /** 切换准备状态 */
    private void toggleReady() {
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelReady(roomId, username);
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    applyRoomState(parseRoomState(resp));
                    // 检查是否全部准备
                    if (resp.contains("ALL_READY")) {
                        startGamePage();
                    }
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 发起匹配 */
    private void startMatching() {
        matching = true;
        actionBtn.setText("取消匹配");
        actionBtn.setBackground(new Color(180, 120, 30));
        statusLabel.setText("匹配中... 60秒");
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelMatch(roomId, username, mode, maxPlayers);
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    startMatchTimer();
                } else {
                    matching = false;
                    actionBtn.setText("匹配");
                    actionBtn.setBackground(ACCENT);
                    statusLabel.setText("匹配失败，请重试");
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 取消匹配 */
    private void cancelMatching() {
        stopMatchTimer();
        matching = false;
        actionBtn.setText("匹配");
        actionBtn.setBackground(ACCENT);
        actionBtn.setEnabled(true);
        statusLabel.setText("已取消匹配");
        Thread t = new Thread(() -> ServerClient.duelMatchCancel(roomId, username));
        t.setDaemon(true);
        t.start();
    }

    /** 启动匹配倒计时（60秒） */
    private void startMatchTimer() {
        matchCountdown = 60;
        if (matchTimer != null) matchTimer.stop();
        matchTimer = new javax.swing.Timer(1000, e -> {
            matchCountdown--;
            if (matchCountdown > 0) {
                statusLabel.setText("匹配中... " + matchCountdown + "秒");
            } else {
                stopMatchTimer();
                statusLabel.setText("匹配超时，等待机器人加入...");
            }
        });
        matchTimer.start();
    }

    /** 停止匹配倒计时 */
    private void stopMatchTimer() {
        if (matchTimer != null) {
            matchTimer.stop();
            matchTimer = null;
        }
    }

    /** 通知匹配成功，停止倒计时 */
    public void onMatchSuccess() {
        stopMatchTimer();
        statusLabel.setText("匹配成功！");
    }

    /** 通知机器人已加入 */
    public void onBotsJoined() {
        stopMatchTimer();
        matching = false;
        statusLabel.setText("机器人已加入，请准备！");
    }

    /** 解析服务器返回的房间状态并更新UI */
    private RoomState parseRoomState(String resp) {
        RoomState state = new RoomState();
        if (!resp.startsWith("SUCCESS|")) return state;
        String data = resp.substring("SUCCESS|".length());
        String[] parts = data.split("\\|");
        // 兼容两种格式：SUCCESS|roomId|mode|max|gameType|players...
        // 和 SUCCESS|mode|max|gameType|players...（推送已去掉 roomId）
        int offset = 0;
        if (parts.length >= 4) {
            try {
                Integer.parseInt(parts[0]);
                offset = 1; // 第一个字段是 roomId，向后偏移
            } catch (NumberFormatException e) {
                offset = 0; // 第一个字段就是 mode
            }
            if (parts.length >= offset + 3) {
                state.mode = parts[offset];
                try { state.maxPlayers = Integer.parseInt(parts[offset + 1]); } catch (Exception ex) {}
                state.gameType = parts[offset + 2];
                for (int i = offset + 3; i < parts.length; i++) {
                    String[] p = parts[i].split(",");
                    if (p.length == 2) {
                        state.players.put(p[0], "1".equals(p[1]));
                    }
                }
            }
        }
        System.out.println("[MineMatchRoom] parseRoomState: input=" + resp + " -> mode=" + state.mode
                + ", max=" + state.maxPlayers + ", players=" + state.players);
        return state;
    }

    private void applyRoomState(RoomState state) {
        System.out.println("[MineMatchRoom] applyRoomState: roomId=" + roomId + ", localUser=" + username
                + ", players=" + state.players + ", max=" + state.maxPlayers + ", localReady=" + isReady
                + ", matching=" + matching + ", gamePageStarted=" + gamePageStarted);

        // 防竞态：创建者3秒内本地改了人数且服务器值不同 → 不覆盖
        boolean creatorRecentlyChanged = isCreator && lastMaxChangeTime > 0
                && (System.currentTimeMillis() - lastMaxChangeTime) < 3000
                && state.maxPlayers != this.maxPlayers;

        // 更新人数
        if (state.maxPlayers > 0 && !creatorRecentlyChanged) {
            this.maxPlayers = state.maxPlayers;
        }

        // 人数显示更新
        if (countDisplayLabel != null) {
            countDisplayLabel.setText("对决人数：" + this.maxPlayers + " 人");
        }

        // 更新槽位
        int idx = 0;
        for (Map.Entry<String, Boolean> e : state.players.entrySet()) {
            if (idx < 4) {
                setSlotPlayer(idx, e.getKey(), e.getValue());
                // 更新自己的准备状态
                if (e.getKey().equals(username)) {
                    isReady = e.getValue();
                }
            }
            idx++;
        }
        // 清空多余槽位
        for (int i = idx; i < maxPlayers; i++) {
            clearSlot(i);
        }
        updateSlotsVisibility();

        // 更新按钮状态（已进入游戏启动流程后不再覆盖）
        if (!gamePageStarted && state.players.size() >= maxPlayers) {
            if (matching) {
                System.out.println("[MineMatchRoom] 房间已满，强制停止匹配倒计时");
            }
            matching = false;
            stopMatchTimer();
            actionBtn.setText(isReady ? "取消准备" : "准备");
            actionBtn.setBackground(isReady ? new Color(180, 50, 50) : READY_COLOR);
            actionBtn.setEnabled(true);
            statusLabel.setText("房间已满，请准备！");
        } else if (!gamePageStarted) {
            if (matching) {
                actionBtn.setText("取消匹配");
                actionBtn.setEnabled(true);
                actionBtn.setBackground(new Color(180, 120, 30));
            } else {
                actionBtn.setText("匹配");
                actionBtn.setEnabled(true);
            }
            actionBtn.setBackground(ACCENT);
            statusLabel.setText("等待玩家加入... (" + state.players.size() + "/" + maxPlayers + ")");
        }

        // 检测全员准备：非最后准备者通过轮询也能触发启动流程
        if (state.players.size() >= maxPlayers && !gamePageStarted) {
            boolean allReady = true;
            for (Boolean r : state.players.values()) {
                if (!r) { allReady = false; break; }
            }
            if (allReady) {
                startGamePage();
            }
        }
    }

    /** 开始对决游戏 */
    private void startGamePage() {
        if (gamePageStarted) {
            System.out.println("[MineMatchRoom] startGamePage 被调用但 gamePageStarted=true，跳过");
            return;
        }
        gamePageStarted = true;
        System.out.println("[MineMatchRoom] startGamePage 启动, roomId=" + roomId + ", username=" + username);

        stopPoll();
        statusLabel.setText("全部准备就绪，等待游戏即将开始...");
        actionBtn.setText("游戏中");
        actionBtn.setEnabled(false);
        actionBtn.setBackground(new Color(120, 120, 120));

        // 收集所有玩家
        java.util.List<String> allPlayers = new java.util.ArrayList<>();
        for (int i = 0; i < maxPlayers; i++) {
            if (slotOccupied[i] && slotNameLabels[i] != null) {
                String name = slotNameLabels[i].getText();
                if (name != null && !name.isEmpty()) {
                    allPlayers.add(name);
                }
            }
        }

        // 打开对决聊天大厅（不阻塞游戏启动）
        try {
            MessageCenter.openDuelLobby(roomId, username, allPlayers);
        } catch (Exception e) {
            System.out.println("[匹配房间] 打开对决聊天失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 直接查询服务器获取种子并启动游戏，不再依赖推送
        Thread startThread = new Thread(() -> {
            try {
                String resp = ServerClient.duelGameState(roomId);
                System.out.println("[MineMatchRoom] 查询 DUEL_GAME_STATE: " + resp);
                if (resp.startsWith("SUCCESS|STARTED|")) {
                    String[] p = resp.split("\\|");
                    if (p.length >= 4) {
                        long seed = Long.parseLong(p[2]);
                        String gsMode = p[3];
                        SwingUtilities.invokeLater(() -> {
                            if (!gameStarting) {
                                gameStarting = true;
                                startGame(seed, gsMode);
                            }
                        });
                        return;
                    }
                }
                // 如果还没 STARTED，短暂轮询等待
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(200);
                    String poll = ServerClient.duelGameState(roomId);
                    System.out.println("[MineMatchRoom] 轮询 DUEL_GAME_STATE[" + i + "]: " + poll);
                    if (poll.startsWith("SUCCESS|STARTED|")) {
                        String[] p = poll.split("\\|");
                        if (p.length >= 4) {
                            long seed = Long.parseLong(p[2]);
                            String gsMode = p[3];
                            SwingUtilities.invokeLater(() -> {
                                if (!gameStarting) {
                                    gameStarting = true;
                                    startGame(seed, gsMode);
                                }
                            });
                            return;
                        }
                    }
                }
                // 仍然失败，重置状态允许重试
                SwingUtilities.invokeLater(() -> {
                    gamePageStarted = false;
                    statusLabel.setText("游戏启动失败，请重新准备");
                    actionBtn.setText(isReady ? "取消准备" : "准备");
                    actionBtn.setEnabled(true);
                    actionBtn.setBackground(isReady ? new Color(180, 50, 50) : READY_COLOR);
                    if (pollTimer == null || !pollTimer.isRunning()) startPoll();
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    gamePageStarted = false;
                    statusLabel.setText("游戏启动异常，请重新准备");
                    actionBtn.setText(isReady ? "取消准备" : "准备");
                    actionBtn.setEnabled(true);
                    actionBtn.setBackground(isReady ? new Color(180, 50, 50) : READY_COLOR);
                    if (pollTimer == null || !pollTimer.isRunning()) startPoll();
                });
            }
        });
        startThread.setDaemon(true);
        startThread.start();
    }

    /** 服务器推送的游戏开始（由 MessageCenter 调用） */
    public static void receiveGameStart(int roomId, long seed, String mode) {
        MineMatchRoom room = activeRooms.get(roomId);
        if (room != null && !room.gameStarting) {
            room.gameStarting = true;
            SwingUtilities.invokeLater(() -> room.startGame(seed, mode));
        }
    }

    /** 启动对决游戏 */
    private void startGame(long seed, String mode) {
        System.out.println("[MineMatchRoom] startGame 被调用, roomId=" + roomId + ", seed=" + seed + ", mode=" + mode);
        // 收集所有玩家列表
        java.util.List<String> allPlayers = new java.util.ArrayList<>();
        for (int i = 0; i < maxPlayers; i++) {
            if (slotOccupied[i] && slotNameLabels[i] != null) {
                String name = slotNameLabels[i].getText();
                if (name != null && !name.isEmpty()) {
                    allPlayers.add(name);
                }
            }
        }

        // 隐藏本窗口，打开游戏
        setVisible(false);
        MineDuelGame game = new MineDuelGame(username, roomId, seed, mode, allPlayers, this);
        game.setVisible(true);
    }

    /** 游戏结束后重置房间状态（供 MineDuelGame 调用） */
    public void resetForNewGame() {
        // 重置准备状态（服务器端也已重置）
        isReady = false;
        matching = false;
        gameStarting = false;
        gamePageStarted = false;
        stopMatchTimer();
        // 恢复动作按钮
        actionBtn.setText("准备");
        actionBtn.setBackground(new Color(0, 120, 215));
        actionBtn.setEnabled(true);
        statusLabel.setText("游戏结束，可再次准备");
        // 重启轮询以接收房间状态变更（玩家加入/离开等）
        if (pollTimer == null || !pollTimer.isRunning()) {
            startPoll();
        }
    }

    /** 获取扫雷父窗口引用 */
    public JFrame getParentMinesweeper() {
        return parentMinesweeper;
    }

    /** 开始轮询房间状态 */
    private void startPoll() {
        pollTimer = new javax.swing.Timer(2000, e -> {
            Thread t = new Thread(() -> {
                String resp = ServerClient.duelInfo(roomId);
                SwingUtilities.invokeLater(() -> {
                    if (resp.startsWith("SUCCESS")) {
                        RoomState state = parseRoomState(resp);
                        applyRoomState(state);
                    }
                });
            });
            t.setDaemon(true);
            t.start();
        });
        pollTimer.start();
    }

    private void stopPoll() {
        if (pollTimer != null) pollTimer.stop();
    }

    private JButton txtBtn(String text, Color fg, Color bg, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        b.setForeground(fg);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { b.setBackground(bg); }
        });
        return b;
    }

    // ===== 内部数据类 =====

    static class RoomState {
        String mode = "";
        int maxPlayers = 2;
        String gameType = "";
        java.util.LinkedHashMap<String, Boolean> players = new java.util.LinkedHashMap<>();
    }

    // ===== 从消息中心接受邀请的静态入口 =====
    // 活跃房间注册表（roomId -> MineMatchRoom）
    private static final java.util.concurrent.ConcurrentHashMap<Integer, MineMatchRoom> activeRooms
            = new java.util.concurrent.ConcurrentHashMap<>();

    // 主扫雷窗口的全局引用（用于通过邀请加入后能回到主页）
    private static JFrame globalMinesweeper = null;

    // 摸鱼中心窗口的全局引用（用于接受邀请后隐藏摸鱼中心）
    private static JFrame globalFishHome = null;

    public static void setGlobalFishHome(JFrame fh) { globalFishHome = fh; }
    public static JFrame getGlobalFishHome() { return globalFishHome; }
    public static JFrame getGlobalMinesweeper() { return globalMinesweeper; }

    /** 获取指定房间ID的活跃房间实例（供 MessageCenter 匹配路由使用） */
    public static MineMatchRoom getActiveRoom(int roomId) {
        return activeRooms.get(roomId);
    }

    /** 接收来自 MessageCenter 的 DUEL_STATE 推送（无需等轮询） */
    public static void receiveStatePush(int roomId, String stateData) {
        MineMatchRoom room = activeRooms.get(roomId);
        if (room != null) {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[MineMatchRoom] 收到 DUEL_STATE 推送: roomId=" + roomId + ", data=" + stateData);
                RoomState state = room.parseRoomState("SUCCESS|" + stateData);
                System.out.println("[MineMatchRoom] 解析后 players=" + state.players + ", maxPlayers=" + state.maxPlayers);
                room.applyRoomState(state);
            });
        } else {
            System.out.println("[MineMatchRoom] 收到 DUEL_STATE 推送但房间不存在: roomId=" + roomId);
        }
    }

    /** 匹配成功后，服务器通知切换到目标房间 */
    public static void receiveMatchMove(int targetRoomId, String mode, int maxPlayers) {
        SwingUtilities.invokeLater(() -> {
            // 找到当前活跃的房间（被匹配者自己的房间），关闭它
            MineMatchRoom oldRoom = null;
            for (MineMatchRoom r : activeRooms.values()) {
                if (r.roomId != targetRoomId) {
                    oldRoom = r;
                    break;
                }
            }
            if (oldRoom != null) {
                String username = oldRoom.username;
                int userId = oldRoom.userId;
            oldRoom.stopPoll();
            oldRoom.stopMatchTimer();
            MineMatchRoom.activeRooms.remove(oldRoom.roomId);
            oldRoom.dispose();
                // 加入目标房间
                joinRoom(username, userId, targetRoomId, mode, maxPlayers);
            }
        });
    }

    /** 匹配开始时收到服务器推送 */
    public void onMatchStartPush(int countdown) {
        matching = true;
        matchCountdown = countdown;
        actionBtn.setText("取消匹配");
        actionBtn.setBackground(new Color(180, 120, 30));
        statusLabel.setText("匹配中... " + countdown + "秒");
        startMatchTimer();
    }

    /** 机器人加入后收到服务器推送 */
    public void onBotsJoinedPush() {
        onBotsJoined();
    }

    /** 立即刷新房间状态（响应推送通知） */
    public void refreshRoomState() {
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelInfo(roomId);
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    RoomState state = parseRoomState(resp);
                    applyRoomState(state);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    public static void joinRoom(String username, int userId, int roomId, String mode, int maxPlayers) {
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelJoin(roomId, username);
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    MineMatchRoom room = new MineMatchRoom(username, userId, mode, maxPlayers, false, roomId, globalMinesweeper);
                    // 初始化已有玩家到槽位
                    room.applyRoomState(room.parseRoomState(resp));
                    room.setVisible(true);
                } else {
                    String msg = resp.contains("|") ? resp.split("\\|")[1] : "加入失败";
                    JOptionPane.showMessageDialog(null, msg, "加入失败", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }
}
