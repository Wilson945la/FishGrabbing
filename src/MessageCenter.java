import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class MessageCenter extends JFrame {

    private static final Color BG_DARK = new Color(50, 53, 56);
    private static final Color BG_SIDEBAR = new Color(44, 47, 50);
    private static final Color BG_CHAT = new Color(50, 53, 56);
    private static final Color BG_INPUT = new Color(60, 63, 65);
    private static final Color BG_BUBBLE_ME = new Color(0, 120, 215);
    private static final Color BG_BUBBLE_OTHER = new Color(70, 73, 76);
    private static final Color TEXT_PRIMARY = Color.WHITE;
    private static final Color TEXT_SECONDARY = new Color(180, 183, 186);
    private static final Color SELECTED_BG = new Color(60, 130, 225);
    private static final Color ONLINE_COLOR = new Color(100, 255, 100);
    private static final Color OFFLINE_COLOR = new Color(150, 153, 156);
    private static final Color BTN_BG = new Color(55, 58, 61);
    private static final Color BTN_HOVER = new Color(65, 68, 71);

    // 内网直连模式（与 ServerClient 的 HOST/PORT 保持一致）
    private static final String PUSH_HOST = "127.0.0.1";
    private static final int PUSH_PORT = 80;

    // natapp 穿透模式（需外网访问时取消注释）
    // private static final String PUSH_HOST = "j56a69f9.natappfree.cc";
    // private static final int PUSH_PORT = 45910;
    // 生产服（已切换为本地联调，需要时再切回）
    // private static final String PUSH_HOST = "172.16.162.87";
    // private static final int PUSH_PORT = 80;

    private String username;
    private JFrame homeFrame;
    private int userId;

    private JButton notifyBtn;
    private JList<FriendItem> friendList;
    private DefaultListModel<FriendItem> friendListModel;

    private JLabel chatTitle;
    private JPanel chatMessagesPanel;
    private JScrollPane chatScroll;
    private JTextArea inputArea;
    private JButton sendBtn;
    private JPanel inputPanel;

    private String currentChatFriend = null;
    private volatile String loadingFor = null;  // 记录正在加载的目标，null=空闲，非null=某目标

    // 推送长连接（全局静态：与窗口生命周期解耦，一直运行）
    private static Socket globalPushSocket = null;
    private static volatile boolean globalPushRunning = false;
    private static Thread globalPushThread = null;
    private static String globalUsername = null;
    private static int globalUserId = 0;

    // 已处理的对决邀请集合
    private static final Set<String> handledDuelInvites = Collections.synchronizedSet(new HashSet<>());

    // 对决临时聊天大厅
    private int duelRoomId = 0;
    private java.util.List<String> duelPlayers = new java.util.ArrayList<>();
    private static final String DUEL_LOBBY_ID = "__DUEL_LOBBY__";

    // 当前活跃的MessageCenter实例（用于去重）
    private static MessageCenter activeInstance = null;

    /** 打开消息中心并导航到指定好友的聊天 */
    public static void openAndNavigate(String username, int userId, String friendName, JFrame parentWindow) {
        // 关闭已有实例
        if (activeInstance != null) {
            activeInstance.dispose();
            activeInstance = null;
        }
        activeInstance = new MessageCenter(username, null, userId);
        activeInstance.setVisible(true);
        activeInstance.navigateToChat(friendName);

        // 监听父窗口关闭：父窗口关闭时自动关闭消息中心
        if (parentWindow != null) {
            parentWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (activeInstance != null) {
                        activeInstance.dispose();
                        activeInstance = null;
                    }
                }
            });
        }
    }

    /** 关闭当前活跃的消息中心实例 */
    public static void disposeActive() {
        if (activeInstance != null) {
            activeInstance.dispose();
            activeInstance = null;
        }
    }

    /** 打开对决聊天大厅（匹配成功后调用） */
    public static void openDuelLobby(int roomId, String myUsername, java.util.List<String> allPlayers) {
        // 如果已有消息中心开着，重开（先关再开）
        if (activeInstance != null) {
            activeInstance.dispose();
            activeInstance = null;
        }
        MessageCenter mc = new MessageCenter(myUsername, null, globalUserId);
        mc.duelRoomId = roomId;
        mc.duelPlayers = new java.util.ArrayList<>(allPlayers);
        activeInstance = mc;
        mc.setVisible(true);
        mc.showDuelLobby();
    }

    /** 启动全局推送长连接（登录后调用一次，不受消息中心窗口开关影响） */
    public static void startGlobalPush(String username, int userId) {
        globalUsername = username;
        globalUserId = userId;
        if (!globalPushRunning) {
            globalPushRunning = true;
            globalPushThread = new Thread(MessageCenter::globalPushLoop, "GlobalPush");
            globalPushThread.setDaemon(true);
            globalPushThread.start();
        }
    }

    /** 关闭当前消息中心窗口（如：好友受邀进房后退出，回到摸鱼中心时清掉残留的信息中心） */
    public static void closeActiveInstance() {
        if (activeInstance != null) {
            activeInstance.dispose();
            activeInstance = null;
        }
    }

    /** 停止全局推送（程序退出时调用） */
    public static void stopGlobalPush() {
        globalPushRunning = false;
        synchronized (MessageCenter.class) {
            try { if (globalPushSocket != null) globalPushSocket.close(); } catch (Exception ignored) {}
        }
        if (globalPushThread != null && globalPushThread.isAlive()) {
            try { globalPushThread.interrupt(); } catch (Exception ignored) {}
        }
    }

    /** 导航到指定好友的聊天 */
    public void navigateToChat(String friendName) {
        // 在好友列表中选中该好友
        for (int i = 0; i < friendListModel.size(); i++) {
            FriendItem item = friendListModel.getElementAt(i);
            if (item.getName().equals(friendName)) {
                friendList.setSelectedIndex(i);
                friendList.ensureIndexIsVisible(i);
                openChat(friendName);
                return;
            }
        }
    }

    /** 显示对决聊天大厅 */
    private void showDuelLobby() {
        currentChatFriend = DUEL_LOBBY_ID;
        chatTitle.setText("对决聊天 · 房间" + duelRoomId);
        chatTitle.setIcon(null);
        notifyBtn.setBackground(BTN_BG);
        inputArea.setEnabled(true);
        inputArea.setBackground(BG_INPUT);
        sendBtn.setEnabled(true);
        sendBtn.setBackground(BG_BUBBLE_ME);

        chatMessagesPanel.removeAll();

        // 玩家列表
        StringBuilder playerList = new StringBuilder("参赛玩家: ");
        for (int i = 0; i < duelPlayers.size(); i++) {
            if (i > 0) playerList.append(", ");
            String p = duelPlayers.get(i);
            playerList.append(p.equals(username) ? p + "(你)" : p);
        }

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_CHAT);
        JLabel playerLbl = new JLabel(playerList.toString());
        playerLbl.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        playerLbl.setForeground(new Color(255, 200, 50));
        header.add(playerLbl, BorderLayout.CENTER);
        header.setMaximumSize(new Dimension(620, 25));
        chatMessagesPanel.add(header);
        chatMessagesPanel.add(Box.createVerticalStrut(12));

        // 系统消息
        chatMessagesPanel.add(createSystemBubble("全部玩家已就绪，游戏即将开始..."));
        chatMessagesPanel.add(Box.createVerticalStrut(8));

        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
    }

    /** 创建系统消息气泡 */
    private JPanel createSystemBubble(String text) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setBackground(BG_CHAT);

        JTextArea bubble = new JTextArea(text);
        bubble.setEditable(false);
        bubble.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        bubble.setForeground(new Color(200, 200, 200));
        bubble.setBackground(new Color(80, 85, 90));
        bubble.setLineWrap(true);
        bubble.setWrapStyleWord(true);
        bubble.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 105, 110), 1),
            BorderFactory.createEmptyBorder(6, 15, 6, 15)
        ));
        bubble.setFocusable(false);
        bubble.setOpaque(true);

        wrapper.add(bubble);
        return wrapper;
    }

    /** 接收对决聊天推送消息 */
    private void onDuelChatReceived(String sender, String msg) {
        if (!DUEL_LOBBY_ID.equals(currentChatFriend)) return;
        boolean isMe = sender.equals(username);
        chatMessagesPanel.add(createBubble(msg, isMe, ""));
        chatMessagesPanel.add(Box.createVerticalStrut(8));
        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
        JScrollBar bar = chatScroll.getVerticalScrollBar();
        SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    public MessageCenter(String username, JFrame homeFrame, int userId) {
        this.username = username;
        this.homeFrame = homeFrame;
        this.userId = userId;

        // 注册为活跃实例
        if (activeInstance != null) {
            activeInstance.dispose();
        }
        activeInstance = this;

        setTitle("消息中心");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(200);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        splitPane.setBackground(new Color(40, 43, 46));
        splitPane.setLeftComponent(createSidebar());
        splitPane.setRightComponent(createChatArea());

        getContentPane().add(splitPane, BorderLayout.CENTER);

        JPanel botPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        botPanel.setBackground(BG_DARK);
        botPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        JButton backBtn = txtBtn("返回主页", TEXT_PRIMARY, new Color(80, 83, 86), 100, 32);
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { backBtn.setBackground(BG_BUBBLE_ME); }
            public void mouseExited(MouseEvent e) { backBtn.setBackground(new Color(80, 83, 86)); }
        });
        backBtn.addActionListener(e -> {
            activeInstance = null;
            dispose();
        });
        botPanel.add(backBtn);
        getContentPane().add(botPanel, BorderLayout.SOUTH);

        setSize(900, 600);
        setLocationRelativeTo(null);

        // 好友列表异步加载，避免阻塞 EDT 导致窗口无法显示
        Thread loadFriendsThread = new Thread(this::loadFriendList);
        loadFriendsThread.setDaemon(true);
        loadFriendsThread.start();

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                activeInstance = null;
                // 不再断开推送，全局推送保持运行
            }
            public void windowClosed(WindowEvent e) {
                // 所有窗口关闭后退出 JVM
                if (JFrame.getFrames().length == 0) {
                    System.exit(0);
                }
            }
        });

        // 兜底：如果全局推送还没启动（如直接 new MessageCenter 而没走登录流程），则启动
        if (!globalPushRunning) {
            startGlobalPush(username, userId);
        }
    }

    /** 路由游戏相关推送（即使消息中心关闭也能处理） */
    private static void routeGamePush(String sender, String msg) {
        if (msg.startsWith("UNO_STATE:")) {
            // UNO_STATE:roomId|colorIdx|curIdx|... （服务端权威牌局状态，含私有手牌）
            String usBody = msg.substring("UNO_STATE:".length());
            int bar = usBody.indexOf('|');
            if (bar > 0) {
                try {
                    int usRoomId = Integer.parseInt(usBody.substring(0, bar));
                    UnoGame.receiveState(usRoomId, usBody);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_GAME_PUSH:")) {
            String gpData = msg.substring("DUEL_GAME_PUSH:".length());
            String[] gpp = gpData.split(":", 2);
            if (gpp.length >= 2) {
                try {
                    int gpRoomId = Integer.parseInt(gpp[0]);
                    String cellData = gpp[1];
                    MineDuelGame.receiveCellPush(gpRoomId, cellData);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_GAME_OVER:")) {
            String goData = msg.substring("DUEL_GAME_OVER:".length());
            String[] gop = goData.split(":", 2);
            if (gop.length >= 2) {
                try {
                    int goRoomId = Integer.parseInt(gop[0]);
                    String results = gop[1];
                    MineDuelGame.receiveGameOver(goRoomId, results);
                    TetrisStackDuelGame.receiveGameOver(goRoomId, results);
                    TetrisDuelGame.receiveGameOver(goRoomId, results);
                    Game2048DuelGame.receiveAllDone(goRoomId, results);
                    UnoGame.receiveGameOver(goRoomId, results);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_BOARD_PUSH:")) {
            String bpData = msg.substring("DUEL_BOARD_PUSH:".length());
            String[] bpp = bpData.split(":", 3);
            if (bpp.length >= 3) {
                try {
                    int bpRoomId = Integer.parseInt(bpp[0]);
                    String bpPushData = bpp[1] + ":" + bpp[2];
                    Game2048DuelGame.receiveBoardPush(bpRoomId, bpPushData);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_STATE:")) {
            String[] dsParts = msg.substring("DUEL_STATE:".length()).split("\\|", 2);
            System.out.println("[MessageCenter] routeGamePush DUEL_STATE: raw=" + msg + ", parts=" + java.util.Arrays.toString(dsParts));
            if (dsParts.length >= 2) {
                try {
                    int dsRoomId = Integer.parseInt(dsParts[0]);
                    String stateData = dsParts[1];
                    SwingUtilities.invokeLater(() -> {
                        MineMatchRoom.receiveStatePush(dsRoomId, stateData);
                        TetrisMatchRoom.receiveStatePush(dsRoomId, stateData);
                        AeroChessMatchRoom.receiveStatePush(dsRoomId, stateData);
                        Game2048MatchRoom.receiveStatePush(dsRoomId, stateData);
                        UnoMatchRoom.receiveStatePush(dsRoomId, dsRoomId + "|" + stateData);
                    });
                } catch (NumberFormatException e) {
                    System.out.println("[MessageCenter] DUEL_STATE roomId 解析失败: " + e.getMessage());
                }
            }
        } else if (msg.startsWith("DUEL_MATCH_MOVE:")) {
            // DUEL_MATCH_MOVE:targetRoomId:mode:maxPlayers
            String[] mmParts = msg.substring("DUEL_MATCH_MOVE:".length()).split(":");
            if (mmParts.length >= 3) {
                try {
                    int targetRoomId = Integer.parseInt(mmParts[0]);
                    String mmMode = mmParts[1];
                    int mmMax = Integer.parseInt(mmParts[2]);
                    MineMatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                    TetrisMatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                    AeroChessMatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                    Game2048MatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_MATCH_START:")) {
            // DUEL_MATCH_START:roomId:countdown
            String[] msParts = msg.substring("DUEL_MATCH_START:".length()).split(":");
            if (msParts.length >= 2) {
                try {
                    int msRoomId = Integer.parseInt(msParts[0]);
                    int msCountdown = Integer.parseInt(msParts[1]);
                    AeroChessMatchRoom aRoom = AeroChessMatchRoom.getActiveRoom(msRoomId);
                    if (aRoom != null) {
                        SwingUtilities.invokeLater(() -> aRoom.onMatchStartPush(msCountdown));
                    }
                    Game2048MatchRoom gRoom = Game2048MatchRoom.getActiveRoom(msRoomId);
                    if (gRoom != null) {
                        SwingUtilities.invokeLater(() -> gRoom.onMatchStartPush(msCountdown));
                    }
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_BOTS_JOINED:")) {
            String[] bjParts = msg.substring("DUEL_BOTS_JOINED:".length()).split(":", 2);
            if (bjParts.length >= 1) {
                try {
                    int bjRoomId = Integer.parseInt(bjParts[0]);
                    MineMatchRoom room = MineMatchRoom.getActiveRoom(bjRoomId);
                    if (room != null) {
                        SwingUtilities.invokeLater(() -> room.onBotsJoinedPush());
                    }
                    TetrisMatchRoom tRoom = TetrisMatchRoom.getActiveRoom(bjRoomId);
                    if (tRoom != null) {
                        SwingUtilities.invokeLater(() -> tRoom.onBotsJoinedPush());
                    }
                    AeroChessMatchRoom aRoom = AeroChessMatchRoom.getActiveRoom(bjRoomId);
                    if (aRoom != null) {
                        SwingUtilities.invokeLater(() -> aRoom.onBotsJoinedPush());
                    }
                    Game2048MatchRoom gRoom = Game2048MatchRoom.getActiveRoom(bjRoomId);
                    if (gRoom != null) {
                        SwingUtilities.invokeLater(() -> gRoom.onBotsJoinedPush());
                    }
                    UnoMatchRoom.receiveBotsJoined(bjRoomId);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_GAME_START:")) {
            // DUEL_GAME_START:roomId:seed:mode
            String gsData = msg.substring("DUEL_GAME_START:".length());
            String[] gsp = gsData.split(":");
            if (gsp.length >= 3) {
                try {
                    int gsRoomId = Integer.parseInt(gsp[0]);
                    long gsSeed = Long.parseLong(gsp[1]);
                    String gsMode = gsp[2];
                    if (gsp.length > 3) {
                        StringBuilder modeBuilder = new StringBuilder(gsp[2]);
                        for (int i = 3; i < gsp.length; i++) {
                            modeBuilder.append(":").append(gsp[i]);
                        }
                        gsMode = modeBuilder.toString();
                    }
                    MineMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    TetrisMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    AeroChessMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    Game2048MatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    UnoMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                } catch (NumberFormatException ignored) {}
            }
        } else if (msg.startsWith("DUEL_CHAT_PUSH:")) {
            // DUEL_CHAT_PUSH:roomId:username:message
            String cpData = msg.substring("DUEL_CHAT_PUSH:".length());
            String[] cpp = cpData.split(":", 3);
            if (cpp.length >= 3) {
                final String cpUser = cpp[1];
                final String cpMsg = cpp[2];
                if (activeInstance != null) {
                    SwingUtilities.invokeLater(() -> activeInstance.onDuelChatReceived(cpUser, cpMsg));
                }
            }
        } else if (msg.startsWith("DUEL_READY_CHANGE:")) {
            // 有人准备/取消准备，立即刷新房间状态
            String[] rcParts = msg.substring("DUEL_READY_CHANGE:".length()).split(":");
            if (rcParts.length >= 2) {
                try {
                    int rcRoomId = Integer.parseInt(rcParts[0]);
                    MineMatchRoom room = MineMatchRoom.getActiveRoom(rcRoomId);
                    if (room != null) {
                        SwingUtilities.invokeLater(() -> room.refreshRoomState());
                    }
                    TetrisMatchRoom tRoom = TetrisMatchRoom.getActiveRoom(rcRoomId);
                    if (tRoom != null) {
                        SwingUtilities.invokeLater(() -> tRoom.refreshRoomState());
                    }
                    AeroChessMatchRoom aRoom = AeroChessMatchRoom.getActiveRoom(rcRoomId);
                    if (aRoom != null) {
                        SwingUtilities.invokeLater(() -> aRoom.refreshRoomState());
                    }
                    Game2048MatchRoom gRoom = Game2048MatchRoom.getActiveRoom(rcRoomId);
                    if (gRoom != null) {
                        SwingUtilities.invokeLater(() -> gRoom.refreshRoomState());
                    }
                    UnoMatchRoom uRoom = UnoMatchRoom.getActiveRoom(rcRoomId);
                    if (uRoom != null) {
                        SwingUtilities.invokeLater(() -> uRoom.refreshRoomState());
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /** 全局推送循环（静态，不受窗口开关影响） */
    private static void globalPushLoop() {
        int reconnectDelay = 1000;
        while (globalPushRunning) {
            try {
                Socket sock = new Socket(PUSH_HOST, PUSH_PORT);
                sock.setSoTimeout(60000);
                synchronized (MessageCenter.class) {
                    globalPushSocket = sock;
                }
                PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

                out.println("PUSH_REGISTER|" + globalUsername);
                String resp = in.readLine();
                if (!"PUSH_OK".equals(resp)) {
                    System.out.println("[推送] 注册失败: " + resp);
                    sock.close();
                    Thread.sleep(3000);
                    continue;
                }

                System.out.println("[推送] 已连接");
                reconnectDelay = 1000; // 成功连接后重置退避

                // 心跳线程：每 30 秒发一次 PING
                Thread hb = new Thread(() -> {
                    try {
                        while (globalPushRunning && !sock.isClosed()) {
                            Thread.sleep(30000);
                            if (globalPushRunning && !sock.isClosed()) {
                                out.println("PING");
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        // 心跳异常，循环会退出
                    }
                }, "PushHeartbeat");
                hb.setDaemon(true);
                hb.start();

                // 接收推送消息
                String line;
                while (globalPushRunning) {
                    line = in.readLine();
                    if (line == null) break;
                    if (line.equals("PONG")) continue; // 心跳回复
                    if (line.startsWith("PUSH|")) {
                        String[] parts = line.split("\\|", 3);
                        if (parts.length == 3) {
                            final String sender = parts[1];
                            final String msg = parts[2];
                            SwingUtilities.invokeLater(() -> {
                                // 游戏/匹配相关推送：即使消息中心没打开也要处理
                        if (msg.startsWith("DUEL_GAME_OVER:") || msg.startsWith("DUEL_GAME_PUSH:")
                                || msg.startsWith("DUEL_STATE:") || msg.startsWith("DUEL_MATCH_MOVE:")
                                || msg.startsWith("DUEL_BOTS_JOINED:") || msg.startsWith("DUEL_GAME_START:")
                                || msg.startsWith("DUEL_CHAT_PUSH:") || msg.startsWith("DUEL_READY_CHANGE:")
                                || msg.startsWith("DUEL_MATCH_START:") || msg.startsWith("DUEL_BOARD_PUSH:")
                                || msg.startsWith("UNO_STATE:")) {
                                    routeGamePush(sender, msg);
                                }
                                // 如果消息中心还没打开但收到了对决邀请，自动创建消息中心
                                if (activeInstance == null && msg.startsWith("DUEL_INVITE:")) {
                                    activeInstance = new MessageCenter(globalUsername, null, globalUserId);
                                    activeInstance.setVisible(true);
                                    activeInstance.navigateToChat(sender);
                                }
                                // 转发到活跃实例
                                if (activeInstance != null) {
                                    activeInstance.onPushReceived(sender, msg);
                                }
                            });
                        }
                    }
                }
                System.out.println("[推送] 连接断开");
                sock.close();
            } catch (Exception e) {
                System.out.println("[推送] 异常: " + e.getMessage());
            }
            // 退避重连：1s → 2s → 4s → 8s → 16s（最大 30s）
            if (globalPushRunning) {
                try { Thread.sleep(reconnectDelay); } catch (InterruptedException e) { break; }
                reconnectDelay = Math.min(reconnectDelay * 2, 30000);
            }
        }
    }

    private void onPushReceived(String sender, String msg) {
        String realSender = sender;
        if ("moyu官方".equals(sender) && msg.startsWith("FRIEND_REQUEST:")) {
            realSender = msg.substring("FRIEND_REQUEST:".length());
        }

        // 处理系统推送的房间通知
        if ("系统".equals(sender)) {
            if (msg.startsWith("UNO_STATE:")) {
                // 已在 routeGamePush 中分发给 UnoGame，这里只需拦住不要当聊天显示
                return;
            }
            if (msg.startsWith("DUEL_JOINED:") || msg.startsWith("DUEL_LEFT:") || msg.startsWith("DUEL_READY_CHANGE:")
                    || msg.startsWith("DUEL_MATCH_START:")) {
                // 对战房间状态变更/匹配开始通知，忽略聊天显示
                return;
            }
            if (msg.startsWith("DUEL_STATE:")) {
                // 房间状态推送给 MineMatchRoom / TetrisMatchRoom（即时刷新，不等轮询）
                String[] dsParts = msg.substring("DUEL_STATE:".length()).split("\\|", 2);
                if (dsParts.length >= 2) {
                    try {
                        int rid = Integer.parseInt(dsParts[0]);
                        MineMatchRoom.receiveStatePush(rid, dsParts[1]);
                        TetrisMatchRoom.receiveStatePush(rid, dsParts[1]);
                        AeroChessMatchRoom.receiveStatePush(rid, dsParts[1]);
                        Game2048MatchRoom.receiveStatePush(rid, dsParts[1]);
                        UnoMatchRoom.receiveStatePush(rid, rid + "|" + dsParts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (msg.startsWith("DUEL_GAME_START:")) {
                // 游戏开始推送
                String gsData = msg.substring("DUEL_GAME_START:".length());
                String[] gsp = gsData.split(":");
                if (gsp.length >= 3) {
                    try {
                        int gsRoomId = Integer.parseInt(gsp[0]);
                        long gsSeed = Long.parseLong(gsp[1]);
                        String gsMode = gsp[2];
                        // 对于格式如 "自定义-16x16(40雷)" 的模式串
                        if (gsp.length > 3) {
                            // 拼接回完整模式名
                            StringBuilder modeBuilder = new StringBuilder(gsp[2]);
                            for (int i = 3; i < gsp.length; i++) {
                                modeBuilder.append(":").append(gsp[i]);
                            }
                            gsMode = modeBuilder.toString();
                        }
                        MineMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    TetrisMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    AeroChessMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    Game2048MatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    UnoMatchRoom.receiveGameStart(gsRoomId, gsSeed, gsMode);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (msg.startsWith("DUEL_GAME_PUSH:")) {
                // 对手操作推送，格式: roomId:username:REVEAL:row:col:value
                String gpData = msg.substring("DUEL_GAME_PUSH:".length());
                String[] gpp = gpData.split(":", 2);
                if (gpp.length >= 2) {
                    try {
                        int gpRoomId = Integer.parseInt(gpp[0]);
                        String cellData = gpp[1];
                        MineDuelGame.receiveCellPush(gpRoomId, cellData);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (msg.startsWith("DUEL_GAME_OVER:")) {
                // 游戏结束推送
                String goData = msg.substring("DUEL_GAME_OVER:".length());
                String[] gop = goData.split(":", 2);
                if (gop.length >= 2) {
                    try {
                        int goRoomId = Integer.parseInt(gop[0]);
                        String results = gop[1];
                        MineDuelGame.receiveGameOver(goRoomId, results);
                        TetrisStackDuelGame.receiveGameOver(goRoomId, results);
                        TetrisDuelGame.receiveGameOver(goRoomId, results);
                        Game2048DuelGame.receiveAllDone(goRoomId, results);
                        UnoGame.receiveGameOver(goRoomId, results);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (msg.startsWith("DUEL_BOARD_PUSH:")) {
                // 2048 对手局面推送（含分数 + 4x4 棋盘）
                String bpData = msg.substring("DUEL_BOARD_PUSH:".length());
                String[] bpp = bpData.split(":", 3);
                if (bpp.length >= 3) {
                    try {
                        int bpRoomId = Integer.parseInt(bpp[0]);
                        String bpPushData = bpp[1] + ":" + bpp[2];
                        Game2048DuelGame.receiveBoardPush(bpRoomId, bpPushData);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (msg.startsWith("DUEL_MATCH_MOVE:")) {
                // 匹配成功，转移到目标房间
                String mmData = msg.substring("DUEL_MATCH_MOVE:".length());
                String[] mmp = mmData.split(":");
                if (mmp.length >= 3) {
                    try {
                        int targetRoomId = Integer.parseInt(mmp[0]);
                        String mmMode = mmp[1];
                        int mmMax = Integer.parseInt(mmp[2]);
                        MineMatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                        TetrisMatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                        AeroChessMatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                        Game2048MatchRoom.receiveMatchMove(targetRoomId, mmMode, mmMax);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }
            if (msg.startsWith("DUEL_BOTS_JOINED:")) {
                // 机器人已加入
                String bjData = msg.substring("DUEL_BOTS_JOINED:".length());
                try {
                    int bjRoomId = Integer.parseInt(bjData.trim());
                    MineMatchRoom room = MineMatchRoom.getActiveRoom(bjRoomId);
                    if (room != null) {
                        SwingUtilities.invokeLater(() -> room.onBotsJoinedPush());
                    }
                    TetrisMatchRoom tRoom = TetrisMatchRoom.getActiveRoom(bjRoomId);
                    if (tRoom != null) {
                        SwingUtilities.invokeLater(() -> tRoom.onBotsJoinedPush());
                    }
                    AeroChessMatchRoom aRoom = AeroChessMatchRoom.getActiveRoom(bjRoomId);
                    if (aRoom != null) {
                        SwingUtilities.invokeLater(() -> aRoom.onBotsJoinedPush());
                    }
                    Game2048MatchRoom gRoom = Game2048MatchRoom.getActiveRoom(bjRoomId);
                    if (gRoom != null) {
                        SwingUtilities.invokeLater(() -> gRoom.onBotsJoinedPush());
                    }
                    UnoMatchRoom.receiveBotsJoined(bjRoomId);
                } catch (NumberFormatException ignored) {}
                return;
            }
            if (msg.startsWith("DUEL_CHAT_PUSH:")) {
                // 对决聊天推送
                String cpData = msg.substring("DUEL_CHAT_PUSH:".length());
                String[] cpp = cpData.split(":", 3);
                if (cpp.length >= 3) {
                    String cpUser = cpp[1];
                    String cpMsg = cpp[2];
                    onDuelChatReceived(cpUser, cpMsg);
                }
                return;
            }
        }

        // 处理对决邀请（来自聊天消息）
        if (msg.startsWith("DUEL_INVITE:") || msg.startsWith("DUEL_ACCEPT:") || msg.startsWith("DUEL_REJECT:")) {
            // 收到对决邀请时：自动导航到发起者的聊天框
            if (msg.startsWith("DUEL_INVITE:") && !realSender.equals(currentChatFriend)) {
                navigateToChat(realSender);
            }
            // 确保消息中心窗口可见
            if (!isVisible()) {
                setVisible(true);
            }
            setExtendedState(JFrame.NORMAL);
            toFront();
            // 推送到当前聊天窗口
            if (realSender.equals(currentChatFriend) || sender.equals(currentChatFriend)) {
                if (msg.startsWith("DUEL_INVITE:")) {
                    String[] dps = msg.substring("DUEL_INVITE:".length()).split(":");
                    if (dps.length >= 4) {
                        long ts = dps.length >= 5 ? Long.parseLong(dps[4]) : System.currentTimeMillis();
                        chatMessagesPanel.add(createDuelInviteCard(realSender, Integer.parseInt(dps[0]), dps[1], Integer.parseInt(dps[2]), dps[3], ts));
                        chatMessagesPanel.add(Box.createVerticalStrut(8));
                    }
                } else if (msg.startsWith("DUEL_ACCEPT:")) {
                    chatMessagesPanel.add(createBubble(realSender + " 已加入对决房间！", false, ""));
                    chatMessagesPanel.add(Box.createVerticalStrut(8));
                } else if (msg.startsWith("DUEL_REJECT:")) {
                    chatMessagesPanel.add(createBubble("好友太忙了下次吧！", false, ""));
                    chatMessagesPanel.add(Box.createVerticalStrut(8));
                }
                chatMessagesPanel.revalidate();
                chatMessagesPanel.repaint();
                JScrollBar bar = chatScroll.getVerticalScrollBar();
                SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
                return;
            }
            return;
        }

        if (realSender.equals(currentChatFriend) || sender.equals(currentChatFriend)) {
            chatMessagesPanel.add(createBubble(msg, false, ""));
            chatMessagesPanel.add(Box.createVerticalStrut(8));
            chatMessagesPanel.revalidate();
            chatMessagesPanel.repaint();
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
        } else if (currentChatFriend == null) {
            if (msg.contains("已成为你的好友") || msg.contains("拒绝成为你的好友")
                    || msg.startsWith("FRIEND_REQUEST:")) {
                chatMessagesPanel.add(createBubbleWithLabel(msg, false, realSender + " →"));
                chatMessagesPanel.add(Box.createVerticalStrut(8));
                chatMessagesPanel.revalidate();
                chatMessagesPanel.repaint();
            } else {
                chatMessagesPanel.add(createBubble(msg, false, ""));
                chatMessagesPanel.add(Box.createVerticalStrut(8));
                chatMessagesPanel.revalidate();
                chatMessagesPanel.repaint();
            }
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
        }
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_SIDEBAR);

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(BG_SIDEBAR);
        titleBar.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        JLabel titleLabel = new JLabel("消息");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleBar.add(titleLabel, BorderLayout.WEST);
        panel.add(titleBar, BorderLayout.NORTH);

        notifyBtn = new JButton("消息通知");
        notifyBtn.setIcon(createEmojiIcon("\uD83D\uDCE2", 18));
        notifyBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        notifyBtn.setForeground(TEXT_PRIMARY);
        notifyBtn.setBackground(BTN_BG);
        notifyBtn.setFocusPainted(false);
        notifyBtn.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        notifyBtn.setHorizontalAlignment(SwingConstants.LEFT);
        notifyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        notifyBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (currentChatFriend != null) notifyBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { if (currentChatFriend != null) notifyBtn.setBackground(BTN_BG); }
        });
        notifyBtn.addActionListener(e -> showNotifications());

        // 顶部区域：标题 + 通知按钮 + 分隔线，作为一个整体放在 NORTH
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BG_SIDEBAR);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_SIDEBAR);
        headerPanel.add(titleBar, BorderLayout.NORTH);
        headerPanel.add(notifyBtn, BorderLayout.CENTER);
        topPanel.add(headerPanel, BorderLayout.NORTH);
        JSeparator sep = new JSeparator();
        sep.setBackground(new Color(70, 73, 76));
        topPanel.add(sep, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);

        // 好友列表占满剩余空间（CENTER）
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBackground(BG_SIDEBAR);

        friendListModel = new DefaultListModel<>();
        friendList = new JList<>(friendListModel);
        friendList.setBackground(BG_SIDEBAR);
        friendList.setSelectionBackground(SELECTED_BG);
        friendList.setSelectionForeground(TEXT_PRIMARY);
        friendList.setBorder(null);
        friendList.setCellRenderer(new FriendCellRenderer());
        friendList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int idx = friendList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < friendListModel.size()) {
                    FriendItem item = friendListModel.getElementAt(idx);
                    if (!"暂无好友".equals(item.getName())) openChat(item.getName());
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(friendList);
        listScroll.setBackground(BG_SIDEBAR);
        listScroll.getViewport().setBackground(BG_SIDEBAR);
        listScroll.setBorder(null);
        listScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);
        listWrapper.add(listScroll, BorderLayout.CENTER);
        panel.add(listWrapper, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createChatArea() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_CHAT);

        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_CHAT);
        chatHeader.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        chatTitle = new JLabel("消息通知");
        chatTitle.setIcon(createEmojiIcon("\uD83D\uDCE2", 20));
        chatTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        chatTitle.setForeground(TEXT_PRIMARY);
        chatHeader.add(chatTitle, BorderLayout.CENTER);
        panel.add(chatHeader, BorderLayout.NORTH);

        chatMessagesPanel = new JPanel();
        chatMessagesPanel.setBackground(BG_CHAT);
        chatMessagesPanel.setLayout(new BoxLayout(chatMessagesPanel, BoxLayout.Y_AXIS));
        chatMessagesPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        chatScroll = new JScrollPane(chatMessagesPanel);
        chatScroll.setBackground(BG_CHAT);
        chatScroll.getViewport().setBackground(BG_CHAT);
        chatScroll.setBorder(null);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(chatScroll, BorderLayout.CENTER);

        inputPanel = new JPanel(new BorderLayout(10, 5));
        inputPanel.setBackground(BG_CHAT);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));

        inputArea = new JTextArea();
        inputArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        inputArea.setBackground(BG_INPUT);
        inputArea.setForeground(TEXT_PRIMARY);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(90, 93, 96), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        inputArea.setCaretColor(TEXT_PRIMARY);
        inputArea.setRows(3);
        inputArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) doSend();
            }
        });

        sendBtn = new JButton("发 送");
        sendBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        sendBtn.setForeground(TEXT_PRIMARY);
        sendBtn.setBackground(BG_BUBBLE_ME);
        sendBtn.setFocusPainted(false);
        sendBtn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> doSend());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setBackground(BG_CHAT);
        btnPanel.add(sendBtn);
        inputPanel.add(inputArea, BorderLayout.CENTER);
        inputPanel.add(btnPanel, BorderLayout.SOUTH);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }

    public void showNotifications() {
        final String target = "__notifications__";
        if (target.equals(loadingFor)) return;
        loadingFor = target;
        currentChatFriend = null;
        chatTitle.setText("消息通知");
        chatTitle.setIcon(createEmojiIcon("\uD83D\uDCE2", 20));
        notifyBtn.setBackground(SELECTED_BG);
        inputArea.setEnabled(false);
        inputArea.setBackground(new Color(40, 43, 46));
        sendBtn.setEnabled(false);
        sendBtn.setBackground(new Color(60, 63, 66));

        Thread t1 = new Thread(() -> {
            try {
                String result = ServerClient.getMessages(username);
                SwingUtilities.invokeLater(() -> {
                    if (!target.equals(loadingFor)) return;
                    chatMessagesPanel.removeAll();
                    renderMessages(result, false);
                    SwingUtilities.invokeLater(() -> {
                        JScrollBar bar = chatScroll.getVerticalScrollBar();
                        bar.setValue(bar.getMaximum());
                    });
                    if (target.equals(loadingFor)) loadingFor = null;
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (!target.equals(loadingFor)) return;
                    showPlaceholder("加载失败，请重试");
                    if (target.equals(loadingFor)) loadingFor = null;
                });
            }
        });
        t1.setDaemon(true);
        t1.start();
        friendList.clearSelection();
    }

    private void openChat(String friendName) {
        if (friendName.equals(loadingFor)) return;
        loadingFor = friendName;
        currentChatFriend = friendName;
        chatTitle.setText(friendName);
        chatTitle.setIcon(null);
        notifyBtn.setBackground(BTN_BG);
        inputArea.setEnabled(true);
        inputArea.setBackground(BG_INPUT);
        sendBtn.setEnabled(true);
        sendBtn.setBackground(BG_BUBBLE_ME);

        final String myLoading = friendName;
        Thread t2 = new Thread(() -> {
            try {
                String result = ServerClient.getRecentChatWithUnread(username, friendName, 6);
                SwingUtilities.invokeLater(() -> {
                    if (!myLoading.equals(loadingFor)) return;
                    chatMessagesPanel.removeAll();
                    renderMessages(result, true);
                    SwingUtilities.invokeLater(() -> {
                        JScrollBar bar = chatScroll.getVerticalScrollBar();
                        bar.setValue(bar.getMaximum());
                    });
                    if (myLoading.equals(loadingFor)) loadingFor = null;
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (!myLoading.equals(loadingFor)) return;
                    showPlaceholder("加载失败，请重试");
                    if (myLoading.equals(loadingFor)) loadingFor = null;
                });
            }
        });
        t2.setDaemon(true);
        t2.start();
        friendList.repaint();
    }

    private void renderMessages(String result, boolean isChat) {
        chatMessagesPanel.removeAll();
        if (!result.startsWith("SUCCESS")) {
            showPlaceholder("加载消息失败");
            return;
        }

        String data = result.substring("SUCCESS|".length());
        if (data.isEmpty()) {
            showPlaceholder(isChat ? "暂无消息记录" : "暂无消息");
            return;
        }

        java.util.List<String> entries = new ArrayList<>();
        for (String p : data.split(";")) {
            if (!p.isEmpty()) entries.add(p);
        }

        if (isChat) {
            for (String entry : entries) {
                String[] msgParts = entry.split("\\|", 5);
                if (msgParts.length < 4) continue;
                String sender = msgParts[0];
                String msg = msgParts[2];
                boolean isMe = sender.equals(username);
                String timeStr = "";
                if (msgParts.length >= 5 && msgParts[4] != null && !msgParts[4].isEmpty()) {
                    try {
                        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(msgParts[4]);
                        java.time.LocalDateTime ldt = ts.toLocalDateTime();
                        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
                        timeStr = ldt.format(fmt);
                    } catch (Exception ignored) {}
                }

                // 对决邀请卡片
                if (msg.startsWith("DUEL_INVITE:")) {
                    String[] duelParts = msg.substring("DUEL_INVITE:".length()).split(":");
                    if (duelParts.length >= 4) {
                        int duelRoomId = Integer.parseInt(duelParts[0]);
                        String duelMode = duelParts[1];
                        int duelMax = Integer.parseInt(duelParts[2]);
                        String duelGame = duelParts[3];
                        // 获取发送时间戳（优先嵌入式时间戳，其次数据库时间戳）
                        long sendTime = System.currentTimeMillis();
                        if (duelParts.length >= 5) {
                            sendTime = Long.parseLong(duelParts[4]);
                        } else if (msgParts.length >= 5 && msgParts[4] != null && !msgParts[4].isEmpty()) {
                            try {
                                sendTime = java.sql.Timestamp.valueOf(msgParts[4]).getTime();
                            } catch (Exception ignored) {}
                        }
                        if (!isMe) {
                            chatMessagesPanel.add(createDuelInviteCard(sender, duelRoomId, duelMode, duelMax, duelGame, sendTime));
                            chatMessagesPanel.add(Box.createVerticalStrut(8));
                            continue;
                        }
                    }
                }
                // 对决接受
                if (msg.startsWith("DUEL_ACCEPT:")) {
                    String acceptInfo = msg.substring("DUEL_ACCEPT:".length());
                    chatMessagesPanel.add(createBubble(sender + " 已加入对决房间！", isMe, timeStr));
                    chatMessagesPanel.add(Box.createVerticalStrut(8));
                    continue;
                }
                // 对决拒绝
                if (msg.startsWith("DUEL_REJECT:")) {
                    chatMessagesPanel.add(createBubble("好友太忙了下次吧！", isMe, timeStr));
                    chatMessagesPanel.add(Box.createVerticalStrut(8));
                    continue;
                }

                chatMessagesPanel.add(createBubble(msg, isMe, timeStr));
                chatMessagesPanel.add(Box.createVerticalStrut(8));
            }
        } else {
            // 收集所有消息用于判断已处理状态（无需额外请求）
            java.util.Set<String> handledRequests = new HashSet<>();
            for (String entry : entries) {
                String[] msgParts = entry.split("\\|", 5);
                if (msgParts.length < 3) continue;
                String receiver = msgParts[1];
                String msg = msgParts[2];
                if (!receiver.equals(username)) continue;
                if (msg.contains("已成为你的好友")) {
                    // 格式: "B已成为你的好友！" → 提取 B
                    String r = msg.split("已成为")[0];
                    if (!r.isEmpty()) handledRequests.add(r);
                }
                if (msg.startsWith("你已成为")) {
                    // 格式: "你已成为A的好友！" → 提取 A
                    String r = msg.replace("你已成为", "").replace("的好友！", "").replace("的好友!", "");
                    if (!r.isEmpty()) handledRequests.add(r);
                }
                if (msg.contains("拒绝成为你的好友")) {
                    // 格式: "A拒绝成为你的好友！" → 提取 A
                    String r = msg.replace("拒绝成为你的好友！", "").replace("拒绝成为你的好友!", "");
                    if (!r.isEmpty()) handledRequests.add(r);
                }
            }

            // 渲染通知卡片
            boolean hasAny = false;
            java.util.Set<String> rendered = new HashSet<>();
            for (String entry : entries) {
                String[] msgParts = entry.split("\\|", 5);
                if (msgParts.length < 3) continue;
                String sender = msgParts[0];
                String receiver = msgParts[1];
                String msg = msgParts[2];
                if (!receiver.equals(username)) continue;

                boolean isNotify = msg.startsWith("FRIEND_REQUEST:")
                                || msg.contains("已成为你的好友")
                                || msg.contains("拒绝成为你的好友");
                if (!isNotify) continue;

                String requester = "";
                if (msg.startsWith("FRIEND_REQUEST:")) {
                    requester = msg.substring("FRIEND_REQUEST:".length());
                } else if (msg.contains("已成为你的好友")) {
                    // "B已成为你的好友！" → 提取 B
                    requester = msg.split("已成为")[0];
                } else if (msg.startsWith("你已成为")) {
                    // "你已成为A的好友！" → 提取 A
                    requester = msg.replace("你已成为", "").replace("的好友！", "").replace("的好友!", "");
                } else if (msg.contains("拒绝成为你的好友")) {
                    // "A拒绝成为你的好友！" → 提取 A
                    requester = msg.replace("拒绝成为你的好友！", "").replace("拒绝成为你的好友!", "");
                }
                if (!requester.isEmpty() && rendered.contains(requester)) continue;
                if (!requester.isEmpty()) rendered.add(requester);

                hasAny = true;
                if (msg.startsWith("FRIEND_REQUEST:")) {
                    chatMessagesPanel.add(createFriendRequestCard(requester, sender, handledRequests.contains(requester)));
                } else {
                    chatMessagesPanel.add(createBubbleWithLabel(msg, false, requester + " →"));
                }
                chatMessagesPanel.add(Box.createVerticalStrut(8));
            }
            if (!hasAny) showPlaceholder("暂无消息");
        }

        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
    }

    private void doSend() {
        if (currentChatFriend == null) return;
        String msg = inputArea.getText().trim();
        if (msg.isEmpty()) return;

        // 对决大厅聊天：不存库，纯推送中转
        if (DUEL_LOBBY_ID.equals(currentChatFriend)) {
            Thread t3 = new Thread(() -> {
                String result = ServerClient.duelChat(duelRoomId, username, msg);
                SwingUtilities.invokeLater(() -> {
                    if (!result.startsWith("SUCCESS")) {
                        JOptionPane.showMessageDialog(this, "发送失败", "提示", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    inputArea.setText("");
                    chatMessagesPanel.add(createBubble(msg, true, ""));
                    chatMessagesPanel.add(Box.createVerticalStrut(8));
                    chatMessagesPanel.revalidate();
                    chatMessagesPanel.repaint();
                    JScrollBar bar = chatScroll.getVerticalScrollBar();
                    bar.setValue(bar.getMaximum());
                });
            });
            t3.setDaemon(true);
            t3.start();
            return;
        }

        Thread t3 = new Thread(() -> {
            String result = ServerClient.sendChatMessage(username, currentChatFriend, msg);
            SwingUtilities.invokeLater(() -> {
                if (!result.startsWith("SUCCESS")) {
                    String errMsg = result.contains("|") ? result.split("\\|")[1] : "发送失败";
                    JOptionPane.showMessageDialog(this, errMsg, "提示", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                inputArea.setText("");
                chatMessagesPanel.add(createBubble(msg, true, ""));
                chatMessagesPanel.add(Box.createVerticalStrut(8));
                chatMessagesPanel.revalidate();
                chatMessagesPanel.repaint();
                JScrollBar bar = chatScroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            });
        });
        t3.setDaemon(true);
        t3.start();
    }

    private void showPlaceholder(String text) {
        JPanel ph = new JPanel(new GridBagLayout());
        ph.setBackground(BG_CHAT);
        ph.setName("placeholder");
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        label.setForeground(TEXT_SECONDARY);
        ph.add(label);
        chatMessagesPanel.add(ph);
    }

    private JPanel createFriendRequestCard(String requester, String originalSender, boolean handled) {
        JPanel card = new JPanel();
        card.setBackground(BG_CHAT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel fromLabel = new JLabel(requester + " →");
        fromLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        fromLabel.setForeground(TEXT_SECONDARY);
        fromLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(fromLabel);
        card.add(Box.createVerticalStrut(4));

        JLabel requestBubble = new JLabel("申请添加你为好友");
        requestBubble.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        requestBubble.setForeground(TEXT_PRIMARY);
        requestBubble.setBackground(new Color(90, 70, 160));
        requestBubble.setOpaque(true);
        requestBubble.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 50, 140), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        requestBubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(requestBubble);
        card.add(Box.createVerticalStrut(8));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        btnRow.setBackground(BG_CHAT);

        if (handled) {
            JLabel statusLabel = new JLabel("已处理");
            statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            statusLabel.setForeground(new Color(150, 153, 156));
            btnRow.add(statusLabel);
        } else {
            JButton acceptBtn = new JButton("同意");
            acceptBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            acceptBtn.setForeground(Color.WHITE);
            acceptBtn.setBackground(new Color(40, 140, 40));
            acceptBtn.setFocusPainted(false);
            acceptBtn.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            acceptBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            acceptBtn.addActionListener(e -> handleAccept(requester));

            JButton rejectBtn = new JButton("拒绝");
            rejectBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            rejectBtn.setForeground(Color.WHITE);
            rejectBtn.setBackground(new Color(180, 50, 50));
            rejectBtn.setFocusPainted(false);
            rejectBtn.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            rejectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            rejectBtn.addActionListener(e -> handleReject(requester));

            btnRow.add(acceptBtn);
            btnRow.add(rejectBtn);
        }
        card.add(btnRow);
        return card;
    }

    /** 对决邀请卡片 */
    private JPanel createDuelInviteCard(String fromUser, int duelRoomId, String duelMode, int duelMax, String duelGame, long sendTime) {
        JPanel card = new JPanel();
        card.setBackground(BG_CHAT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel fromLabel = new JLabel(fromUser + " →");
        fromLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        fromLabel.setForeground(TEXT_SECONDARY);
        fromLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(fromLabel);
        card.add(Box.createVerticalStrut(4));

        String modeLabel = duelMode;
        if ("UNO".equals(duelGame)) {
            if ("1".equals(duelMode)) modeLabel = "逆转叠加";
            else if ("0".equals(duelMode)) modeLabel = "普通叠加";
        }
        String inviteText = "邀请你参加 " + duelGame + " " + modeLabel + " " + duelMax + "人对决";
        JLabel inviteBubble = new JLabel(inviteText);
        inviteBubble.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        inviteBubble.setForeground(TEXT_PRIMARY);
        inviteBubble.setBackground(new Color(160, 100, 40));
        inviteBubble.setOpaque(true);
        inviteBubble.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(140, 80, 20), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        inviteBubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(inviteBubble);
        card.add(Box.createVerticalStrut(8));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        btnRow.setBackground(BG_CHAT);

        // 检查是否已过期或已处理
        String inviteKey = fromUser + ":" + duelRoomId;
        boolean alreadyHandled = handledDuelInvites.contains(inviteKey);
        long elapsed = System.currentTimeMillis() - sendTime;
        boolean expired = elapsed >= 60000;

        if (alreadyHandled) {
            JLabel statusLabel = new JLabel("已处理");
            statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            statusLabel.setForeground(new Color(150, 153, 156));
            btnRow.add(statusLabel);
        } else if (expired) {
            JLabel statusLabel = new JLabel("邀请已过期");
            statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            statusLabel.setForeground(new Color(150, 153, 156));
            btnRow.add(statusLabel);
        } else {
            final javax.swing.Timer[] expiryHolder = new javax.swing.Timer[1];

            JButton acceptBtn = new JButton("\u2713 接受");
            acceptBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            acceptBtn.setForeground(Color.WHITE);
            acceptBtn.setBackground(new Color(40, 140, 40));
            acceptBtn.setFocusPainted(false);
            acceptBtn.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            acceptBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            acceptBtn.addActionListener(e -> {
                handledDuelInvites.add(inviteKey);
                if (expiryHolder[0] != null) expiryHolder[0].stop();
                btnRow.removeAll();
                JLabel doneLabel = new JLabel("已处理");
                doneLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                doneLabel.setForeground(new Color(150, 153, 156));
                btnRow.add(doneLabel);
                btnRow.revalidate();
                btnRow.repaint();
                // 发送同意 + 加入房间
                String acceptMsg = "DUEL_ACCEPT:" + duelRoomId;
                Thread t = new Thread(() -> {
                    ServerClient.sendChatMessage(username, fromUser, acceptMsg);
                    SwingUtilities.invokeLater(() -> {
                        if ("俄罗斯方块".equals(duelGame)) {
                            TetrisMatchRoom.joinRoom(username, userId, duelRoomId, duelMode, duelMax);
                        } else if ("飞行棋".equals(duelGame)) {
                            AeroChessMatchRoom.joinRoom(username, userId, duelRoomId, duelMode, duelMax);
                        } else if ("2048".equals(duelGame)) {
                            Game2048MatchRoom.joinRoom(username, userId, duelRoomId, duelMode, duelMax);
                        } else if ("UNO".equals(duelGame)) {
                            UnoMatchRoom.joinRoom(username, userId, duelRoomId, duelMode, duelMax);
                        } else {
                            MineMatchRoom.joinRoom(username, userId, duelRoomId, duelMode, duelMax);
                        }
                        // 隐藏摸鱼中心：优先用消息中心自身记录的 homeFrame，其次用全局引用
                        FishGrabbingHome.hideActiveInstance();
                    });
                });
                t.setDaemon(true);
                t.start();
            });

            JButton rejectBtn = new JButton("\u2717 拒绝");
            rejectBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            rejectBtn.setForeground(Color.WHITE);
            rejectBtn.setBackground(new Color(180, 50, 50));
            rejectBtn.setFocusPainted(false);
            rejectBtn.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            rejectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            rejectBtn.addActionListener(e -> {
                handledDuelInvites.add(inviteKey);
                if (expiryHolder[0] != null) expiryHolder[0].stop();
                btnRow.removeAll();
                JLabel doneLabel = new JLabel("已处理");
                doneLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                doneLabel.setForeground(new Color(150, 153, 156));
                btnRow.add(doneLabel);
                btnRow.revalidate();
                btnRow.repaint();
                String rejectMsg = "DUEL_REJECT:" + duelRoomId;
                Thread t = new Thread(() -> {
                    ServerClient.sendChatMessage(username, fromUser, rejectMsg);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "已拒绝 " + fromUser + " 的对决邀请", "已拒绝", JOptionPane.INFORMATION_MESSAGE);
                    });
                });
                t.setDaemon(true);
                t.start();
            });

            btnRow.add(acceptBtn);
            btnRow.add(rejectBtn);

            // 60秒过期定时器
            long remaining = 60000 - elapsed;
            javax.swing.Timer expiryTimer = new javax.swing.Timer((int)Math.max(remaining, 1000), evt -> {
                btnRow.removeAll();
                JLabel expiredLabel = new JLabel("邀请已过期");
                expiredLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
                expiredLabel.setForeground(new Color(150, 153, 156));
                btnRow.add(expiredLabel);
                btnRow.revalidate();
                btnRow.repaint();
                ((javax.swing.Timer)evt.getSource()).stop();
            });
            expiryTimer.setRepeats(false);
            expiryTimer.start();
            expiryHolder[0] = expiryTimer;
        }
        card.add(btnRow);
        return card;
    }

    private void handleAccept(String requester) {
        String target = "__accept__" + requester;
        if (target.equals(loadingFor)) return;
        loadingFor = target;
        Thread t4 = new Thread(() -> {
            // 发送同意申请，通过 moyu官方 中转通知申请者
            String replyMsg = "FRIEND_ACCEPT:" + requester + "," + username;
            String result = ServerClient.sendChatMessage(username, requester, replyMsg);
            SwingUtilities.invokeLater(() -> {
                if (!target.equals(loadingFor)) return;
                loadingFor = null;
                if (result.startsWith("SUCCESS")) {
                    showNotifications();
                } else {
                    String msg = result.contains("|") ? result.split("\\|")[1] : "处理失败";
                    JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t4.setDaemon(true);
        t4.start();
    }

    private void handleReject(String requester) {
        String target = "__reject__" + requester;
        if (target.equals(loadingFor)) return;
        Thread t5 = new Thread(() -> {
            // 发送拒绝申请，通过 moyu官方 中转通知申请者
            String replyMsg = "FRIEND_REJECT:" + requester + "," + username;
            String result = ServerClient.sendChatMessage(username, requester, replyMsg);
            SwingUtilities.invokeLater(() -> {
                if (!target.equals(loadingFor)) return;
                loadingFor = null;
                if (result.startsWith("SUCCESS")) {
                    showNotifications();
                } else {
                    String msg = result.contains("|") ? result.split("\\|")[1] : "处理失败";
                    JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t5.setDaemon(true);
        t5.start();
    }

    private JPanel createBubble(String text, boolean isMe, String timeStr) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_CHAT);

        // 时间标签
        if (timeStr != null && !timeStr.isEmpty()) {
            JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            timePanel.setBackground(BG_CHAT);
            JLabel timeLabel = new JLabel(timeStr);
            timeLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            timeLabel.setForeground(new Color(140, 143, 146));
            timePanel.add(timeLabel);
            wrapper.add(timePanel, BorderLayout.NORTH);
        }

        // 气泡面板 - 用 FlowLayout 实现贴边
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 8, 0));
        row.setBackground(BG_CHAT);

        // 气泡 - JTextArea 自动换行
        JTextArea bubble = new JTextArea(text);
        bubble.setEditable(false);
        bubble.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        bubble.setForeground(TEXT_PRIMARY);
        bubble.setBackground(isMe ? BG_BUBBLE_ME : BG_BUBBLE_OTHER);
        bubble.setLineWrap(true);
        bubble.setWrapStyleWord(true);
        bubble.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isMe ? new Color(0, 100, 200) : new Color(60, 63, 66), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        bubble.setFocusable(false);
        bubble.setOpaque(true);
        bubble.setMaximumSize(new Dimension(400, 500));

        // 头像
        JLabel avatar = new JLabel(isMe ? "我" : "友", SwingConstants.CENTER);
        avatar.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(isMe ? new Color(0, 100, 180) : new Color(120, 123, 126));
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(36, 36));
        avatar.setMinimumSize(new Dimension(36, 36));
        avatar.setMaximumSize(new Dimension(36, 36));
        avatar.setBorder(BorderFactory.createLineBorder(isMe ? new Color(0, 80, 160) : new Color(100, 103, 106), 1));

        if (isMe) {
            // 自己：先气泡再头像，靠右对齐
            row.add(bubble);
            row.add(avatar);
        } else {
            // 好友：先头像再气泡，靠左对齐
            row.add(avatar);
            row.add(bubble);
        }

        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createBubbleWithLabel(String text, boolean isMe, String label) {
        JPanel wrapper = new JPanel();
        wrapper.setBackground(BG_CHAT);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(650, 1000));

        JLabel fromLabel = new JLabel(label);
        fromLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        fromLabel.setForeground(TEXT_SECONDARY);
        fromLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(fromLabel);
        wrapper.add(Box.createVerticalStrut(4));

        // 气泡 - HTML table 方案实现自动换行
        int maxBubbleWidth = 400;
        int estimatedWidth = text.length() * 14 + 40;
        int bubbleWidth = Math.min(maxBubbleWidth, estimatedWidth);
        JTextArea bubble = new JTextArea(text);
        bubble.setEditable(false);
        bubble.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        bubble.setForeground(TEXT_PRIMARY);
        bubble.setBackground(isMe ? BG_BUBBLE_ME : BG_BUBBLE_OTHER);
        bubble.setLineWrap(true);
        bubble.setWrapStyleWord(true);
        bubble.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isMe ? new Color(0, 100, 200) : new Color(60, 63, 66), 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        bubble.setFocusable(false);
        bubble.setOpaque(true);
        bubble.setMaximumSize(new Dimension(400, 500));

        JLabel avatar = new JLabel(isMe ? "我" : "友", SwingConstants.CENTER);
        avatar.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(isMe ? new Color(0, 100, 180) : new Color(120, 123, 126));
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(36, 36));
        avatar.setBorder(BorderFactory.createLineBorder(isMe ? new Color(0, 80, 160) : new Color(100, 103, 106), 1));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setBackground(BG_CHAT);
        row.setMaximumSize(new Dimension(650, 1000));
        row.add(avatar);
        row.add(Box.createHorizontalStrut(8));
        row.add(bubble);

        wrapper.add(row);
        return wrapper;
    }

    private void loadFriendList() {
        friendListModel.clear();
        String result = ServerClient.getFriends(username);
        if (!result.startsWith("SUCCESS") || result.length() <= "SUCCESS|".length()) {
            friendListModel.addElement(new FriendItem("暂无好友", 0));
            return;
        }

        String data = result.substring("SUCCESS|".length());
        List<FriendItem> online = new ArrayList<>();
        List<FriendItem> offline = new ArrayList<>();

        for (String entry : data.split(";")) {
            String[] parts = entry.split(",");
            if (parts.length == 2) {
                FriendItem fi = new FriendItem(parts[0], Integer.parseInt(parts[1]));
                if (fi.getState() == 1) online.add(fi);
                else offline.add(fi);
            }
        }

        for (FriendItem f : online) {
            if (!"moyu官方".equals(f.getName())) friendListModel.addElement(f);
        }
        for (FriendItem f : offline) {
            if (!"moyu官方".equals(f.getName())) friendListModel.addElement(f);
        }

        if (friendListModel.isEmpty()) {
            friendListModel.addElement(new FriendItem("暂无好友", 0));
        }
    }

    private class FriendCellRenderer extends JPanel implements ListCellRenderer<FriendItem> {
        private JLabel dotLabel;
        private JLabel nameLabel;

        FriendCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
            setBackground(BG_SIDEBAR);
            setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
            dotLabel = new JLabel("\u25CF");
            dotLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            nameLabel.setForeground(TEXT_PRIMARY);
            add(dotLabel);
            add(nameLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FriendItem> list, FriendItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.getName());
            dotLabel.setForeground(value.getState() == 1 ? ONLINE_COLOR : OFFLINE_COLOR);
            setBackground(isSelected ? SELECTED_BG : BG_SIDEBAR);
            return this;
        }
    }

    static class FriendItem {
        private String name;
        private int state;
        FriendItem(String name, int state) { this.name = name; this.state = state; }
        String getName() { return name; }
        int getState() { return state; }
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

    private ImageIcon createEmojiIcon(String emoji, int size) {
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
        return new ImageIcon(img);
    }
}