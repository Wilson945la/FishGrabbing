import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * 俄罗斯方块对决匹配房间
 */
public class TetrisMatchRoom extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color CARD_BG = new Color(60, 63, 65);
    private static final Color ACCENT = new Color(0, 120, 215);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final Color READY_COLOR = new Color(40, 180, 40);

    private String username;
    private int userId;
    private String mode;      // 经典/困难/叠叠乐
    private int maxPlayers;
    private int roomId;
    private boolean isCreator;
    private boolean isReady;
    private boolean matching;
    private boolean gameStarting;
    private boolean gamePageStarted;

    private JFrame parentHome; // TetrisHome 引用

    private JPanel[] slotPanels;
    private JLabel[] slotAvatars;
    private JLabel[] slotNameLabels;
    private boolean[] slotOccupied;

    private JButton actionBtn;
    private JLabel statusLabel;
    private javax.swing.Timer pollTimer;
    private long lastMaxChangeTime = 0;
    private javax.swing.Timer matchTimer;
    private int matchCountdown = 60;
    private JLabel countDisplayLabel;

    // 活跃房间注册表
    private static final Map<Integer, TetrisMatchRoom> activeRooms = new java.util.concurrent.ConcurrentHashMap<>();
    private static JFrame globalTetrisHome = null;
    private static JFrame globalFishHome = null;

    public static void setGlobalFishHome(JFrame fh) { globalFishHome = fh; }
    public static JFrame getGlobalFishHome() { return globalFishHome; }
    public static JFrame getGlobalTetrisHome() { return globalTetrisHome; }

    public TetrisMatchRoom(String username, int userId, String mode, int maxPlayers,
                           boolean isCreator, int roomId, JFrame parentHome) {
        this.username = username;
        this.userId = userId;
        this.mode = mode;
        this.maxPlayers = maxPlayers;
        this.isCreator = isCreator;
        this.roomId = roomId;
        this.parentHome = parentHome;
        this.isReady = false;

        MessageCenter.startGlobalPush(username, userId);

        if (parentHome != null) {
            globalTetrisHome = parentHome;
        }
        if (roomId > 0) {
            activeRooms.put(roomId, this);
        }

        setTitle("俄罗斯方块对决 - " + mode + "模式");
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
                if (parentHome != null && parentHome.isDisplayable()) {
                    parentHome.setVisible(true);
                    parentHome.setLocationRelativeTo(null);
                } else if (globalTetrisHome != null && globalTetrisHome.isDisplayable()) {
                    globalTetrisHome.setVisible(true);
                    globalTetrisHome.setLocationRelativeTo(null);
                } else {
                    FishGrabbingHome home = FishGrabbingHome.getActiveInstance();
                    if (home != null) {
                        home.setVisible(true);
                    } else {
                        System.exit(0);
                    }
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

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        JLabel title = new JLabel("俄罗斯方块对决 · " + mode, JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);
        main.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setBackground(BG);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(5, 20, 10, 20));

        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        countPanel.setBackground(BG);
        countDisplayLabel = new JLabel("对决人数：" + maxPlayers + " 人");
        countDisplayLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        countDisplayLabel.setForeground(new Color(255, 200, 50));
        countPanel.add(countDisplayLabel);
        center.add(countPanel);
        center.add(Box.createVerticalStrut(15));

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
                    if (!slotOccupied[idx]) avatar.setBackground(new Color(85, 88, 91));
                }
                public void mouseExited(MouseEvent e) {
                    if (!slotOccupied[idx]) avatar.setBackground(new Color(70, 73, 76));
                }
            });

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

            if (i < maxPlayers) slotsPanel.add(slot);
        }
        slotsWrapper.add(slotsPanel);
        center.add(slotsWrapper);
        center.add(Box.createVerticalStrut(15));

        statusLabel = new JLabel("等待玩家加入...", JLabel.CENTER);
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 183, 186));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(10));

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
                startMatching();
            } else if (text.equals("取消匹配")) {
                cancelMatching();
            } else if (text.equals("准备") || text.equals("取消准备")) {
                toggleReady();
            }
        });
        btnWrap.add(actionBtn);
        center.add(btnWrap);
        main.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.setBackground(BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JButton backBtn = txtBtn("退出房间", Color.WHITE, BTN_BASE, 100, 30);
        backBtn.addActionListener(e -> {
            stopPoll();
            stopMatchTimer();
            if (matching) ServerClient.duelMatchCancel(roomId, username);
            activeRooms.remove(roomId);
            if (roomId > 0) ServerClient.duelLeave(roomId, username);
            if (parentHome != null && parentHome.isDisplayable()) {
                parentHome.setVisible(true);
                parentHome.setLocationRelativeTo(null);
            } else if (globalTetrisHome != null && globalTetrisHome.isDisplayable()) {
                globalTetrisHome.setVisible(true);
                globalTetrisHome.setLocationRelativeTo(null);
            } else {
                FishGrabbingHome.showActiveInstance();
            }
            dispose();
        });
        bottom.add(backBtn);
        main.add(bottom, BorderLayout.SOUTH);

        getContentPane().add(main);

        if (isCreator && roomId > 0) {
            setSlotPlayer(0, username, false);
            updateSlotsVisibility();
        }
    }

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

    private void sendDuelInvite(String friendName) {
        String inviteMsg = "DUEL_INVITE:" + roomId + ":" + mode + ":" + maxPlayers + ":俄罗斯方块:" + System.currentTimeMillis();
        Thread t = new Thread(() -> {
            String result = ServerClient.sendChatMessage(username, friendName, inviteMsg);
            SwingUtilities.invokeLater(() -> {
                if (result.startsWith("SUCCESS")) {
                    JOptionPane.showMessageDialog(this, "已向 " + friendName + " 发送对决邀请！", "邀请已发送", JOptionPane.INFORMATION_MESSAGE);
                    MessageCenter.openAndNavigate(username, userId, friendName, parentHome);
                } else {
                    String msg = result.contains("|") ? result.split("\\|")[1] : "发送失败";
                    JOptionPane.showMessageDialog(this, msg, "邀请失败", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

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

    private void updateSlotsVisibility() {
        JPanel slotsPanel = (JPanel) slotPanels[0].getParent();
        slotsPanel.removeAll();
        for (int i = 0; i < maxPlayers; i++) {
            slotsPanel.add(slotPanels[i]);
        }
        slotsPanel.revalidate();
        slotsPanel.repaint();
    }

    private void toggleReady() {
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelReady(roomId, username);
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    applyRoomState(parseRoomState(resp));
                    if (resp.contains("ALL_READY")) {
                        startGamePage();
                    }
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

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

    private void stopMatchTimer() {
        if (matchTimer != null) { matchTimer.stop(); matchTimer = null; }
    }

    public void onMatchSuccess() {
        stopMatchTimer();
        statusLabel.setText("匹配成功！");
    }

    public void onBotsJoined() {
        stopMatchTimer();
        matching = false;
        statusLabel.setText("机器人已加入，请准备！");
    }

    static class RoomState {
        String mode = "";
        int maxPlayers = 2;
        String gameType = "";
        LinkedHashMap<String, Boolean> players = new LinkedHashMap<>();
    }

    RoomState parseRoomState(String resp) {
        RoomState state = new RoomState();
        if (!resp.startsWith("SUCCESS|")) return state;
        String data = resp.substring("SUCCESS|".length());
        String[] parts = data.split("\\|");
        int offset = 0;
        if (parts.length >= 4) {
            try {
                Integer.parseInt(parts[0]);
                offset = 1;
            } catch (NumberFormatException e) {
                offset = 0;
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
        return state;
    }

    void applyRoomState(RoomState state) {
        boolean creatorRecentlyChanged = isCreator && lastMaxChangeTime > 0
                && (System.currentTimeMillis() - lastMaxChangeTime) < 3000
                && state.maxPlayers != this.maxPlayers;

        if (state.maxPlayers > 0 && !creatorRecentlyChanged) {
            this.maxPlayers = state.maxPlayers;
        }

        if (countDisplayLabel != null) {
            countDisplayLabel.setText("对决人数：" + this.maxPlayers + " 人");
        }

        int idx = 0;
        for (Map.Entry<String, Boolean> e : state.players.entrySet()) {
            if (idx < 4) {
                setSlotPlayer(idx, e.getKey(), e.getValue());
                if (e.getKey().equals(username)) {
                    isReady = e.getValue();
                }
            }
            idx++;
        }
        for (int i = idx; i < maxPlayers; i++) {
            clearSlot(i);
        }
        updateSlotsVisibility();

        if (!gamePageStarted && state.players.size() >= maxPlayers) {
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
                actionBtn.setBackground(ACCENT);
            }
            statusLabel.setText("等待玩家加入... (" + state.players.size() + "/" + maxPlayers + ")");
        }

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

    private void startGamePage() {
        if (gamePageStarted) return;
        gamePageStarted = true;

        stopPoll();
        statusLabel.setText("全部准备就绪，等待游戏即将开始...");
        actionBtn.setText("游戏中");
        actionBtn.setEnabled(false);
        actionBtn.setBackground(new Color(120, 120, 120));

        java.util.List<String> allPlayers = new java.util.ArrayList<>();
        for (int i = 0; i < maxPlayers; i++) {
            if (slotOccupied[i] && slotNameLabels[i] != null) {
                String name = slotNameLabels[i].getText();
                if (name != null && !name.isEmpty()) allPlayers.add(name);
            }
        }

        try {
            MessageCenter.openDuelLobby(roomId, username, allPlayers);
        } catch (Exception e) {
            System.out.println("[Tetris匹配房间] 打开对决聊天失败: " + e.getMessage());
        }

        Thread startThread = new Thread(() -> {
            try {
                String resp = ServerClient.duelGameState(roomId);
                if (resp.startsWith("SUCCESS|STARTED|")) {
                    String[] p = resp.split("\\|");
                    if (p.length >= 4) {
                        long seed = Long.parseLong(p[2]);
                        String gsMode = p[3];
                        SwingUtilities.invokeLater(() -> {
                            if (!gameStarting) { gameStarting = true; startGame(seed, gsMode); }
                        });
                        return;
                    }
                }
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(200);
                    String poll = ServerClient.duelGameState(roomId);
                    if (poll.startsWith("SUCCESS|STARTED|")) {
                        String[] p = poll.split("\\|");
                        if (p.length >= 4) {
                            long seed = Long.parseLong(p[2]);
                            String gsMode = p[3];
                            SwingUtilities.invokeLater(() -> {
                                if (!gameStarting) { gameStarting = true; startGame(seed, gsMode); }
                            });
                            return;
                        }
                    }
                }
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

    public static void receiveGameStart(int roomId, long seed, String mode) {
        TetrisMatchRoom room = activeRooms.get(roomId);
        if (room != null && !room.gameStarting) {
            room.gameStarting = true;
            SwingUtilities.invokeLater(() -> room.startGame(seed, mode));
        }
    }

    private void startGame(long seed, String mode) {
        java.util.List<String> allPlayers = new java.util.ArrayList<>();
        for (int i = 0; i < maxPlayers; i++) {
            if (slotOccupied[i] && slotNameLabels[i] != null) {
                String name = slotNameLabels[i].getText();
                if (name != null && !name.isEmpty()) allPlayers.add(name);
            }
        }

        setVisible(false);
        if ("叠叠乐".equals(mode)) {
            TetrisStackDuelGame game = new TetrisStackDuelGame(username, roomId, mode, allPlayers, this);
            game.setVisible(true);
        } else {
            TetrisDuelGame game = new TetrisDuelGame(username, roomId, mode, allPlayers, this);
            game.setVisible(true);
        }
    }

    public void resetForNewGame() {
        isReady = false;
        matching = false;
        gameStarting = false;
        gamePageStarted = false;
        stopMatchTimer();
        actionBtn.setText("准备");
        actionBtn.setBackground(ACCENT);
        actionBtn.setEnabled(true);
        statusLabel.setText("游戏结束，可再次准备");
        if (pollTimer == null || !pollTimer.isRunning()) startPoll();
    }

    public JFrame getParentHome() { return parentHome; }

    private void startPoll() {
        pollTimer = new javax.swing.Timer(2000, e -> {
            Thread t = new Thread(() -> {
                String resp = ServerClient.duelInfo(roomId);
                SwingUtilities.invokeLater(() -> {
                    if (resp.startsWith("SUCCESS")) {
                        applyRoomState(parseRoomState(resp));
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

    // ===== 静态方法（供 MessageCenter 调用）=====

    public static TetrisMatchRoom getActiveRoom(int roomId) {
        return activeRooms.get(roomId);
    }

    public static void receiveStatePush(int roomId, String stateData) {
        TetrisMatchRoom room = activeRooms.get(roomId);
        if (room != null) {
            SwingUtilities.invokeLater(() -> {
                RoomState state = room.parseRoomState("SUCCESS|" + stateData);
                room.applyRoomState(state);
            });
        }
    }

    public static void receiveMatchMove(int targetRoomId, String mode, int maxPlayers) {
        SwingUtilities.invokeLater(() -> {
            TetrisMatchRoom oldRoom = null;
            for (TetrisMatchRoom r : activeRooms.values()) {
                if (r.roomId != targetRoomId) { oldRoom = r; break; }
            }
            if (oldRoom != null) {
                String username = oldRoom.username;
                int userId = oldRoom.userId;
                oldRoom.stopPoll();
                oldRoom.stopMatchTimer();
                activeRooms.remove(oldRoom.roomId);
                oldRoom.dispose();
                joinRoom(username, userId, targetRoomId, mode, maxPlayers);
            }
        });
    }

    public void onMatchStartPush(int countdown) {
        matching = true;
        matchCountdown = countdown;
        actionBtn.setText("取消匹配");
        actionBtn.setBackground(new Color(180, 120, 30));
        statusLabel.setText("匹配中... " + countdown + "秒");
        startMatchTimer();
    }

    public void onBotsJoinedPush() { onBotsJoined(); }

    public void refreshRoomState() {
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelInfo(roomId);
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    applyRoomState(parseRoomState(resp));
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
                    TetrisMatchRoom room = new TetrisMatchRoom(username, userId, mode, maxPlayers,
                            false, roomId, globalTetrisHome);
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
}
