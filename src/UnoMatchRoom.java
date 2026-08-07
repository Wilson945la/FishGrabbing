import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UNO 匹配房。
 * - 离线（未登录）：选人数 + 规则，本地机器人立即开局
 * - 在线（已登录）：DUEL_CREATE 建房（gameType=UNO），好友可加入，
 *   人不够时房主点"添加机器人"补齐，全员准备后服务端推 DUEL_GAME_START，
 *   随后整局由服务端权威推进（UNO_STATE）。
 */
public class UnoMatchRoom extends JFrame {
    static final Color BG = new Color(0x1b, 0x1b, 0x29);
    static final Color PANEL_BG = new Color(0x2a, 0x2a, 0x3a);
    static final Color FG = new Color(0xf5, 0xf5, 0xf5);
    static final Color YELLOW = new Color(0xff, 0xc1, 0x07);
    static final Color BTN_BASE = new Color(0x40, 0x40, 0x55);
    static final Color BTN_HOVER = new Color(0x00, 0x78, 0xd7);
    static final Color SLOT_EMPTY = new Color(0x33, 0x33, 0x44);
    static final Color SLOT_BOT = new Color(0x60, 0x40, 0x80);
    static final Color SLOT_HUMAN = new Color(0x40, 0x60, 0x80);
    static final Color C_RED = new Color(0xe5, 0x39, 0x35);
    static final Color C_GREEN = new Color(0x43, 0xa0, 0x47);

    private final String currentUser;
    private final FishGrabbingHome parent;
    /** 是否为"接受好友邀请"进房（此时退出应回到摸鱼中心并关闭残留的信息中心） */
    private boolean enteredViaInvite = false;
    private final UnoHome home;
    private int maxPlayers = 4; // 4-8
    private int mode = 0; // 0=普通叠加 1=逆转叠加
    private final List<String> bots = new ArrayList<>();   // 仅离线使用
    private int roomId = 0; // 0=离线
    private boolean isHost = false;
    /** 在线：服务端房间成员（保持服务端顺序），name -> ready */
    private final LinkedHashMap<String, Boolean> onlinePlayers = new LinkedHashMap<>();
    private boolean isReady = false;

    private javax.swing.Timer pollTimer;
    private JLabel waitLabel;
    private JButton startBtn;
    private JButton addBotBtn;
    private JButton inviteBtn;
    private JSpinner pcSpinner;
    private JRadioButton normalRadio, reverseRadio;
    private JPanel slotsPanel;
    private boolean gameStarted = false;
    private boolean gameStarting = false;
    private boolean offlineMode = false;
    /** 抑制程序化改动 spinner/radio 时回调服务端 */
    private boolean suppressUiEvents = false;

    private static final ConcurrentHashMap<Integer, UnoMatchRoom> activeRooms = new ConcurrentHashMap<>();
    private static FishGrabbingHome globalHome = null;
    private static UnoHome globalUnoHome = null;

    public UnoMatchRoom(String currentUser, FishGrabbingHome parent, UnoHome home) {
        this(currentUser, parent, home, true);
    }

    /** autoCreate=false 用于"加入好友房"：不再自己建房，等 attachExistingRoom 接管 */
    private UnoMatchRoom(String currentUser, FishGrabbingHome parent, UnoHome home, boolean autoCreate) {
        this.currentUser = currentUser == null || currentUser.isEmpty() ? "玩家" : currentUser;
        this.parent = parent;
        this.home = home;
        if (parent != null) globalHome = parent;
        if (home != null) globalUnoHome = home;

        setTitle("UNO · 匹配房");
        setSize(720, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                leaveRoomIfOnline();
                if (!gameStarted) {
                    if (parent != null) {
                        parent.setVisible(true);
                        if (enteredViaInvite) MessageCenter.closeActiveInstance();
                    }
                }
            }
        });

        // 根
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        setContentPane(root);

        // 标题
        JLabel title = new JLabel("UNO 匹配房", SwingConstants.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
        title.setForeground(YELLOW);
        root.add(title, BorderLayout.NORTH);

        // 中央：玩家槽位 + 设置
        JPanel center = new JPanel();
        center.setBackground(BG);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        root.add(center, BorderLayout.CENTER);

        slotsPanel = new JPanel(new GridLayout(2, 4, 10, 10));
        slotsPanel.setBackground(BG);
        slotsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x55, 0x55, 0x66)),
                "  玩家 ( 1 / " + maxPlayers + " )  ", 0, 0,
                new Font("Microsoft YaHei", Font.PLAIN, 13), FG));
        slotsPanel.setPreferredSize(new Dimension(680, 200));
        for (int i = 0; i < 8; i++) slotsPanel.add(makeSlot(i));
        center.add(slotsPanel);

        center.add(Box.createVerticalStrut(10));

        // 设置：人数 + 模式
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        settings.setBackground(BG);
        JLabel pcLabel = new JLabel("人数：");
        pcLabel.setForeground(FG);
        pcLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        settings.add(pcLabel);
        pcSpinner = new JSpinner(new SpinnerNumberModel(4, 4, 8, 1));
        pcSpinner.setPreferredSize(new Dimension(60, 28));
        pcSpinner.addChangeListener(e -> {
            if (suppressUiEvents) return;
            int newMax = (Integer) pcSpinner.getValue();
            if (roomId > 0) {
                if (!isHost) { syncSpinnerSilently(maxPlayers); return; }
                pushMaxPlayers(newMax);
            } else {
                maxPlayers = newMax;
                while (bots.size() + 1 > maxPlayers && !bots.isEmpty()) bots.remove(bots.size() - 1);
                while (bots.size() + 1 < maxPlayers) bots.add("机器人" + (bots.size() + 1));
                updateSlots();
            }
        });
        settings.add(pcSpinner);

        JLabel modeLabel = new JLabel("规则：");
        modeLabel.setForeground(FG);
        modeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        settings.add(modeLabel);
        normalRadio = new JRadioButton("普通叠加");
        normalRadio.setBackground(BG);
        normalRadio.setForeground(FG);
        normalRadio.setSelected(true);
        reverseRadio = new JRadioButton("逆转叠加");
        reverseRadio.setBackground(BG);
        reverseRadio.setForeground(FG);
        ButtonGroup bg = new ButtonGroup();
        bg.add(normalRadio); bg.add(reverseRadio);
        normalRadio.addActionListener(e -> onModePicked(0));
        reverseRadio.addActionListener(e -> onModePicked(1));
        settings.add(normalRadio);
        settings.add(reverseRadio);
        center.add(settings);

        center.add(Box.createVerticalStrut(10));

        waitLabel = new JLabel(" ", SwingConstants.CENTER);
        waitLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        waitLabel.setForeground(new Color(0xcc, 0xcc, 0xcc));
        center.add(waitLabel);

        // 底部按钮
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        bottom.setBackground(BG);
        addBotBtn = makeBtn("添加机器人", 14, 130, 36);
        addBotBtn.addActionListener(e -> onAddBot());
        inviteBtn = makeBtn("邀请好友", 14, 130, 36);
        inviteBtn.addActionListener(e -> inviteFriend());
        JButton backBtn = makeBtn("返回", 14, 100, 36);
        backBtn.addActionListener(e -> {
            leaveRoomIfOnline();
            if (parent != null) parent.setVisible(true);
            dispose();
        });
        startBtn = makeBtn("开始游戏", 16, 150, 40);
        startBtn.setBackground(YELLOW);
        startBtn.setForeground(Color.BLACK);
        startBtn.addActionListener(e -> onStartOrReady());
        bottom.add(addBotBtn);
        bottom.add(inviteBtn);
        bottom.add(backBtn);
        bottom.add(startBtn);
        root.add(bottom, BorderLayout.SOUTH);

        updateSlots();

        // 在线分支：尝试建房
        boolean onlineLoggedIn = ServerClient.getCurrentUser() != null;
        if (!autoCreate) {
            // 加入好友房：房间信息由 attachExistingRoom 填入
            startBtn.setEnabled(false);
            waitLabel.setText("正在加入房间…");
        } else if (onlineLoggedIn) {
            startBtn.setEnabled(false);
            waitLabel.setText("正在创建房间…");
            createOnlineRoom();
        } else {
            offlineMode = true;
            inviteBtn.setEnabled(false);
            inviteBtn.setToolTipText("离线模式下不可邀请好友，请先登录");
            while (bots.size() + 1 < maxPlayers) bots.add("机器人" + (bots.size() + 1));
            updateSlots();
            waitLabel.setText("离线模式：当前 " + (bots.size() + 1) + " 人（可调人数增加机器人到 4-8 人）");
        }
    }

    // ============================================================
    //                          槽位渲染
    // ============================================================

    private JPanel makeSlot(int idx) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(SLOT_EMPTY);
        p.setBorder(BorderFactory.createLineBorder(new Color(0x55, 0x55, 0x66)));
        JLabel lbl = new JLabel("空位", SwingConstants.CENTER);
        lbl.setForeground(new Color(0x88, 0x88, 0x99));
        lbl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        p.add(lbl, BorderLayout.CENTER);
        JLabel sub = new JLabel(" ", SwingConstants.CENTER);
        sub.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        sub.setForeground(new Color(0xaa, 0xaa, 0xbb));
        p.add(sub, BorderLayout.SOUTH);
        p.putClientProperty("label", lbl);
        p.putClientProperty("sub", sub);
        return p;
    }

    /** 当前应展示的成员列表（在线取服务端顺序，离线为 我+bots） */
    private List<String> displayNames() {
        List<String> list = new ArrayList<>();
        if (roomId > 0) list.addAll(onlinePlayers.keySet());
        else { list.add(currentUser); list.addAll(bots); }
        return list;
    }

    private void updateSlots() {
        List<String> names = displayNames();
        Component[] comps = slotsPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            JPanel slot = (JPanel) comps[i];
            JLabel lbl = (JLabel) slot.getClientProperty("label");
            JLabel sub = (JLabel) slot.getClientProperty("sub");
            if (i >= maxPlayers) { slot.setVisible(false); continue; }
            slot.setVisible(true);
            if (i < names.size()) {
                String n = names.get(i);
                boolean bot = n.startsWith("机器人");
                slot.setBackground(bot ? SLOT_BOT : SLOT_HUMAN);
                lbl.setText(n.equals(currentUser) ? n + "（我）" : n);
                lbl.setForeground(Color.WHITE);
                if (roomId > 0) {
                    boolean r = Boolean.TRUE.equals(onlinePlayers.get(n));
                    sub.setText(r ? "已准备" : "未准备");
                    sub.setForeground(r ? C_GREEN : new Color(0xdd, 0xaa, 0x55));
                } else {
                    sub.setText(bot ? "机器人" : "房主");
                    sub.setForeground(new Color(0xaa, 0xaa, 0xbb));
                }
            } else {
                slot.setBackground(SLOT_EMPTY);
                lbl.setText("空位");
                lbl.setForeground(new Color(0x88, 0x88, 0x99));
                sub.setText(" ");
            }
        }
        javax.swing.border.Border b = slotsPanel.getBorder();
        if (b instanceof javax.swing.border.TitledBorder) {
            ((javax.swing.border.TitledBorder) b)
                    .setTitle("  玩家 ( " + names.size() + " / " + maxPlayers + " )  ");
        }
        slotsPanel.revalidate();
        slotsPanel.repaint();
    }

    private JButton makeBtn(String text, int fontSize, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setBackground(BTN_BASE);
        b.setForeground(FG);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        return b;
    }

    // ============================================================
    //                        设置项变更
    // ============================================================

    private void syncSpinnerSilently(int v) {
        suppressUiEvents = true;
        pcSpinner.setValue(v);
        suppressUiEvents = false;
    }

    private void onModePicked(int m) {
        if (suppressUiEvents) return;
        if (roomId > 0) {
            if (!isHost) { syncModeSilently(mode); waitLabel.setText("只有房主可以修改玩法规则"); return; }
            final int want = m;
            runAsync(() -> {
                String resp = ServerClient.duelUpdateMode(roomId, currentUser, String.valueOf(want));
                SwingUtilities.invokeLater(() -> {
                    if (resp != null && resp.startsWith("SUCCESS")) mode = want;
                    else { syncModeSilently(mode); waitLabel.setText(errText(resp, "修改玩法失败")); }
                });
            });
        } else {
            mode = m;
        }
    }

    private void syncModeSilently(int m) {
        suppressUiEvents = true;
        if (m == 1) reverseRadio.setSelected(true); else normalRadio.setSelected(true);
        suppressUiEvents = false;
    }

    private void pushMaxPlayers(int newMax) {
        runAsync(() -> {
            String resp = ServerClient.duelUpdateMax(roomId, currentUser, newMax);
            SwingUtilities.invokeLater(() -> {
                if (resp != null && resp.startsWith("SUCCESS")) {
                    applyRoomState(resp.substring("SUCCESS|".length()));
                } else {
                    syncSpinnerSilently(maxPlayers);
                    waitLabel.setText(errText(resp, "修改人数失败"));
                }
            });
        });
    }

    private void onAddBot() {
        if (roomId > 0) {
            if (!isHost) { waitLabel.setText("只有房主可以添加机器人"); return; }
            if (onlinePlayers.size() >= maxPlayers) {
                waitLabel.setText("房间已满（" + onlinePlayers.size() + "/" + maxPlayers + "），可先把人数调大");
                return;
            }
            runAsync(() -> {
                String resp = ServerClient.duelAddBot(roomId, currentUser);
                SwingUtilities.invokeLater(() -> {
                    if (resp == null || !resp.startsWith("SUCCESS")) waitLabel.setText(errText(resp, "添加机器人失败"));
                    else refreshRoomState();
                });
            });
            return;
        }
        // 离线
        if (bots.size() + 1 >= maxPlayers) {
            JOptionPane.showMessageDialog(this,
                    "已达人数上限 " + maxPlayers + "\n可先把人数调到 5~8 再加机器人。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        bots.add("机器人" + (bots.size() + 1));
        updateSlots();
        waitLabel.setText("离线模式：当前 " + (bots.size() + 1) + " 人");
    }

    private void inviteFriend() {
        if (offlineMode || roomId == 0) {
            JOptionPane.showMessageDialog(this, "离线模式下不可邀请好友，请先登录再开在线房。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // 好友列表弹窗：列出好友，点名字直接邀请（与其余对决玩法一致）
        JDialog dialog = new JDialog(this, "邀请好友", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(280, 350);
        dialog.setLocationRelativeTo(this);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        list.setBackground(PANEL_BG);
        list.setForeground(FG);
        list.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        list.setSelectionBackground(BTN_HOVER);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        dialog.add(sp, BorderLayout.CENTER);

        JButton sendBtn = new JButton("邀请");
        sendBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setBackground(BTN_HOVER);
        sendBtn.setFocusPainted(false);
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(e -> {
            String friend = list.getSelectedValue();
            if (friend != null && !"暂无好友".equals(friend) && !"加载中…".equals(friend)) {
                dialog.dispose();
                sendDuelInvite(friend);
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setBackground(PANEL_BG);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        btnPanel.add(sendBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        list.addListSelectionListener(e ->
                sendBtn.setEnabled(list.getSelectedValue() != null
                        && !"暂无好友".equals(list.getSelectedValue())
                        && !"加载中…".equals(list.getSelectedValue())));

        listModel.addElement("加载中…");
        Thread loader = new Thread(() -> {
            String result = ServerClient.getFriends(currentUser);
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                if (result != null && result.startsWith("SUCCESS")) {
                    String data = result.substring("SUCCESS|".length());
                    if (!data.isEmpty()) {
                        for (String entry : data.split(";")) {
                            String[] parts = entry.split(",");
                            if (parts.length >= 1 && !"moyu官方".equals(parts[0]) && !parts[0].equals(currentUser)) {
                                listModel.addElement(parts[0]);
                            }
                        }
                    }
                }
                if (listModel.isEmpty()) listModel.addElement("暂无好友");
            });
        });
        loader.setDaemon(true);
        loader.start();
        dialog.setVisible(true);
    }

    private void sendDuelInvite(String friendName) {
        String inviteMsg = "DUEL_INVITE:" + roomId + ":" + mode + ":" + maxPlayers + ":UNO:" + System.currentTimeMillis();
        Thread t = new Thread(() -> {
            String result = ServerClient.sendChatMessage(currentUser, friendName, inviteMsg);
            SwingUtilities.invokeLater(() -> {
                if (result != null && result.startsWith("SUCCESS")) {
                    JOptionPane.showMessageDialog(this, "已向 " + friendName + " 发送 UNO 对决邀请！", "邀请已发送", JOptionPane.INFORMATION_MESSAGE);
                    // 打开与该好友的聊天，便于查看状态
                    try {
                        String idResp = ServerClient.getUserId(currentUser);
                        int myId = 0;
                        if (idResp != null && idResp.contains("|")) myId = Integer.parseInt(idResp.split("\\|", 2)[1].trim());
                        MessageCenter.openAndNavigate(currentUser, myId, friendName, parent);
                    } catch (Exception ignored) {}
                } else {
                    String msg = (result != null && result.contains("|")) ? result.split("\\|", 2)[1] : "发送失败";
                    JOptionPane.showMessageDialog(this, "邀请 " + friendName + " 失败：" + msg, "邀请失败", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    // ============================================================
    //                       在线建房 / 状态同步
    // ============================================================

    private void createOnlineRoom() {
        runAsync(() -> {
            String resp = ServerClient.duelCreate(currentUser, String.valueOf(mode), maxPlayers, "UNO");
            SwingUtilities.invokeLater(() -> {
                if (resp == null || !resp.startsWith("SUCCESS|")) {
                    // 建房失败 → 退回离线，保证仍能玩
                    fallbackOffline(errText(resp, "建房失败"));
                    return;
                }
                String body = resp.substring("SUCCESS|".length());
                int rid = parseRoomId(body);
                if (rid <= 0) { fallbackOffline("房间号解析失败"); return; }
                roomId = rid;
                isHost = true;
                activeRooms.put(roomId, UnoMatchRoom.this);
                setTitle("UNO · 在线房 " + roomId);
                applyRoomState(body);
                startBtn.setEnabled(true);
                startBtn.setText("准备");
                waitLabel.setText("房间 " + roomId + " 已创建，等待好友加入；人不够可点\"添加机器人\"补齐");
                startPoll();
            });
        });
    }

    /** 从好友邀请加入已有 UNO 房间（与 2048/飞行棋 等同签名） */
    public static void joinRoom(String username, int userId, int roomId, String mode, int maxPlayers) {
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelJoin(roomId, username);
            SwingUtilities.invokeLater(() -> {
                if (resp == null || !resp.startsWith("SUCCESS|")) {
                    JOptionPane.showMessageDialog(null,
                            resp != null && resp.contains("|") ? resp.split("\\|", 2)[1] : "加入失败",
                            "加入失败", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // 好友通过邀请进房：parent 用当前摸鱼中心实例兜底（globalHome 可能为 null）
                FishGrabbingHome home = (globalHome != null) ? globalHome : FishGrabbingHome.getActiveInstance();
                UnoMatchRoom room = new UnoMatchRoom(username, home, globalUnoHome, false);
                room.enteredViaInvite = true;
                room.attachExistingRoom(roomId, resp.substring("SUCCESS|".length()));
                room.setVisible(true);
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 已经在服务端房间里了（加入方），跳过建房直接接管 */
    private void attachExistingRoom(int rid, String stateBody) {
        // 建房线程可能已经在跑（构造里发起的 createOnlineRoom），这里覆盖为目标房
        this.roomId = rid;
        this.isHost = false;
        activeRooms.put(rid, this);
        setTitle("UNO · 在线房 " + rid);
        applyRoomState(stateBody);
        startBtn.setEnabled(true);
        startBtn.setText("准备");
        waitLabel.setText("已加入房间 " + rid + "，等待房主开局");
        startPoll();
    }

    private void fallbackOffline(String why) {
        offlineMode = true;
        roomId = 0;
        isHost = false;
        inviteBtn.setEnabled(false);
        startBtn.setEnabled(true);
        startBtn.setText("开始游戏");
        while (bots.size() + 1 < maxPlayers) bots.add("机器人" + (bots.size() + 1));
        updateSlots();
        waitLabel.setText(why + "，已切换为本地机器人对战");
    }

    private int parseRoomId(String stateBody) {
        String[] p = stateBody.split("\\|");
        if (p.length < 1) return 0;
        try { return Integer.parseInt(p[0]); } catch (NumberFormatException e) { return 0; }
    }

    /** stateBody: roomId|mode|maxPlayers|gameType|name,ready|name,ready... */
    private void applyRoomState(String stateBody) {
        if (stateBody == null || stateBody.isEmpty()) return;
        String[] p = stateBody.split("\\|");
        if (p.length < 4) return;
        try {
            int rid = Integer.parseInt(p[0]);
            if (roomId > 0 && rid != roomId) return; // 不是本房间的状态
        } catch (NumberFormatException e) { return; }

        try { mode = Integer.parseInt(p[1]); } catch (NumberFormatException ignored) {}
        try { maxPlayers = Integer.parseInt(p[2]); } catch (NumberFormatException ignored) {}
        syncSpinnerSilently(Math.max(4, Math.min(8, maxPlayers)));
        syncModeSilently(mode);

        onlinePlayers.clear();
        for (int i = 4; i < p.length; i++) {
            int c = p[i].lastIndexOf(',');
            if (c <= 0) continue;
            onlinePlayers.put(p[i].substring(0, c), "1".equals(p[i].substring(c + 1)));
        }
        Boolean mine = onlinePlayers.get(currentUser);
        isReady = Boolean.TRUE.equals(mine);
        if (!onlinePlayers.isEmpty()) {
            isHost = currentUser.equals(onlinePlayers.keySet().iterator().next());
        }

        updateSlots();
        if (!gameStarting) {
            startBtn.setText(isReady ? "取消准备" : "准备");
            startBtn.setBackground(isReady ? new Color(0xb0, 0x3a, 0x3a) : YELLOW);
            startBtn.setForeground(isReady ? Color.WHITE : Color.BLACK);
            addBotBtn.setEnabled(isHost && onlinePlayers.size() < maxPlayers);
            int need = maxPlayers - onlinePlayers.size();
            if (need > 0) {
                waitLabel.setText("房间 " + roomId + "：还差 " + need + " 人（"
                        + onlinePlayers.size() + "/" + maxPlayers + "）"
                        + (isHost ? " · 可点\"添加机器人\"补齐" : " · 等待房主补人"));
            } else {
                int readyCnt = 0;
                for (Boolean b : onlinePlayers.values()) if (Boolean.TRUE.equals(b)) readyCnt++;
                waitLabel.setText("房间 " + roomId + " 已满，已准备 " + readyCnt + "/" + onlinePlayers.size());
            }
        }
    }

    public void refreshRoomState() {
        if (roomId <= 0) return;
        runAsync(() -> {
            String resp = ServerClient.duelInfo(roomId);
            SwingUtilities.invokeLater(() -> {
                if (resp != null && resp.startsWith("SUCCESS|")) applyRoomState(resp.substring("SUCCESS|".length()));
            });
        });
    }

    private void startPoll() {
        if (pollTimer != null) pollTimer.stop();
        pollTimer = new javax.swing.Timer(2000, e -> refreshRoomState());
        pollTimer.start();
    }

    private void stopPoll() {
        if (pollTimer != null) { pollTimer.stop(); pollTimer = null; }
    }

    private void leaveRoomIfOnline() {
        stopPoll();
        if (roomId > 0 && !gameStarted) {
            final int rid = roomId;
            activeRooms.remove(rid, this);
            runAsync(() -> ServerClient.duelLeave(rid, currentUser));
        }
    }

    // ============================================================
    //                      准备 / 开始游戏
    // ============================================================

    private void onStartOrReady() {
        if (gameStarted || gameStarting) return;
        if (roomId <= 0) { startOfflineGame(); return; }

        if (onlinePlayers.size() < maxPlayers && !isReady) {
            waitLabel.setText("人数不足（" + onlinePlayers.size() + "/" + maxPlayers + "），"
                    + (isHost ? "先点\"添加机器人\"补齐再准备" : "等待房主补人"));
            return;
        }
        startBtn.setEnabled(false);
        runAsync(() -> {
            String resp = ServerClient.duelReady(roomId, currentUser);
            SwingUtilities.invokeLater(() -> {
                startBtn.setEnabled(true);
                if (resp != null && resp.startsWith("SUCCESS|")) {
                    String body = resp.substring("SUCCESS|".length());
                    if (body.endsWith("|ALL_READY")) body = body.substring(0, body.length() - "|ALL_READY".length());
                    applyRoomState(body);
                    // ALL_READY 时服务端会推 DUEL_GAME_START，这里只提示
                    if (resp.contains("ALL_READY")) waitLabel.setText("全员准备完毕，开局中…");
                } else {
                    waitLabel.setText(errText(resp, "准备失败"));
                }
            });
        });
    }

    private void startOfflineGame() {
        if (gameStarted) return;
        gameStarted = true;
        stopPoll();
        List<String> all = new ArrayList<>();
        all.add(currentUser);
        all.addAll(bots);
        while (all.size() < 4) all.add("机器人" + all.size());
        long seed = System.currentTimeMillis();
        UnoGame game = new UnoGame(currentUser, 0, mode, all, 0, seed, () -> {
            // 一局结束：离线同样回到匹配房继续开下一局，而非直接回摸鱼中心主页
            SwingUtilities.invokeLater(() -> {
                gameStarted = false;
                startBtn.setEnabled(true);
                startBtn.setText("开始游戏");
                startBtn.setBackground(YELLOW);
                startBtn.setForeground(Color.BLACK);
                waitLabel.setText("本局结束，可再次开始游戏");
                setVisible(true);
            });
        });
        setVisible(false);
        game.setVisible(true);
    }

    /** MessageCenter 路由：收到 DUEL_GAME_START */
    public static void receiveGameStart(int roomId, long seed, String modeStr) {
        UnoMatchRoom room = activeRooms.get(roomId);
        if (room == null || room.gameStarting) return;
        room.gameStarting = true;
        SwingUtilities.invokeLater(() -> room.startOnlineGame(seed, modeStr));
    }

    /** MessageCenter 路由：收到 DUEL_STATE */
    public static void receiveStatePush(int roomId, String stateData) {
        UnoMatchRoom room = activeRooms.get(roomId);
        if (room == null) return;
        SwingUtilities.invokeLater(() -> room.applyRoomState(stateData));
    }

    /** MessageCenter 路由：收到 DUEL_BOTS_JOINED */
    public static void receiveBotsJoined(int roomId) {
        UnoMatchRoom room = activeRooms.get(roomId);
        if (room == null) return;
        SwingUtilities.invokeLater(room::refreshRoomState);
    }

    public static UnoMatchRoom getActiveRoom(int roomId) { return activeRooms.get(roomId); }

    private void startOnlineGame(long seed, String modeStr) {
        if (gameStarted) return;
        gameStarted = true;
        stopPoll();
        int m = mode;
        try { m = Integer.parseInt(modeStr.trim()); } catch (Exception ignored) {}

        List<String> all = new ArrayList<>(onlinePlayers.keySet());
        if (all.isEmpty()) all.add(currentUser);
        int myIdx = Math.max(0, all.indexOf(currentUser));

        final int rid = roomId;
        activeRooms.remove(rid, this);
        setVisible(false);
        UnoGame game = new UnoGame(currentUser, myIdx, m, all, rid, seed, () -> {
            // 一局结束：回到匹配房继续开下一局
            SwingUtilities.invokeLater(() -> {
                gameStarted = false;
                gameStarting = false;
                isReady = false;
                activeRooms.put(rid, UnoMatchRoom.this);
                startBtn.setEnabled(true);
                startBtn.setText("准备");
                startBtn.setBackground(YELLOW);
                startBtn.setForeground(Color.BLACK);
                waitLabel.setText("本局结束，可再次准备开新局");
                setVisible(true);
                refreshRoomState();
                startPoll();
            });
        });
        game.setVisible(true);
    }

    // ============================================================
    //                          小工具
    // ============================================================

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
    }

    private static String errText(String resp, String fallback) {
        if (resp == null || resp.isEmpty()) return fallback + "（无响应）";
        int i = resp.indexOf('|');
        return i >= 0 ? resp.substring(i + 1) : fallback;
    }
}
