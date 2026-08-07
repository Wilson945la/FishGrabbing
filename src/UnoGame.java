import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

/**
 * UNO 主游戏。
 * 4-8 人，6 分钟限时，Java2D 手绘牌面。
 * 同时支持离线（roomId=0，本地机器人）与在线（>0，server 权威），
 * 在线的事件路由/同步由 UnoMatchRoom + ServerClient 协调，本类只暴露公共方法供外部驱动。
 */
public class UnoGame extends JFrame {
    // ===== 常量 =====
    static final int MIN_PLAYERS = 4;
    static final int MAX_PLAYERS = 8;
    static final int INITIAL_HAND = 7;
    static final long TURN_LIMIT_MS = 15000; // 单回合出牌限时，与在线一致 15 秒
    static final int CARD_W = 64;
    static final int CARD_H = 96;
    static final int W = 1000, H = 780;
    static final int PLAY_AREA_CX = 500, PLAY_AREA_CY = 320, PLAYER_R = 220;
    static final int AVATAR_R = 36;
    static final int HAND_Y = 620;
    static final int HAND_CARD_GAP = 26; // 牌之间重叠
    static final int UNO_BTN_X = 880, UNO_BTN_Y = 600, UNO_BTN_R = 38;

    // ===== 颜色 =====
    static final Color BG = new Color(0x1b, 0x1b, 0x29);
    static final Color PANEL_BG = new Color(0x2a, 0x2a, 0x3a);
    static final Color FG = new Color(0xf5, 0xf5, 0xf5);
    static final Color DARK_FG = new Color(0x33, 0x33, 0x44); // 浅色头像上的深色字
    static final Color YELLOW = new Color(0xff, 0xc1, 0x07);
    static final Color C_RED = new Color(0xe5, 0x39, 0x35);
    static final Color C_YELLOW = new Color(0xfd, 0xd8, 0x35);
    static final Color C_GREEN = new Color(0x43, 0xa0, 0x47);
    static final Color C_BLUE = new Color(0x1e, 0x88, 0xe5);
    static final Color C_BLACK = new Color(0x21, 0x21, 0x21);
    static final Color C_BACK = new Color(0x37, 0x47, 0x4f);
    static final Color HILITE = new Color(0xff, 0xff, 0xff, 70);
    static final Color SEL_BORDER = new Color(0xff, 0xe0, 0x00);
    static final Color ACTIVE_BORDER = new Color(0xff, 0xff, 0x66);
    // 闪烁高亮色（亮春绿，匹配“打出”按钮，且对白/黑底牌都清晰）
    static final Color BLINK_GLOW = new Color(0x8c, 0xff, 0x5a);

    // ===== 数据 =====
    static class Player {
        String name;
        boolean isBot;
        List<UnoCard> hand = new ArrayList<>();
        boolean showHammer = false; // 未喊 UNO 头像旁闪锤子
        Rectangle hammerHit = null; // 锤子按钮的可点区
        int pendingDrawsOnMe = 0; // 累加的张数提示
        long hammerShownAt = 0;
        int avatarSeed;
        boolean finished = false; // 已出完牌
        int finishRank = 0; // 1..N
        String seatRegion = "self"; // 布局段："top"/"left"/"right"/"self"（在 computePlayerPositions 填充）
        Player(String n, boolean b, int s) { name = n; isBot = b; avatarSeed = s; }
    }

    private final List<Player> players = new ArrayList<>();
    private int myPlayerIdx;
    private final int mode; // 0=普通叠加 1=逆转叠加
    private final int roomId; // 0=离线
    private final String myUsername;
    private final Runnable onCloseCallback;

    private List<UnoCard> drawPile;
    private final List<UnoCard> discardPile = new ArrayList<>();
    private UnoCard topCard;
    private UnoCard.Color currentColor;
    private int currentPlayerIdx;
    private int direction = 1;
    private double ringSpin = 0; // 方向环自转角度（弧度），一直转
    private long ringSpinUntil = 0; // 换向冲击：到点之前用更高的角速度转一下，肉眼可见
    private static final double SPIN_KICK_RATE = 0.32; // 冲击期每帧的角速度（≈ 7x 正常）
    private static final double SPIN_NORMAL_RATE = 0.035;
    private int pendingDraws = 0;
    private boolean autoDrawPending = false; // 已为当前"被加牌且无可接牌"状态发起过自动认罚
    private boolean gameOver = false;
    private int winnerIdx = -1;
    private long startTime;
    private int selectedHandIdx = -1;
    private boolean waitingForColor = false;
    private int wildCardHandIdx = -1; // 待选色的牌在手牌中的下标
    private boolean waitingForChallenge = false;
    private boolean pendingSkip = false;
    private boolean pendingReplay = false;
    private int replayIdx = -1;
    private boolean suppressAutoAdvance = false;
    private boolean reverseDesignMirrored = false; // 每次出 REVERSE 翻转，REVERSE 牌图按此决定是否左右镜像
    // UNO 喊牌大动画：屏幕中心大 UNO 缩小飞到喊牌者头像
    private boolean unoCallAnim = false;
    private long unoCallStartMs = 0;
    private int unoCallFromIdx = -1; // 喊牌者 playerIdx（取头像位置）
    private int challengeFromIdx = -1;
    private boolean unoCalled = false;
    private String wildChallengeFrom; // 谁可以质疑
    private boolean challengeResolved = false; // 质疑窗口已结束
    private UnoCard.Color pendingWildColor; // 暂存选色
    private int lastDrawnCount = 0; // 提示"你摸了N张"
    private long lastDrawnAt = 0;
    private String lastActionText = ""; // 屏幕中央提示
    private long lastActionAt = 0;
    private boolean turnAnimating = false; // 防止出牌动画期间输入

    // 出牌滑动动画：从手牌/头像位置飞到出牌区（discardRect）
    private boolean playingCardAnim = false;
    private UnoCard playingCard;
    private UnoCard.Color playingDisplayColor;
    private int playingFromX, playingFromY, playingToX, playingToY;
    private long playingStartMs;
    private int playingDurationMs = 360; // 0.36s 飞行
    private javax.swing.Timer playAnimTimer;

    // 倒计时 / 机器人 / 锤子
    private final BoardPanel boardPanel;
    private final JButton unoButton;
    private javax.swing.Timer countdownTimer;
    private javax.swing.Timer botTimer;
    private javax.swing.Timer hammerCheckTimer;
    private javax.swing.Timer spinTimer; // 方向环自转

    // ===== 在线模式（roomId>0，服务端权威） =====
    private final boolean onlineMode;
    private long gameDurationMs = 10 * 60 * 1000; // 离线总时长（构造时按人数算，与在线一致）
    private javax.swing.Timer offlineTurnTimer;    // 离线人类回合 15s 出牌限时
    private long turnDeadlineMs = 0;
    /** 与 players.get(myPlayerIdx).hand 平行：界面第 i 张牌 → 服务端手牌下标 */
    private final List<Integer> myServerIdx = new ArrayList<>();
    private int pendingOnlinePlayIdx = -1;   // 万能牌等待选色时暂存的界面下标
    private boolean challengePromptShown = false;
    private boolean drawDecidePromptShown = false; // 摸到的牌能出，是否已弹过"是否打出"
    private boolean onlineGameOverShown = false;
    private boolean onlineStateReceived = false;
    private String prevTopEnc = null;        // 上一帧顶牌编码，用于判断"有人出牌了"
    private int prevCurrentIdx = -1;         // 上一帧轮到谁，用于判断出牌者
    private final Map<String, Integer> prevCounts = new HashMap<>();
    private javax.swing.Timer onlinePollTimer;
    // 服务端计时（在线模式显示 / 结算依据）
    private long srvTurnDeadline = 0;     // 当前回合截止时刻
    private long srvMatchStartMs = 0;     // 开局时刻
    private long srvMatchDurationMs = 0;  // 本局总时长（4 人 10 分钟，每多 1 人 +2 分钟）
    /** 在线对局注册表：roomId → 窗口，供 MessageCenter 路由推送 */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, UnoGame> activeGames
            = new java.util.concurrent.ConcurrentHashMap<>();

    public UnoGame(String myUsername, int myPlayerIdx, int mode,
                   List<String> playerNames, int roomId, long seed,
                   Runnable onCloseCallback) {
        this.myUsername = (myUsername == null || myUsername.isEmpty()) ? "玩家" : myUsername;
        this.myPlayerIdx = myPlayerIdx;
        this.mode = mode;
        this.roomId = roomId;
        this.onlineMode = roomId > 0;
        this.onCloseCallback = onCloseCallback;

        setTitle("UNO" + (mode == 1 ? " · 逆转叠加" : "") + (roomId > 0 ? " · 在线房 " + roomId : ""));
        setSize(W, H);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                // 在线中途关窗：通知服务端结束本局，避免其他人卡在等待
                if (onlineMode && !gameOver) {
                    final int rid = UnoGame.this.roomId;
                    final String un = UnoGame.this.myUsername;
                    Thread t = new Thread(() -> ServerClient.unoEnd(rid, un));
                    t.setDaemon(true);
                    t.start();
                }
                cleanup();
                if (onCloseCallback != null) onCloseCallback.run();
            }
        });

        int seedC = (int)(seed & 0x7fffffff);
        for (String n : playerNames) players.add(new Player(n, n.startsWith("机器人"), seedC++));
        if (players.size() < MIN_PLAYERS || players.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("UNO 玩家数必须在 " + MIN_PLAYERS + "-" + MAX_PLAYERS + " 之间");
        }
        if (myPlayerIdx < 0 || myPlayerIdx >= players.size()) myPlayerIdx = 0;
        // 离线总时长与在线一致：4 人 10 分钟，每多 1 人 +2 分钟
        gameDurationMs = (10 + Math.max(0, players.size() - 4) * 2) * 60_000L;

        // 洗牌发牌
        List<UnoCard> deck = UnoCard.createDeck();
        drawPile = UnoCard.shuffle(deck, seed);
        for (int i = 0; i < INITIAL_HAND; i++) {
            for (Player p : players) p.hand.add(drawPile.remove(drawPile.size() - 1));
        }
        sortHand(players.get(myPlayerIdx)); // 起手牌也排序

        // 起手牌：翻开第一张
        do {
            topCard = drawPile.remove(drawPile.size() - 1);
        } while (topCard.type == UnoCard.Type.WILD_DRAW_FOUR);
        discardPile.add(topCard);
        currentColor = topCard.isWild() ? UnoCard.Color.RED : topCard.color;
        if (topCard.type == UnoCard.Type.WILD) {
            // 起手是变色：让当前人类选色
            waitingForColor = true;
            wildCardHandIdx = -1;
        }

        // 起手是 +2：左边的玩家需接 2
        // 起手是跳过/反转：调整 first player
        int firstIdx;
        if (topCard.type == UnoCard.Type.DRAW_TWO) {
            pendingDraws = 2;
            firstIdx = (myPlayerIdx + 1) % players.size();
        } else if (topCard.type == UnoCard.Type.SKIP) {
            firstIdx = (myPlayerIdx + 2) % players.size();
        } else if (topCard.type == UnoCard.Type.REVERSE) {
            direction = -1;
            firstIdx = (myPlayerIdx + players.size() - 1) % players.size();
        } else {
            firstIdx = (myPlayerIdx + 1) % players.size();
        }
        currentPlayerIdx = firstIdx;
        startTime = System.currentTimeMillis();

        // UI
        boardPanel = new BoardPanel();
        setContentPane(boardPanel);

        unoButton = new JButton("UNO!");
        unoButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        unoButton.setForeground(Color.BLACK);
        unoButton.setBackground(YELLOW);
        unoButton.setFocusPainted(false);
        unoButton.setBorderPainted(false);
        unoButton.setOpaque(true);
        unoButton.setBounds(UNO_BTN_X, UNO_BTN_Y, UNO_BTN_R * 2, UNO_BTN_R * 2);
        unoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        unoButton.addActionListener(e -> onUnoButtonPressed());
        setLayout(null);
        add(unoButton);

        // 倒计时 500ms 刷新 + 倒计时归零触发 endGame
        countdownTimer = new javax.swing.Timer(500, e -> {
            Player me = players.get(UnoGame.this.myPlayerIdx);
            unoButton.setEnabled(!gameOver && currentPlayerIdx == UnoGame.this.myPlayerIdx && me.hand.size() == 2);
            boardPanel.repaint();
            onTick();
        });
        countdownTimer.start();
        // 锤子过期检测（每秒）
        hammerCheckTimer = new javax.swing.Timer(500, e -> checkHammerExpiry());
        hammerCheckTimer.start();
        // 方向环自转（约 22fps，平滑转动；方向随 direction 翻转）
        spinTimer = new javax.swing.Timer(45, e -> {
            long now = System.currentTimeMillis();
            double rate = (now < ringSpinUntil) ? SPIN_KICK_RATE : SPIN_NORMAL_RATE;
            ringSpin += rate * direction;
            if (ringSpin > Math.PI * 2) ringSpin -= Math.PI * 2;
            if (ringSpin < 0) ringSpin += Math.PI * 2;
            boardPanel.repaint();
        });
        spinTimer.start();

        if (onlineMode) {
            // 在线：本地发的牌只是占位，真实牌局完全由服务端 UNO_STATE 覆盖
            activeGames.put(roomId, this);
            currentPlayerIdx = -1;          // 状态到达前禁止一切输入
            for (Player p : players) p.hand.clear();
            drawPile.clear();
            showAction("正在同步牌局…");
            // 主动拉一次（兜底：开局推送可能早于窗口创建）
            Thread t = new Thread(() -> {
                String r = ServerClient.unoState(roomId, this.myUsername);
                if (r != null && r.startsWith("SUCCESS|")) {
                    final String body = r.substring("SUCCESS|".length());
                    SwingUtilities.invokeLater(() -> applyServerState(body));
                }
            });
            t.setDaemon(true);
            t.start();
            // 推送丢失兜底：每 3 秒补拉一次状态
            onlinePollTimer = new javax.swing.Timer(3000, e -> {
                if (gameOver) return;
                Thread pt = new Thread(() -> {
                    String r = ServerClient.unoState(roomId, this.myUsername);
                    if (r != null && r.startsWith("SUCCESS|")) {
                        final String body = r.substring("SUCCESS|".length());
                        SwingUtilities.invokeLater(() -> applyServerState(body));
                    }
                });
                pt.setDaemon(true);
                pt.start();
            });
            onlinePollTimer.start();
        } else {
            // 启动第一回合
            SwingUtilities.invokeLater(this::beginTurn);
        }
    }

    // ============================================================
    //                       游戏流程
    // ============================================================

    private void beginTurn() {
        if (gameOver) return;
        if (onlineMode) return; // 在线：回合推进由服务端状态驱动
        // 轮到谁，谁的锤子就清掉（与在线一致：当前回合的人不会被抓）
        players.get(currentPlayerIdx).showHammer = false;
        srvTurnDeadline = 0; // 顶栏回合倒计时仅在本回合显示
        Player p = players.get(currentPlayerIdx);
        // 如果有 pendingDraws 且当前玩家没法接 → 强制吃
        if (pendingDraws > 0 && !canStack(currentPlayerIdx)) {
            forceTakePending(currentPlayerIdx);
            return;
        }
        if (p.isBot) {
            scheduleBotTurn(p);
        } else {
            // 人类回合：启动 15s 出牌限时（与在线一致）
            startTurnTimer();
        }
    }

    /** 离线人类回合：启动 15s 出牌限时，超时由 autoPlayOffline 代打 */
    private void startTurnTimer() {
        if (offlineTurnTimer != null) offlineTurnTimer.stop();
        turnDeadlineMs = System.currentTimeMillis() + TURN_LIMIT_MS;
        srvTurnDeadline = turnDeadlineMs; // 顶栏显示"出牌 Ns"
        offlineTurnTimer = new javax.swing.Timer((int) TURN_LIMIT_MS, e -> {
            ((javax.swing.Timer) e.getSource()).stop();
            autoPlayOffline();
        });
        offlineTurnTimer.setRepeats(false);
        offlineTurnTimer.start();
    }

    private void stopTurnTimer() {
        if (offlineTurnTimer != null) offlineTurnTimer.stop();
        turnDeadlineMs = 0;
        srvTurnDeadline = 0;
    }

    /** 离线超时代打：打出一张能出的牌（非万能优先，其次万能）；无牌可出则摸 1 张，摸到能出就直接打 */
    private void autoPlayOffline() {
        if (gameOver || turnAnimating) return;
        if (currentPlayerIdx != myPlayerIdx) return;
        if (waitingForColor || waitingForChallenge) return;
        Player me = players.get(myPlayerIdx);
        int playIdx = -1;
        for (int i = 0; i < me.hand.size(); i++) {
            UnoCard c = me.hand.get(i);
            if (!c.isWild() && c.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) { playIdx = i; break; }
        }
        if (playIdx < 0) {
            for (int i = 0; i < me.hand.size(); i++) {
                UnoCard c = me.hand.get(i);
                if (c.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) { playIdx = i; break; }
            }
        }
        if (playIdx >= 0) {
            UnoCard c = me.hand.get(playIdx);
            showAction("超时，系统帮你出了 " + (c.isWild() ? "变色万能" : colorCN(c.color) + (c.displayChar().isEmpty() ? "" : " " + c.displayChar())));
            if (c.isWild()) tryPlayCard(myPlayerIdx, playIdx, mostHandColor());
            else tryPlayCard(myPlayerIdx, playIdx, null);
            return;
        }
        // 无可出：被加牌则直接认罚，否则摸 1 张
        if (pendingDraws > 0) {
            forceTakePending(myPlayerIdx);
            return;
        }
        unoCalled = false;
        UnoCard drawn = drawCards(me, 1);
        if (drawn == null) { advanceTurn(); return; }
        lastDrawnCount = 1;
        lastDrawnAt = System.currentTimeMillis();
        int drawnIdx = me.hand.indexOf(drawn);
        showAction("超时，你摸了 1 张牌");
        if (drawn.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) {
            if (drawn.isWild()) tryPlayCard(myPlayerIdx, drawnIdx, mostHandColor());
            else tryPlayCard(myPlayerIdx, drawnIdx, null);
        } else {
            advanceTurn();
        }
    }

    private boolean canStack(int playerIdx) {
        Player p = players.get(playerIdx);
        for (UnoCard c : p.hand) {
            if (c.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) return true;
        }
        return false;
    }

    private void forceTakePending(int playerIdx) {
        Player p = players.get(playerIdx);
        drawCards(p, pendingDraws);
        showAction(p.name + " 接收了 " + pendingDraws + " 张");
        pendingDraws = 0;
        for (Player q : players) q.pendingDrawsOnMe = 0;
        advanceTurn();
    }

    /** 给自己摸一张（玩家主动摸 / 强制吃多张） */
    private UnoCard drawCards(Player p, int count) {
        UnoCard lastDrawn = null;
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) replenishDrawPile();
            if (drawPile.isEmpty()) break; // 真的没牌了
            lastDrawn = drawPile.remove(drawPile.size() - 1);
            p.hand.add(lastDrawn);
        }
        // 抽到的牌自动排序（用户视角）
        if (p == players.get(myPlayerIdx)) sortHand(p);
        return lastDrawn;
    }

    /** 按规则排序手牌：万能(+4→换色) → 红→黄→蓝→绿；同色内 +2→跳过→倒转→9..0 */
    private void sortHand(Player p) {
        p.hand.sort((a, b) -> {
            int ga = groupOf(a), gb = groupOf(b);
            if (ga != gb) return Integer.compare(ga, gb);
            return Integer.compare(rankOf(a), rankOf(b));
        });
        if (p == players.get(myPlayerIdx)) selectedHandIdx = -1;
    }

    private int groupOf(UnoCard c) {
        if (c.color == UnoCard.Color.BLACK) return 0;            // 万能牌最前
        switch (c.color) {
            case RED:    return 1;
            case YELLOW: return 2;
            case BLUE:   return 3;
            case GREEN:  return 4;
            default:     return 5;
        }
    }

    private int rankOf(UnoCard c) {
        switch (c.type) {
            case WILD_DRAW_FOUR: return 0;   // +4 在万能里最前
            case WILD:           return 1;   // 换色
            case DRAW_TWO:       return 2;   // +2
            case SKIP:           return 3;   // 跳过
            case REVERSE:        return 4;   // 倒转
            case NUMBER:         return 5 + (9 - c.number); // 9→5 … 0→14
            default:             return 20;
        }
    }

    private void replenishDrawPile() {
        if (discardPile.size() <= 1) return;
        UnoCard top = discardPile.remove(discardPile.size() - 1);
        List<UnoCard> rest = new ArrayList<>(discardPile);
        discardPile.clear();
        discardPile.add(top);
        long seed = System.nanoTime();
        Collections.shuffle(rest, new Random(seed));
        drawPile.addAll(rest);
    }

    private int nextIdx(int from, int steps) {
        int n = players.size();
        int idx = from;
        for (int i = 0; i < steps; i++) {
            idx = (idx + direction + n) % n;
        }
        return idx;
    }

    private void advanceTurn() {
        // 清掉上一个玩家的累加提示
        for (Player q : players) q.pendingDrawsOnMe = 0;
        currentPlayerIdx = nextIdx(currentPlayerIdx, 1);
        if (pendingSkip) {
            // 跳过恰好 1 名下家
            currentPlayerIdx = nextIdx(currentPlayerIdx, 1);
            pendingSkip = false;
        }
        if (pendingReplay) {
            // 2 人倒转：出牌者再出一次
            currentPlayerIdx = replayIdx;
            pendingReplay = false;
        }
        selectedHandIdx = -1;
        // 锤子不再因"下家出牌"而消失：被抓玩家只要手牌=1且没喊UNO、且不是自己回合，
        // 锤子就一直挂着（满足"不会因为自己出牌就不能抓了"）。
        lastPlayerIdx = currentPlayerIdx;
        beginTurn();
    }

    private int lastPlayerIdx = -1;

    // ============================================================
    //                       人类输入
    // ============================================================

    void onHandCardClicked(int handIdx) {
        if (gameOver || turnAnimating) return;
        if (currentPlayerIdx != myPlayerIdx) return;
        if (waitingForColor) return; // 等选色
        if (waitingForChallenge) return; // 等下家质疑（不可能轮到我）
        Player me = players.get(myPlayerIdx);
        if (handIdx < 0 || handIdx >= me.hand.size()) return;
        UnoCard c = me.hand.get(handIdx);
        if (selectedHandIdx == handIdx) {
            // 二次点击 = 尝试出牌
            if (c.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) {
                if (onlineMode) {
                    if (c.isWild()) {
                        // 万能牌：先选色，选完由 onColorPicked 发指令
                        pendingOnlinePlayIdx = handIdx;
                        waitingForColor = true;
                        boardPanel.repaint();
                        promptColorPicker();
                    } else {
                        sendOnlinePlay(handIdx, null);
                    }
                } else {
                    stopTurnTimer();
                    tryPlayCard(myPlayerIdx, handIdx, null);
                }
            } else {
                showAction("这张牌不能出");
                selectedHandIdx = -1;
            }
        } else {
            selectedHandIdx = handIdx;
        }
        boardPanel.repaint();
    }

    /** 摸一张（仅当无牌可出 / 不想出时可点 draw 按钮） */
    void onDrawClicked() {
        if (gameOver || turnAnimating) return;
        if (currentPlayerIdx != myPlayerIdx) return;
        if (waitingForColor || waitingForChallenge) return;
        if (onlineMode) {
            // 在线：被加牌时点摸牌 = 认罚吃下累加张数（服务端语义）
            sendOnlineCmd(() -> ServerClient.unoDraw(roomId, myUsername), "摸牌失败");
            return;
        }
        if (pendingDraws > 0) {
            showAction("已被加牌，不能主动摸牌");
            return;
        }
        Player me = players.get(myPlayerIdx);
        stopTurnTimer();
        unoCalled = false; // 摸牌后不再是"即将出到 1 张"状态
        UnoCard drawn = drawCards(me, 1);
        if (drawn == null) { advanceTurn(); return; } // 牌堆空，直接过
        lastDrawnCount = 1;
        lastDrawnAt = System.currentTimeMillis();
        int drawnIdx = me.hand.indexOf(drawn); // 排序后按对象找正确下标
        showAction("你摸了 1 张牌");
        // 摸到的牌能出 → 提示是否打出（15 秒超时默认不放，与在线一致）
        if (drawn.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) {
            String dn = drawn.isWild()
                    ? (drawn.type == UnoCard.Type.WILD_DRAW_FOUR ? "+4 万能" : "变色万能")
                    : colorCN(drawn.color) + (drawn.displayChar().isEmpty() ? "" : " " + drawn.displayChar());
            boolean play = confirmTimeout("摸牌",
                    "你摸到 " + dn + "，可以打出，是否打出？",
                    "打出", "不打", false);
            if (play) {
                tryPlayCard(myPlayerIdx, drawnIdx, null);
                return;
            }
        }
        // 摸完就结束回合（按标准 UNO 规则）
        advanceTurn();
    }

    /** 选色（万能牌） */
    void onColorPicked(UnoCard.Color c) {
        if (!waitingForColor) return;
        waitingForColor = false;
        if (!onlineMode) stopTurnTimer();
        if (onlineMode) {
            // 在线：颜色由服务端确认后随状态回来，本地不抢先改
            int idx = pendingOnlinePlayIdx;
            pendingOnlinePlayIdx = -1;
            if (idx >= 0) sendOnlinePlay(idx, c);
            return;
        }
        currentColor = c;
        // 起手是变色
        if (wildCardHandIdx == -1) {
            // 起手翻牌时翻到变色，不需要进一步动作（已选色）
            boardPanel.repaint();
            return;
        }
        // 玩家手中打出变色
        int handIdx = wildCardHandIdx;
        wildCardHandIdx = -1;
        applyPlayCard(myPlayerIdx, handIdx, c);
    }

    /** 质疑 +4 弹窗回调 */
    void onChallengeDecision(boolean challenge) {
        if (!waitingForChallenge) return;
        waitingForChallenge = false;
        stopTurnTimer();
        int fromIdx = challengeFromIdx;
        if (fromIdx < 0) { clearPendingDraws(); advanceTurn(); return; }
        Player from = players.get(fromIdx);
        boolean hasMatch = hasPlayableCardExcludingWild4(from, currentColorBeforeChallenge);
        if (challenge) {
            if (hasMatch) {
                // 质疑成功：出 +4 的人自吃 4
                drawCards(from, 4);
                showAction("质疑成功！ " + from.name + " 自吃 4 张");
            } else {
                // 质疑失败：质疑者吃 6
                Player me = players.get(myPlayerIdx);
                drawCards(me, 6);
                showAction("质疑失败！ 你吃 6 张");
            }
        } else {
            // 不质疑：当前玩家吃 4
            Player me = players.get(myPlayerIdx);
            drawCards(me, 4);
            showAction("你吃了 4 张，跳过回合");
            clearPendingDraws();
            advanceTurn();
            return;
        }
        clearPendingDraws();
        advanceTurn();
    }

    /** +4 弹窗结束后清理累加（质疑成功/失败/不质疑都要清，否则后续玩家被锁） */
    private void clearPendingDraws() {
        pendingDraws = 0;
        for (Player q : players) q.pendingDrawsOnMe = 0;
    }

    private UnoCard.Color currentColorBeforeChallenge;

    private boolean hasPlayableCardExcludingWild4(Player p, UnoCard.Color activeColor) {
        for (UnoCard c : p.hand) {
            if (c.type == UnoCard.Type.WILD_DRAW_FOUR) continue;
            if (c.canPlayOn(topCard, activeColor, 0, false)) return true;
        }
        return false;
    }

    // ============================================================
    //                       出牌（玩家+机器人公用）
    // ============================================================

    /** 尝试出牌（人类），可选选色（wild 已选过时） */
    private void tryPlayCard(int playerIdx, int handIdx, UnoCard.Color chosenColor) {
        Player p = players.get(playerIdx);
        if (handIdx < 0 || handIdx >= p.hand.size()) return;
        UnoCard c = p.hand.get(handIdx);
        // 人类打万能牌且尚未选色 → 先弹选色框，暂不出牌（选完由 onColorPicked 再调本方法带色出牌）
        if (c.isWild() && chosenColor == null) {
            wildCardHandIdx = handIdx;
            waitingForColor = true;
            boardPanel.repaint();
            if (!onlineMode) stopTurnTimer(); // 选色弹窗自带 15s，停掉回合计时
            promptColorPicker();
            return;
        }
        applyPlayCard(playerIdx, handIdx, chosenColor);
    }

    private void applyPlayCard(int playerIdx, int handIdx, UnoCard.Color chosenColor) {
        Player p = players.get(playerIdx);
        if (handIdx < 0 || handIdx >= p.hand.size()) return;
        UnoCard c = p.hand.remove(handIdx);
        boolean isHuman = (playerIdx == myPlayerIdx);
        // 记录出牌前颜色（用于质疑判定）
        UnoCard.Color beforeColor = currentColor;
        // 立即更新顶牌 / 颜色（让方向环立刻变色，飞行牌也是它本身）
        topCard = c;
        discardPile.add(c);
        if (c.isWild()) {
            currentColor = chosenColor != null ? chosenColor : pickBotColor(p);
        } else {
            currentColor = c.color;
        }
        selectedHandIdx = -1;
        p.pendingDrawsOnMe = 0;
        waitingForColor = false;
        // UNO 喊牌判定
        boolean goingToUno = (p.hand.size() == 1);
        if (goingToUno) {
            if (p.isBot) {
                p.showHammer = false;
                beep();
                showAction(p.name + " 喊了 UNO！");
                triggerUnoAnim(playerIdx);
            } else if (unoCalled) {
                p.showHammer = false;
            } else {
                p.showHammer = true;
                p.hammerShownAt = System.currentTimeMillis();
            }
        } else {
            p.showHammer = false;
        }
        // 启动飞行动画
        playingCard = c;
        playingDisplayColor = c.isWild() ? (chosenColor != null ? chosenColor : currentColor) : c.color;
        Rectangle tr = new Rectangle(PLAY_AREA_CX + 20, PLAY_AREA_CY - CARD_H / 2, CARD_W, CARD_H);
        playingToX = tr.x;
        playingToY = tr.y;
        if (isHuman) {
            // 玩家：从手牌对应 handIdx 出。handIdx 已被 remove，按 remove 前的 totalWidth 算位置
            int totalForPre = (handIdx + 1) * CARD_W - handIdx * (CARD_W - HAND_CARD_GAP);
            playingFromX = (W - totalForPre) / 2 + handIdx * (CARD_W - HAND_CARD_GAP);
            playingFromY = HAND_Y;
        } else {
            // 机器人：从头像位置出发
            Point pp = playerPositions[playerIdx];
            playingFromX = pp.x - CARD_W / 2;
            playingFromY = pp.y - CARD_H / 2 - 6;
        }
        playingCardAnim = true;
        playingStartMs = System.currentTimeMillis();
        turnAnimating = true;
        if (playAnimTimer != null && playAnimTimer.isRunning()) playAnimTimer.stop();
        playAnimTimer = new javax.swing.Timer(16, e -> {
            double t = (System.currentTimeMillis() - playingStartMs) / (double) playingDurationMs;
            if (t >= 1.0) {
                playingCardAnim = false;
                ((javax.swing.Timer) e.getSource()).stop();
                // 飞行完成 → 应用 effect
                applyCardEffect(c, playerIdx, beforeColor);
                if (p.hand.isEmpty()) {
                    p.finished = true;
                    if (winnerIdx < 0) winnerIdx = playerIdx;
                    // 任一玩家出完 → 立即结束游戏（按 finishRank 排名）
                    if (roomId == 0) {
                        javax.swing.Timer t3 = new javax.swing.Timer(220, e3 -> {
                            if (!gameOver) endGame();
                        });
                        t3.setRepeats(false);
                        t3.start();
                    }
                    return; // 出完牌的玩家不再 advanceTurn，让 endGame 接管
                }
                if (roomId == 0) {
                    // 短停顿后 advance
                    javax.swing.Timer t2 = new javax.swing.Timer(180, e2 -> {
                        turnAnimating = false;
                        if (!suppressAutoAdvance) advanceTurn();
                        suppressAutoAdvance = false;
                    });
                    t2.setRepeats(false);
                    t2.start();
                }
            }
            boardPanel.repaint();
        });
        playAnimTimer.start();
        boardPanel.repaint();
    }

    private void applyCardEffect(UnoCard c, int playerIdx, UnoCard.Color beforeColor) {
        pendingSkip = false;
        pendingReplay = false;
        switch (c.type) {
            case NUMBER: /* nothing */ break;
            case SKIP:
                pendingSkip = true;
                showAction(players.get(nextIdx(playerIdx, 1)).name + " 被跳过！");
                break;
            case REVERSE: {
                direction = -direction;
                reverseDesignMirrored = !reverseDesignMirrored; // 牌图左右镜像翻转
                ringSpinUntil = System.currentTimeMillis() + 500; // 换向冲击 0.5s：让方向变化肉眼可见
                if (players.size() <= 2) {
                    // 2 人：倒转＝跳过对方，出牌者再出一次
                    pendingReplay = true;
                    replayIdx = playerIdx;
                }
                // 3 人及以上：方向反转后，新方向的下家出牌（交给 advanceTurn 前进 1 步）
                break;
            }
            case DRAW_TWO:
                pendingDraws += 2;
                players.get(nextIdx(playerIdx, 1)).pendingDrawsOnMe = pendingDraws;
                break;
            case WILD:
                // 已选色；下家出牌（交给 advanceTurn）
                break;
            case WILD_DRAW_FOUR: {
                pendingDraws += 4;
                int next = nextIdx(playerIdx, 1);
                players.get(next).pendingDrawsOnMe = pendingDraws;
                if (roomId == 0 && next == myPlayerIdx) {
                    // 离线且被加 +4 的是人类 → 给 15 秒质疑窗口（与在线一致）
                    currentColorBeforeChallenge = currentColor;
                    challengeFromIdx = playerIdx;
                    waitingForChallenge = true;
                    suppressAutoAdvance = true; // 等质疑结果，timer 不再自动 advance
                    boolean ch = confirmTimeout("质疑 +4",
                            players.get(playerIdx).name + " 出了 +4（变色：" + colorCN(currentColor) + "）<br><br>"
                                    + "质疑成功：" + players.get(playerIdx).name + " 自加 4 张<br>"
                                    + "质疑失败：你加 6 张<br>"
                                    + "不质疑：你加 4 张",
                            "质疑", "不质疑", false);
                    onChallengeDecision(ch);
                }
                break;
            }
        }
    }

private void promptColorPicker() {
    // 自定义 4 色按钮选色对话框（红 / 黄 / 绿 / 蓝）
    final JDialog d = new JDialog(this, "选择颜色", true);
    d.setUndecorated(true);
    d.setSize(460, 130);
    d.setLocationRelativeTo(this);

    JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 22));
    p.setBackground(new Color(0x1b, 0x1b, 0x29));

    Color[] btnColors = { C_RED, C_YELLOW, C_GREEN, C_BLUE };
    UnoCard.Color[] cardColors = { UnoCard.Color.RED, UnoCard.Color.YELLOW, UnoCard.Color.GREEN, UnoCard.Color.BLUE };
    String[] names = { "红", "黄", "绿", "蓝" };
    final boolean[] done = {false};
    for (int i = 0; i < 4; i++) {
        final int idx = i;
        JButton btn = new JButton(names[i]);
        btn.setBackground(btnColors[i]);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(90, 60));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            if (done[0]) return; done[0] = true;
            d.dispose();
            onColorPicked(cardColors[idx]);
        });
        p.add(btn);
    }
    d.setContentPane(p);
    // 15 秒倒计时：超时自动选手牌最多色（平局随机）
    final int[] left = {15};
    final javax.swing.Timer t = new javax.swing.Timer(1000, e -> {
        if (!d.isVisible()) { ((javax.swing.Timer) e.getSource()).stop(); return; }
        left[0]--;
        if (left[0] <= 0) {
            if (!done[0]) { done[0] = true; d.dispose(); onColorPicked(mostHandColor()); }
            ((javax.swing.Timer) e.getSource()).stop();
        }
    });
    d.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosed(java.awt.event.WindowEvent e) { t.stop(); }
    });
    t.start();
    d.setVisible(true);
    t.stop();
}

    // ============================================================
    //                       机器人
    // ============================================================

    private void scheduleBotTurn(Player p) {
        if (botTimer != null && botTimer.isRunning()) botTimer.stop();
        long delay = 1300 + new Random().nextInt(1200); // 1.3s~2.5s，给玩家看清
        botTimer = new javax.swing.Timer((int) delay, e -> botPlay(p));
        botTimer.setRepeats(false);
        botTimer.start();
    }

    private void botPlay(Player p) {
        if (gameOver) return;
        // 1) 如果有 pendingDraws 但可以接（在逆转叠加模式下可以 REVERSE）
        if (pendingDraws > 0) {
            UnoCard stack = pickBotStack(p);
            if (stack != null) {
                int handIdx = p.hand.indexOf(stack);
                UnoCard.Color chosenColor = stack.isWild() ? pickBotColor(p) : null;
                if (stack.type == UnoCard.Type.WILD_DRAW_FOUR) {
                    currentColorBeforeChallenge = stack.color;
                }
                applyPlayCard(players.indexOf(p), handIdx, chosenColor);
                return;
            }
            // 接不了 → 强制吃（beginTurn 已处理）
            return;
        }
        // 2) 选可出的牌
        UnoCard chosen = pickBotPlay(p);
        if (chosen == null) {
            drawCards(p, 1);
            showAction(p.name + " 摸了 1 张");
            javax.swing.Timer t = new javax.swing.Timer(900, e -> advanceTurn());
            t.setRepeats(false);
            t.start();
            return;
        }
        int handIdx = p.hand.indexOf(chosen);
        UnoCard.Color chosenColor = chosen.isWild() ? pickBotColor(p) : null;
        if (chosen.type == UnoCard.Type.WILD_DRAW_FOUR) {
            currentColorBeforeChallenge = chosen.color;
        }
        applyPlayCard(players.indexOf(p), handIdx, chosenColor);
    }

    private UnoCard pickBotStack(Player p) {
        // 优先 +4（万能总能叠），其次 +2（叠 +2），最后 REVERSE（仅逆转叠加模式）
        for (UnoCard c : p.hand) if (c.type == UnoCard.Type.WILD_DRAW_FOUR) return c;
        for (UnoCard c : p.hand) if (c.type == UnoCard.Type.DRAW_TWO) return c;
        if (mode == 1) {
            for (UnoCard c : p.hand) {
                if (c.type == UnoCard.Type.REVERSE && c.color == currentColor) return c;
            }
        }
        return null;
    }

    private UnoCard pickBotPlay(Player p) {
        UnoCard bestAction = null;
        UnoCard firstNumber = null;
        for (UnoCard c : p.hand) {
            if (!c.canPlayOn(topCard, currentColor, 0, false)) continue;
            if (c.type != UnoCard.Type.NUMBER) {
                // 优先 +2/+4 > 跳过/反转 > 变色
                if (bestAction == null) bestAction = c;
                else if (priority(c) > priority(bestAction)) bestAction = c;
            } else if (firstNumber == null) {
                firstNumber = c;
            }
        }
        if (bestAction != null) return bestAction;
        return firstNumber;
    }

    private int priority(UnoCard c) {
        switch (c.type) {
            case WILD_DRAW_FOUR: return 5;
            case DRAW_TWO: return 4;
            case WILD: return 3;
            case SKIP: return 2;
            case REVERSE: return 1;
            default: return 0;
        }
    }

    private UnoCard.Color pickBotColor(Player p) {
        int[] cnt = new int[4];
        for (UnoCard c : p.hand) {
            if (c.isWild() || c.type == UnoCard.Type.NUMBER) {
                switch (c.color) {
                    case RED: cnt[0]++; break;
                    case YELLOW: cnt[1]++; break;
                    case GREEN: cnt[2]++; break;
                    case BLUE: cnt[3]++; break;
                    default: break;
                }
            }
        }
        int best = 0, max = -1;
        for (int i = 0; i < 4; i++) if (cnt[i] > max) { max = cnt[i]; best = i; }
        return UnoCard.Color.values()[best];
    }

    // ============================================================
    //                       UNO 喊牌 + 锤子
    // ============================================================

    private void onUnoButtonPressed() {
        if (gameOver) return;
        Player me = players.get(myPlayerIdx);
        if (currentPlayerIdx != myPlayerIdx) {
            showAction("还没轮到你");
            return;
        }
        if (me.hand.size() != 2) {
            showAction("只剩 1 张时不用喊 UNO；要在打倒数第二张牌前（剩 2 张时）点");
            return;
        }
        // 剩 2 张时点 = 预告即将出到 1 张；出牌时不再被抓
        unoCalled = true;
        me.showHammer = false;
        beep();
        showAction("你喊了 UNO！");
        triggerUnoAnim(myPlayerIdx);
        if (onlineMode) sendOnlineCmd(() -> ServerClient.unoCallUno(roomId, myUsername), null);
        boardPanel.repaint();
    }

    /** 启动 UNO 喊牌大动画：屏幕中心大 UNO 缩小飞到 callerIdx 头像 */
    private void triggerUnoAnim(int callerIdx) {
        if (callerIdx < 0 || callerIdx >= players.size()) return;
        unoCallAnim = true;
        unoCallStartMs = System.currentTimeMillis();
        unoCallFromIdx = callerIdx;
    }

    private void checkHammerExpiry() {
        // 在线由服务端权威推送驱动；离线锤子持续显示直到被抓 / 轮到该玩家 / 其出牌才消失（与在线一致），不做强制过期
    }

    void onHammerClicked(int targetIdx) {
        if (gameOver) return;
        if (targetIdx == myPlayerIdx) return;
        Player target = players.get(targetIdx);
        if (!target.showHammer) return;
        if (onlineMode) {
            final String tname = target.name;
            target.showHammer = false;
            beep(); beep();
            showAction("抓到 " + tname + " 没喊 UNO！+2张");
            // 在线模式让服务端真正执行扣牌（其内部会校验窗口与状态，避免误触发）
            Thread th = new Thread(() -> {
                String r = ServerClient.unoCatch(roomId, myUsername, tname);
                SwingUtilities.invokeLater(() -> {
                    if (r == null || !r.startsWith("SUCCESS")) {
                        showAction(errText(r, "抓 UNO 失败"));
                    }
                    boardPanel.repaint();
                });
            });
            th.setDaemon(true);
            th.start();
            boardPanel.repaint();
            return;
        }
        drawCards(target, 2);
        target.showHammer = false;
        if (targetIdx == myPlayerIdx) unoCalled = false; // 被抓后手牌变多，旧标记作废
        beep(); beep();
        JOptionPane.showMessageDialog(this,
                myUsername + " 发现 " + target.name + " 没喊 UNO！！！\n" + target.name + " +2 张",
                "抓 UNO", JOptionPane.INFORMATION_MESSAGE);
        boardPanel.repaint();
    }

    // ============================================================
    //                       倒计时 / 结束 / 计分
    // ============================================================

    private long remainingMs() {
        if (onlineMode && srvMatchDurationMs > 0 && srvMatchStartMs > 0) {
            return Math.max(0, srvMatchStartMs + srvMatchDurationMs - System.currentTimeMillis());
        }
        return Math.max(0, gameDurationMs - (System.currentTimeMillis() - startTime));
    }

    private void onTick() {
        if (gameOver) return;
        if (onlineMode) return; // 在线：结束由服务端 DUEL_GAME_OVER 判定
        if (remainingMs() <= 0) {
            endGame();
        }
    }

    private void endGame() {
        if (gameOver) return;
        gameOver = true;
        countdownTimer.stop();
        if (botTimer != null) botTimer.stop();
        stopTurnTimer();
        hammerCheckTimer.stop();
        // 计算排名
        // 已出完的：按 finishRank（按出完顺序 1,2,3...）；未出完的：按手牌面值和升序
        List<Player> order = new ArrayList<>(players);
        // 先把 winnerIdx 的人放第一
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).finished) {
                // finished 玩家：按手牌数（0=第一名）
            }
        }
        order.sort((a, b) -> {
            if (a.finished && b.finished) return Integer.compare(a.hand.size(), b.hand.size());
            if (a.finished) return -1;
            if (b.finished) return 1;
            int sa = handValue(a), sb = handValue(b);
            if (sa != sb) return Integer.compare(sa, sb);
            return Integer.compare(a.hand.size(), b.hand.size());
        });
        for (int i = 0; i < order.size(); i++) {
            order.get(i).finishRank = i + 1;
        }
        // 弹排名 + 计分
        showRankingDialog(order);
    }

    private int handValue(Player p) {
        int s = 0;
        for (UnoCard c : p.hand) s += c.scoreValue();
        return s;
    }

    private void showRankingDialog(List<Player> order) {
        StringBuilder sb = new StringBuilder("游戏结束！\n\n最终排名：\n");
        for (int i = 0; i < order.size(); i++) {
            Player p = order.get(i);
            sb.append(i + 1).append(". ").append(p.name);
            if (p.finished) sb.append(" (已出完)");
            else sb.append(" (手牌 ").append(p.hand.size()).append(" 张，扣 ").append(handValue(p)).append(" 分)");
            sb.append("\n");
        }
        sb.append("\n手牌扣分规则：\n数字牌=面值，功能牌=20，万能=50");
        JOptionPane.showMessageDialog(this, sb.toString(), "UNO 排名", JOptionPane.INFORMATION_MESSAGE);
        // 关窗返回
        cleanup();
        if (onCloseCallback != null) onCloseCallback.run();
        dispose();
    }

    private void cleanup() {
        if (countdownTimer != null) countdownTimer.stop();
        if (botTimer != null) botTimer.stop();
        if (hammerCheckTimer != null) hammerCheckTimer.stop();
        if (spinTimer != null) spinTimer.stop();
        if (playAnimTimer != null) playAnimTimer.stop();
        if (onlinePollTimer != null) onlinePollTimer.stop();
        stopTurnTimer();
        if (onlineMode) activeGames.remove(roomId, this);
    }

    // ============================================================
    //                       提示
    // ============================================================

    private void showAction(String s) {
        lastActionText = s;
        lastActionAt = System.currentTimeMillis();
    }

    private static String colorCN(UnoCard.Color c) {
        switch (c) {
            case RED: return "红";
            case YELLOW: return "黄";
            case GREEN: return "绿";
            case BLUE: return "蓝";
            case BLACK: return "黑";
            default: return "?";
        }
    }

    private void beep() {
        try { Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
    }

    // ============================================================
    //                       绘制
    // ============================================================

    private Point[] playerPositions;

    private void computePlayerPositions() {
        if (playerPositions != null && playerPositions.length == players.size()) return;
        playerPositions = new Point[players.size()];
        // 自己固定在底部中央；其他按 turn 顺序切成「上 / 左 / 右」三段
        int n = players.size();
        int others = n - 1;
        // 8→上3左2右2；7→上2左2右2；6→上2左2右1；5→上2左1右1；4→上1左1右1；3→上1左0右1；2→上0左0右1
        int topN, leftN, rightN;
        switch (others) {
            case 7: topN = 3; leftN = 2; rightN = 2; break;
            case 6: topN = 2; leftN = 2; rightN = 2; break;
            case 5: topN = 2; leftN = 2; rightN = 1; break;
            case 4: topN = 2; leftN = 1; rightN = 1; break;
            case 3: topN = 1; leftN = 1; rightN = 1; break;
            case 2: topN = 1; leftN = 0; rightN = 1; break;
            case 1: topN = 0; leftN = 0; rightN = 1; break;
            default: topN = 0; leftN = 0; rightN = 0; break;
        }
        // 填充顺序 = 沿屏幕「逆时针」读一圈的顺序（player 在底）：
        //   左列 [下→上] → 顶行 [左→中→右] → 右列 [上→下] → 回到 player
        // 这样沿这个方向读下来就是 turn 顺序：玩家 → 1 → 2 → … → N-1 → 玩家
        int[] slot = new int[others];
        String[] region = new String[others];
        int idx = 0;
        for (int s = 0; s < leftN;  s++) { slot[idx] = s; region[idx++] = "left";  }
        for (int s = 0; s < topN;   s++) { slot[idx] = s; region[idx++] = "top";   }
        for (int s = 0; s < rightN; s++) { slot[idx] = s; region[idx++] = "right"; }
        for (int i = 0; i < n; i++) {
            if (i == myPlayerIdx) {
                playerPositions[i] = new Point(PLAY_AREA_CX, PLAY_AREA_CY + PLAYER_R + 30);
                players.get(i).seatRegion = "self";
            } else {
                int orderIdx;
                if (i > myPlayerIdx) orderIdx = i - myPlayerIdx - 1;
                else                 orderIdx = i + (n - myPlayerIdx - 1);
                int regionSize = "top".equals(region[orderIdx]) ? topN
                                : "left".equals(region[orderIdx]) ? leftN : rightN;
                playerPositions[i] = seatForRegion(slot[orderIdx], regionSize, region[orderIdx]);
                players.get(i).seatRegion = region[orderIdx];
            }
        }
    }

    /** 在「上 / 左 / 右」某个区段内，按区段内编号均匀分布 */
    private Point seatForRegion(int slot, int regionSize, String region) {
        int x, y;
        if ("top".equals(region)) {
            // 顶段整体下移到 y=110，避开顶栏；水平跨度收窄到 500，让两端头像往中央靠，
            // 避免牌背堆向左/右段 slot=0 的头像 y 方向撞到
            y = 110;
            int spanX = 500;
            if (regionSize <= 1) x = PLAY_AREA_CX;
            else                 x = PLAY_AREA_CX - spanX / 2 + slot * spanX / (regionSize - 1);
        } else if ("left".equals(region)) {
            // 左列：逆 slot 序号 → 从下往上（slot 0 = 靠玩家下端，slot 1 = 远离玩家上端）。
            // 沿桌读下来 slot=0 先（上一步=player）→ slot=1，符合 turn 顺序 1→2。
            x = 200;
            int spanY = 130;
            if (regionSize <= 1) y = PLAY_AREA_CY + 50;
            else                 y = PLAY_AREA_CY + 50 + spanY / 2 - slot * spanY / (regionSize - 1);
        } else { // right
            // 右列：与左列镜像 — 从上往下（slot 0 = 远离玩家上端，slot 1 = 靠玩家下端）。
            // 沿桌从顶段右端读下来时先遇到右列上端，对应 turn 顺序靠前的那个人（机器人6），
            // 再到右列下端（机器人7），与左列 slot 序号语义相反。
            x = 800;
            int spanY = 130;
            if (regionSize <= 1) y = PLAY_AREA_CY + 50;
            else                 y = PLAY_AREA_CY + 50 - spanY / 2 + slot * spanY / (regionSize - 1);
        }
        return new Point(x, y);
    }

    private class BoardPanel extends JPanel {
        BoardPanel() {
            setBackground(BG);
            setLayout(null);
            // 鼠标点击：hand 选牌 / draw pile 摸牌 / 头像点锤子
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (gameOver) return;
                    int mx = e.getX(), my = e.getY();
                    // 摸牌堆
                    if (drawPileRect().contains(mx, my)) {
                        onDrawClicked();
                        return;
                    }
                    // 锤子按钮（优先于头像圆，避免点不准）
                    for (int i = 0; i < players.size(); i++) {
                        Player pp1 = players.get(i);
                        if (pp1.showHammer && pp1.hammerHit != null && pp1.hammerHit.contains(mx, my)) {
                            onHammerClicked(i);
                            return;
                        }
                    }
                    // 头像
                    for (int i = 0; i < players.size(); i++) {
                        Point pp = playerPositions[i];
                        Rectangle avatarRect = new Rectangle(pp.x - AVATAR_R, pp.y - AVATAR_R, AVATAR_R * 2, AVATAR_R * 2);
                        if (avatarRect.contains(mx, my)) {
                            if (i == myPlayerIdx) continue;
                            onHammerClicked(i);
                            return;
                        }
                    }
                    // 手牌（从右往左，保证叠在最上面的牌优先命中）
                    Rectangle[] hr = handCardRects();
                    for (int i = hr.length - 1; i >= 0; i--) {
                        if (hr[i] != null && hr[i].contains(mx, my)) {
                            onHandCardClicked(i);
                            return;
                        }
                    }
                }
            });
        }

        private Rectangle drawPileRect() {
            return new Rectangle(PLAY_AREA_CX - CARD_W - 20, PLAY_AREA_CY - CARD_H / 2, CARD_W, CARD_H);
        }

        private Rectangle discardRect() {
            return new Rectangle(PLAY_AREA_CX + 20, PLAY_AREA_CY - CARD_H / 2, CARD_W, CARD_H);
        }

        private Rectangle[] handCardRects() {
            Player me = players.get(myPlayerIdx);
            int n = me.hand.size();
            int gap = HAND_CARD_GAP;
            int totalW = n * CARD_W - (n - 1) * (CARD_W - gap);
            int x0 = (W - totalW) / 2;
            Rectangle[] rs = new Rectangle[n];
            for (int i = 0; i < n; i++) {
                int x = x0 + i * (CARD_W - gap);
                int y = HAND_Y + ((i == selectedHandIdx) ? -10 : 0);
                rs[i] = new Rectangle(x, y, CARD_W, CARD_H);
            }
            return rs;
        }

        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            computePlayerPositions();
            // 顶栏
            drawTopBar(g);
            // 玩家头像（圆形）+ 手牌数 + 锤子
            for (int i = 0; i < players.size(); i++) {
                drawPlayer(g, i);
            }
            // 中央：方向环（顺/逆时针，颜色 = 当前出牌色）
            drawDirectionRing(g);
            // 中央：摸牌堆 / 弃牌堆
            drawDrawPile(g);
            drawDiscard(g);
            // 中央提示
            drawActionText(g);
            // 自己的手牌
            drawHand(g);
            // 出牌动画（覆盖在最上层）
            if (playingCardAnim) {
                drawPlayingCardAnim(g);
            }
            // 倒计时在顶栏已画
            // 累加提示
            drawPendingBadge(g);
            // 起手变色提示
            if (waitingForColor && wildCardHandIdx == -1) {
                String s = "起手为变色牌，请选择当前颜色";
                g.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
                int w = g.getFontMetrics().stringWidth(s);
                g.setColor(YELLOW);
                g.drawString(s, (W - w) / 2, 90);
            }
            // UNO 喊牌大动画（最上层）
            if (unoCallAnim) {
                drawUnoCallAnim(g);
            }
        }

        /** UNO 喊牌大动画：从屏幕中心大 UNO 缩小飞到 caller 头像 */
        private void drawUnoCallAnim(Graphics2D g) {
            long elapsed = System.currentTimeMillis() - unoCallStartMs;
            double total = 1400.0; // 总时长 1.4s
            double t = elapsed / total;
            if (t >= 1.0) {
                unoCallAnim = false;
                return;
            }
            int callerIdx = unoCallFromIdx;
            int toX, toY;
            if (callerIdx >= 0 && callerIdx < players.size()) {
                Point pp = playerPositions[callerIdx];
                toX = pp.x;
                toY = pp.y;
            } else {
                toX = W / 2;
                toY = H / 2;
            }
            int fromX = W / 2, fromY = H / 2;
            // 字号：0..0.55 维持 180px（巨大），0.55..1 缩到 28px
            double scalePhase = Math.min(1.0, Math.max(0.0, (t - 0.55) / 0.45));
            double sizeEase = 1 - Math.pow(1 - scalePhase, 2.0);
            int fontSize = (int) Math.round(180 - (180 - 28) * sizeEase);
            // 位置：前 55% 停在中心微微脉动；后 45% 飞向 caller
            int cx, cy;
            if (t < 0.55) {
                // 微微脉动一下
                double pulse = 1.0 + 0.06 * Math.sin(t * Math.PI * 4);
                cx = fromX;
                cy = fromY;
                _unoDrawBigUNO(g, cx, cy, fontSize, 1.0, pulse);
            } else {
                double flyPhase = (t - 0.55) / 0.45;
                double flyEase = flyPhase * flyPhase; // ease-in
                cx = (int) (fromX + (toX - fromX) * flyEase);
                cy = (int) (fromY + (toY - fromY) * flyEase);
                double alpha = 1.0 - 0.4 * flyPhase; // 飞的过程轻微淡出
                _unoDrawBigUNO(g, cx, cy, fontSize, alpha, 1.0);
            }
        }

        private void _unoDrawBigUNO(Graphics2D g, int cx, int cy, int fontSize, double alpha, double scale) {
            Font font = new Font("Arial Black", Font.BOLD, fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            String s = "UNO";
            int w = fm.stringWidth(s);
            int h = fm.getAscent();
            int bx = cx - (int) (w * scale / 2);
            int by = cy + (int) (h * scale / 2) - (int) (10 * scale);
            // 暗影
            g.setColor(new Color(0, 0, 0, (int) (110 * alpha)));
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    if (dx * dx + dy * dy <= 9 && (dx != 0 || dy != 0)) {
                        g.drawString(s, bx + dx, by + dy);
                    }
                }
            }
            // 黄色字 + 红色描边
            g.setColor(new Color(0xc8, 0x18, 0x1c, (int) (255 * alpha)));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx * dx + dy * dy <= 4 && (dx != 0 || dy != 0)) {
                        g.drawString(s, bx + dx, by + dy);
                    }
                }
            }
            g.setColor(new Color(0xff, 0xd6, 0x12, (int) (255 * alpha)));
            g.drawString(s, bx, by);
        }

        private void drawTopBar(Graphics2D g) {
            // 顶部条
            g.setColor(PANEL_BG);
            g.fillRect(0, 0, W, 50);
            // 游戏总倒计时 + 手绘小时钟图标（替代不显示的 emoji）
            long ms = remainingMs();
            int min = (int)(ms / 60000);
            int sec = (int)((ms % 60000) / 1000);
            String ts = String.format("%02d:%02d", min, sec);
            Color timeCol = ms < 30000 ? C_RED : YELLOW;
            // 时钟图标：圆心 (31,25) 半径 11
            int clx = 31, cly = 25, clr = 11;
            g.setColor(timeCol);
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(clx - clr, cly - clr, clr * 2, clr * 2);
            // 指针（短针指向 10 点方向，长针指向 2 点方向）
            double a1 = Math.toRadians(150), a2 = Math.toRadians(-60);
            g.drawLine(clx, cly, (int)(clx + Math.cos(a1) * clr * 0.55), (int)(cly - Math.sin(a1) * clr * 0.55));
            g.drawLine(clx, cly, (int)(clx + Math.cos(a2) * clr * 0.85), (int)(cly - Math.sin(a2) * clr * 0.85));
            g.fillOval(clx - 1, cly - 1, 3, 3);
            // 时间文本
            g.setFont(new Font("Consolas", Font.BOLD, 26));
            g.drawString(ts, 47, 36);
            // 当前回合出牌倒计时（仅在线、服务端提供截止时间时显示）
            if (!gameOver && srvTurnDeadline > 0) {
                long rem = srvTurnDeadline - System.currentTimeMillis();
                if (rem > 0) {
                    int rs = (int) Math.ceil(rem / 1000.0);
                    String tts = "出牌 " + rs + "s";
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
                    g.setColor(rem < 5000 ? C_RED : new Color(0x9b, 0xe3, 0x6b));
                    int tx = 47 + g.getFontMetrics().stringWidth(ts) + 30;
                    g.drawString(tts, tx, 36);
                }
            }
            // 模式
            g.setColor(FG);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            String modeStr = "模式：" + (mode == 1 ? "逆转叠加" : "普通叠加") + "  |  人数：" + players.size();
            int w = g.getFontMetrics().stringWidth(modeStr);
            g.drawString(modeStr, (W - w) / 2, 30);
            // 自己
            g.setColor(FG);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            String me = "我：" + myUsername;
            g.drawString(me, W - g.getFontMetrics().stringWidth(me) - 20, 30);
        }

        private void drawPlayer(Graphics2D g, int idx) {
            Player p = players.get(idx);
            Point pp = playerPositions[idx];
            int ax = pp.x, ay = pp.y;
            // 背景圆（浅色）
            boolean active = (idx == currentPlayerIdx) && !gameOver;
            g.setColor(avatarBg(p.avatarSeed));
            g.fillOval(ax - AVATAR_R, ay - AVATAR_R, AVATAR_R * 2, AVATAR_R * 2);
            // 轮到该玩家：头像高闪 + 一闪一闪
            if (active) {
                double pulse = (Math.sin(System.currentTimeMillis() / 220.0) + 1) / 2; // 0..1
                // 外发光圈（亮黄，随 pulse 呼吸）
                g.setColor(new Color(255, 230, 90, (int) (110 + 120 * pulse)));
                g.setStroke(new BasicStroke(5f));
                g.drawOval(ax - AVATAR_R - 5, ay - AVATAR_R - 5, (AVATAR_R + 5) * 2, (AVATAR_R + 5) * 2);
                g.setStroke(new BasicStroke(1));
                // 头像高亮层（白色半透明，随 pulse 明暗）
                g.setColor(new Color(255, 255, 255, (int) (80 * pulse)));
                g.fillOval(ax - AVATAR_R, ay - AVATAR_R, AVATAR_R * 2, AVATAR_R * 2);
            }
            // 边框
            g.setColor(active ? new Color(0xff, 0xdc, 0x3c) : new Color(0x55, 0x55, 0x66));
            g.setStroke(new BasicStroke(active ? 3 : 1.5f));
            g.drawOval(ax - AVATAR_R, ay - AVATAR_R, AVATAR_R * 2, AVATAR_R * 2);
            g.setStroke(new BasicStroke(1));
            // 名字位置按 region 决定，避免重叠：
            //   top  → 头像正下方（避开顶栏）
            //   left → 头像左侧（水平居中），与左段其他玩家的牌背完全错开
            //   right → 头像右侧（水平居中），与右段其他玩家的牌背完全错开
            //   self → 头像上方（兜底，本人类极少走到这里）
            g.setColor(FG);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            String nm = p.name.length() > 5 ? p.name.substring(0, 5) : p.name;
            int nw = g.getFontMetrics().stringWidth(nm);
            String reg = p.seatRegion == null ? "self" : p.seatRegion;
            if ("top".equals(reg)) {
                int nameY = ay + AVATAR_R + 15;
                g.drawString(nm, ax - nw / 2, nameY);
            } else if ("left".equals(reg)) {
                // 头像左侧：名字右端结束在头像左边外 8px，所以基准 x = ax - AVATAR_R - 8 - nw
                int baseX = ax - AVATAR_R - 8 - nw;
                int nameY = ay + 5; // 微微偏下以对齐头像视觉中心
                g.drawString(nm, baseX, nameY);
            } else if ("right".equals(reg)) {
                // 头像右侧：名字左端开始于头像右边外 8px
                int nameY = ay + 5;
                g.drawString(nm, ax + AVATAR_R + 8, nameY);
            } else {
                // self / 兜底：保持头像上方
                int nameY = ay - AVATAR_R - 8;
                g.drawString(nm, ax - nw / 2, nameY);
            }
            // （上面用到 nw_temp 是为兼容旧路径，无副作用）
            // 手牌数（圆内，浅色头像用深色字）
            String cnt = p.finished ? "完" : String.valueOf(p.hand.size());
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
            int cw = g.getFontMetrics().stringWidth(cnt);
            int ch = g.getFontMetrics().getAscent();
            g.setColor(DARK_FG);
            g.drawString(cnt, ax - cw / 2, ay + ch / 2);
            // 锤子（抓 UNO 大按钮）：持续高闪，绝不消失；被抓玩家自己看不到
            if (p.showHammer && idx != myPlayerIdx) {
                long elapsed = System.currentTimeMillis() - p.hammerShownAt;
                // 平滑高频脉动（约 220ms 一轮），亮度在亮红↔更亮红间来回，始终不透明
                double t = (elapsed % 220) / 220.0;
                double pulse = 0.5 + 0.5 * Math.sin(t * 2 * Math.PI); // 0..1
                int radius = 26;
                int hx = ax + AVATAR_R - 18;
                int hy = ay + AVATAR_R - 18;
                // 外发光（脉动增强"高闪"观感）
                g.setColor(new Color(0xff, 0x66, 0x66, (int) (12 + 16 * pulse)));
                g.fillOval(hx - 5, hy - 5, radius * 2 + 10, radius * 2 + 10);
                // 阴影
                g.setColor(new Color(0, 0, 0, 90));
                g.fillOval(hx + 2, hy + 3, radius * 2, radius * 2);
                // 红色按钮本体（亮度脉动，始终不透明，不会出现一会消失一会）
                int rr = (int) Math.min(255, 0xe6 + (0xff - 0xe6) * pulse);
                int gg = (int) Math.min(255, 0x39 + (0x70 - 0x39) * pulse);
                int bb = (int) Math.min(255, 0x46 + (0x70 - 0x46) * pulse);
                g.setColor(new Color(rr, gg, bb));
                g.fillOval(hx, hy, radius * 2, radius * 2);
                // 白色描边
                g.setStroke(new BasicStroke(2.5f));
                g.setColor(Color.WHITE);
                g.drawOval(hx, hy, radius * 2, radius * 2);
                g.setStroke(new BasicStroke(1f));
                // 文字"抓!"
                g.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
                String lbl = "抓!";
                int lw = g.getFontMetrics().stringWidth(lbl);
                int lh = g.getFontMetrics().getAscent();
                g.setColor(Color.WHITE);
                g.drawString(lbl, hx + radius - lw / 2, hy + radius + lh / 2 - 2);
                // 记录按钮命中区（点击事件用）
                p.hammerHit = new Rectangle(hx, hy, radius * 2, radius * 2);
            } else {
                p.hammerHit = null;
            }
            // 对手牌背堆（玩家眼里都是牌背）：有几张显示几张
            //   大小按 CARD_W×CARD_H 比例缩放 + 重叠，让每张牌背图案可读
            if (idx != myPlayerIdx && !p.finished) {
                double scale = 0.44;
                int miniW = (int) Math.round(CARD_W * scale); // 28
                int miniH = (int) Math.round(CARD_H * scale); // 42
                int overlap = Math.round(miniW * 0.65f);       // 18 → 每张露 10px
                int showCnt = p.hand.size();                   // 全部显示，不再截断
                int totalW = showCnt > 0 ? miniW + (showCnt - 1) * (miniW - overlap) : 0;
                int baseX = ax - totalW / 2;
                // 顶段名在头像下方需要让出 30px；左/右段名已经水平偏移到头像侧面，牌背紧贴下方即可
                int baseY = ay + AVATAR_R + ("top".equals(p.seatRegion) ? 44 : 10);
                for (int i = 0; i < showCnt; i++) {
                    int mx = baseX + i * (miniW - overlap);
                    int my = baseY;
                    // 暗色阴影 + 牌背（用同款 drawCardBack 的极简版）
                    g.setColor(new Color(0x10, 0x10, 0x14));
                    g.fillRoundRect(mx - 1, my - 1, miniW, miniH, 5, 5);
                    drawCardBackMini(g, mx, my, miniW, miniH);
                }
            }
        }

        private Color avatarBg(int seed) {
            Color[] cs = { new Color(0xbf, 0xd8, 0xf2), new Color(0xf2, 0xcf, 0xe4), new Color(0xc9, 0xe8, 0xd2),
                    new Color(0xf7, 0xe6, 0xb8), new Color(0xd8, 0xcf, 0xf0), new Color(0xc7, 0xec, 0xec) };
            return cs[Math.abs(seed) % cs.length];
        }

        /**
         * 方向环：环绕出牌堆和摸牌堆的圆，颜色 = 当前出牌色（currentColor）。
         * 两段弧 + 各自的两个端点（共 4 个箭头），箭头尖朝「转动方向」（切向：
         * 顺时针时整圈顺时针指、逆时针时整圈逆时针指）。
         * 弧位置由 basePhase = ringSpin - π/2 控制 → 整条环在自转。
         */
        private Path2D ringArcTpl; // 开口弧（局部坐标），两条弧同样长度

        private static final double ARC_SPAN = Math.PI * 0.72; // ≈ 130°

        private void ensureRingTemplates() {
            if (ringArcTpl != null) return;
            ringArcTpl = buildArcTpl(ARC_SPAN);
        }

        private static Path2D buildArcTpl(double span) {
            Path2D p = new Path2D.Double();
            int segs = 40;
            double l = span / 2, r = -span / 2;
            for (int i = 0; i <= segs; i++) {
                double t = l + (r - l) * i / segs;
                double x = Math.cos(t), y = Math.sin(t);
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            return p;
        }

        private void drawDirectionRing(Graphics2D g) {
            int cx = PLAY_AREA_CX;
            int cy = PLAY_AREA_CY;
            int r = 100;
            Color col = currentColor.awtColor();
            ensureRingTemplates();

            // 环位置跟着 ringSpin 自转；两条弧长度不同 → 打破 180° 旋转对称，转向肉眼可辨
            double basePhase = ringSpin - Math.PI / 2;

            g.setColor(col);
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // 两条弧等长，各两端一个箭头（共 4 个），尖朝「环实际转动方向」
            // 关键：sx 始终 = +r，不做水平镜像。镜像会让切向公式被抵消成「上下反射」
            // 而非 180° 旋转，导致倒转后箭头方向看着没变。direction 仅控制自转方向与箭头切向。
            double sx = r;
            double sy = r;
            double tipLen = 0.17, halfW = 0.11;

            for (int k = 0; k < 2; k++) {
                double phase = basePhase + k * (ARC_SPAN + (Math.PI * 2 - 2 * ARC_SPAN) / 2);

                // 同一 tf：translate(cx,cy) → scale(±r,r) → rotate(phase)
                AffineTransform tf = new AffineTransform();
                tf.translate(cx, cy);
                tf.scale(sx, sy);
                tf.rotate(phase);

                g.draw(ringArcTpl.createTransformedShape(tf));

                // 弧的两个端点（模板角 ±ARC_SPAN/2），旋转到 phase 后绝对角度 A = ±ARC_SPAN/2 + phase
                double[] tEnds = { ARC_SPAN / 2, -ARC_SPAN / 2 };
                for (int e = 0; e < 2; e++) {
                    double A = tEnds[e] + phase;
                    double ex = cx + sx * Math.cos(A);
                    double ey = cy + sy * Math.sin(A);
                    double tvx = direction * (-sx * Math.sin(A));
                    double tvy = direction * ( sy * Math.cos(A));
                    double tn = Math.hypot(tvx, tvy);
                    if (tn > 1e-9) { tvx /= tn; tvy /= tn; }

                    double tipX = ex + tvx * tipLen * r;
                    double tipY = ey + tvy * tipLen * r;
                    double nrmX = -tvy * halfW * r;
                    double nrmY =  tvx * halfW * r;
                    g.fillPolygon(new int[] {
                            (int) Math.round(tipX),
                            (int) Math.round(ex + nrmX),
                            (int) Math.round(ex - nrmX)
                    }, new int[] {
                            (int) Math.round(tipY),
                            (int) Math.round(ey + nrmY),
                            (int) Math.round(ey - nrmY)
                    }, 3);
                }
            }

            g.setStroke(new BasicStroke(1));
        }

        private void drawDrawPile(Graphics2D g) {
            Rectangle r = drawPileRect();
            // 牌背堆叠效果
            g.setColor(new Color(0x20, 0x20, 0x28));
            g.fillRoundRect(r.x - 3, r.y - 3, r.width, r.height, 8, 8);
            g.setColor(C_BACK);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g.setColor(FG);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            String s = "摸牌";
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, r.x + (r.width - sw) / 2, r.y + r.height / 2 + 4);
            // 在线模式 drawPile 不维护（服务端权威），不画误导性的 0 张

            // 轮到我且手里没有任何能出的牌 → 摸牌堆高亮闪烁
            if (myTurnNoPlayable()) {
                float b = blink();
                // 外发光描边：120..255 alpha
                g.setColor(new Color(BLINK_GLOW.getRed(), BLINK_GLOW.getGreen(), BLINK_GLOW.getBlue(), (int) (120 + 135 * b)));
                g.setStroke(new BasicStroke(4f));
                g.drawRoundRect(r.x - 6, r.y - 6, r.width + 12, r.height + 12, 12, 12);
                // 柔和发光填充：40..90 alpha
                g.setColor(new Color(BLINK_GLOW.getRed(), BLINK_GLOW.getGreen(), BLINK_GLOW.getBlue(), (int) (40 + 50 * b)));
                g.fillRoundRect(r.x - 6, r.y - 6, r.width + 12, r.height + 12, 12, 12);
                g.setStroke(new BasicStroke(1));
            }
        }

        private void drawDiscard(Graphics2D g) {
            Rectangle r = discardRect();
            drawCard(g, r.x, r.y, topCard, currentColor, false);
        }

        /**
         * 绘制一张牌。设计参考 UNO 官方：
         *  - 普通牌（数字/+2/SKIP/REVERSE）：白底圆角、彩色椭圆占据中央、中央白色大字符、左上/右下小角标
         *  - +4 / WILD：黑底圆角、中央 2×2 四色方块（红黄/蓝绿）+ 白边；+4 中央覆盖 "+4" 字
         *  - 牌背 faceDown=true：红/黑对角 + 中央 "UNO" 黄色字
         */
        private void drawCard(Graphics2D g, int x, int y, UnoCard c, UnoCard.Color displayColor, boolean faceDown) {
            // 牌背
            if (faceDown) {
                drawCardBack(g, x, y);
                return;
            }

            // 1) 卡牌底色（白 / 黑）
            boolean wildCard = (c.isWild());
            Color cardBg = wildCard ? C_BLACK : Color.WHITE;
            g.setColor(cardBg);
            g.fillRoundRect(x, y, CARD_W, CARD_H, 10, 10);

            // 2) 边框
            if (wildCard) {
                g.setColor(Color.WHITE);
            } else {
                g.setColor(displayColor.awtColor());
            }
            g.setStroke(new BasicStroke(2.2f));
            g.drawRoundRect(x, y, CARD_W, CARD_H, 10, 10);
            g.setStroke(new BasicStroke(1));

            if (wildCard) {
                if (c.type == UnoCard.Type.WILD_DRAW_FOUR) {
                    drawPlus4Design(g, x, y);
                } else {
                    drawWildDesign(g, x, y);
                }
                return;
            }

            // 3) 彩色椭圆（占卡牌大部分区域）
            int ovalW = (int) (CARD_W * 0.86);
            int ovalH = (int) (CARD_H * 0.80);
            int ovalX = x + (CARD_W - ovalW) / 2;
            int ovalY = y + (CARD_H - ovalH) / 2;
            g.setColor(displayColor.awtColor());
            g.fillOval(ovalX, ovalY, ovalW, ovalH);

            // 4) 椭圆中央：SKIP/REVERSE 走自定义绘制，其余画字符
            String s = c.displayChar();
            if (c.type == UnoCard.Type.SKIP) {
                drawSkip(g, x, y, Color.WHITE);
            } else if (c.type == UnoCard.Type.REVERSE) {
                drawReverse(g, x, y, Color.WHITE, reverseDesignMirrored);
            } else {
                g.setColor(Color.WHITE);
                g.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
                FontMetrics fm = g.getFontMetrics();
                int sw = fm.stringWidth(s);
                int sh = fm.getAscent();
                int cx0 = x + (CARD_W - sw) / 2;
                int cy0 = y + CARD_H / 2 + sh / 2 - 5;
                g.drawString(s, cx0, cy0);
                // 椭圆中央字符外描边（卡色）
                g.setColor(displayColor.awtColor());
                int[][] offsets = { {-1,0},{1,0},{0,-1},{0,1} };
                for (int[] o : offsets) {
                    g.drawString(s, cx0 + o[0], cy0 + o[1]);
                }
                g.setColor(Color.WHITE);
                g.drawString(s, cx0, cy0);
            }

            // 6) 左上角小角标（卡色）
            drawCornerLabel(g, x, y, s, displayColor.awtColor(), false);

            // 7) 右下角小角标（卡色，旋转 180°）
            drawCornerLabel(g, x, y, s, displayColor.awtColor(), true);
        }

        /** 角标：左上小字符 / 右下旋转 180° 小字符 */
        private void drawCornerLabel(Graphics2D g, int x, int y, String s, Color col, boolean rotated) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(col);
            g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            if (rotated) {
                // 绕牌中心旋转 180°，使右下角标落在牌内而非溢出
                g2.rotate(Math.PI, x + CARD_W / 2.0, y + CARD_H / 2.0);
            }
            g2.drawString(s, x + 5, y + 14);
            g2.dispose();
        }

        /** 牌背（参考图）：黑底 + 中央大红椭圆 + 椭圆内黄色 "UNO" */
        private void drawCardBack(Graphics2D g, int x, int y) {
            // 圆角外框（避免方形锯齿感）
            Shape oldClip = g.getClip();
            java.awt.geom.RoundRectangle2D.Float rr =
                    new java.awt.geom.RoundRectangle2D.Float(x, y, CARD_W, CARD_H, 10, 10);

            // 黑底（按图，牌背主体为黑色）
            g.setClip(rr);
            g.setColor(Color.BLACK);
            g.fillRect(x, y, CARD_W, CARD_H);

            int cx = x + CARD_W / 2;
            int cy = y + CARD_H / 2;

            // 角落白色装饰（4 角小方点 + 细十字）—— UNO 官方卡背的标志性小亮点
            g.setColor(new Color(0xee, 0xee, 0xee));
            int dotR = 2;
            int[][] corners = {
                    {x + 8, y + 8}, {x + CARD_W - 8, y + 8},
                    {x + 8, y + CARD_H - 8}, {x + CARD_W - 8, y + CARD_H - 8}
            };
            for (int[] c : corners) {
                g.fillOval(c[0] - dotR, c[1] - dotR, dotR * 2, dotR * 2);
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(c[0] - 5, c[1], c[0] + 5, c[1]);
                g.drawLine(c[0], c[1] - 5, c[0], c[1] + 5);
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(0xee, 0xee, 0xee));
            }

            // 中央大红椭圆（按官方卡背：占牌面大部分）
            int rx = Math.round(CARD_W * 0.42f);
            int ry = Math.round(CARD_H * 0.34f);
            g.setColor(new Color(0xd9, 0x1e, 0x2a)); // UNO 官方红
            g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
            // 椭圆白色描边
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(cx - rx, cy - ry, rx * 2, ry * 2);
            g.setStroke(new BasicStroke(1));

            // "UNO" 字：稍向上倾斜、加粗、白色、深色描边（官方卡背字是白色）
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.rotate(-0.18, cx, cy);
            g2.setFont(new Font("Arial Black", Font.BOLD, 20));
            String t = "UNO";
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(t);
            int th = fm.getAscent();
            int tx = cx - tw / 2;
            int ty = cy + th / 2 - 3;
            // 暗红/深棕描边
            g2.setColor(new Color(0x3a, 0x10, 0x08));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx * dx + dy * dy <= 5 && (dx != 0 || dy != 0)) {
                        g2.drawString(t, tx + dx, ty + dy);
                    }
                }
            }
            // 白字
            g2.setColor(Color.WHITE);
            g2.drawString(t, tx, ty);
            g2.dispose();

            // 圆角外白细边
            g.setClip(oldClip);
            g.setColor(new Color(0x33, 0x33, 0x33));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(rr);
            g.setStroke(new BasicStroke(1));
        }

        /** 牌背 mini 版（对手牌堆用）：按比例缩放，复用主牌背关键元素 */
        private void drawCardBackMini(Graphics2D g, int x, int y, int w, int h) {
            Shape oldClip = g.getClip();
            java.awt.geom.RoundRectangle2D.Float rr =
                    new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, 5, 5);
            g.setClip(rr);
            g.setColor(Color.BLACK);
            g.fillRect(x, y, w, h);
            int cx = x + w / 2;
            int cy = y + h / 2;
            // 红椭圆（按比例）
            int rx = Math.round(w * 0.42f);
            int ry = Math.round(h * 0.34f);
            g.setColor(new Color(0xd9, 0x1e, 0x2a));
            g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.2f));
            g.drawOval(cx - rx, cy - ry, rx * 2, ry * 2);
            g.setStroke(new BasicStroke(1));
            // UNO 字（按比例缩字号，太小就跳过）
            int fontSize = Math.max(8, h / 4);
            if (fontSize >= 8) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.rotate(-0.18, cx, cy);
                g2.setFont(new Font("Arial Black", Font.BOLD, fontSize));
                String t = "UNO";
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(t);
                int th = fm.getAscent();
                int tx = cx - tw / 2;
                int ty = cy + th / 2 - 1;
                g2.setColor(Color.WHITE);
                g2.drawString(t, tx, ty);
                g2.dispose();
            }
            g.setClip(oldClip);
        }

        /**
         * +4 牌设计（UNO 官方）：黑底，中央 2×2 方块
         *   ┌──┬──┐
         *   │ 红 │ 黄 │
         *   ├──┼──┤
         *   │ 蓝 │ 绿 │
         *   └──┴──┘
         * 上方覆盖白色 "+4" 字
         */
        private void drawPlus4Design(Graphics2D g, int x, int y) {
            int cw = 22, ch = 22;
            int cx0 = x + (CARD_W - cw * 2) / 2;
            int cy0 = y + (CARD_H - ch * 2) / 2;
            g.setColor(C_RED);
            g.fillRect(cx0,         cy0,         cw, ch);   // 红
            g.setColor(C_YELLOW);
            g.fillRect(cx0 + cw,    cy0,         cw, ch);   // 黄
            g.setColor(C_BLUE);
            g.fillRect(cx0,         cy0 + ch,    cw, ch);   // 蓝
            g.setColor(C_GREEN);
            g.fillRect(cx0 + cw,    cy0 + ch,    cw, ch);   // 绿
            // 4 色方块白色边框
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(cx0 - 1,         cy0 - 1,         cw * 2 + 1, ch * 2 + 1);
            g.drawRect(cx0,             cy0,             cw,         ch);
            g.drawRect(cx0 + cw,        cy0,             cw,         ch);
            g.drawRect(cx0,             cy0 + ch,        cw,         ch);
            g.drawRect(cx0 + cw,        cy0 + ch,        cw,         ch);
            g.setStroke(new BasicStroke(1));
            // 中央覆盖 "+4" 白字
            g.setColor(Color.WHITE);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
            String t = "+4";
            int tw = g.getFontMetrics().stringWidth(t);
            int th = g.getFontMetrics().getAscent();
            int tx = x + (CARD_W - tw) / 2;
            int ty = y + (CARD_H - 8) / 2 + th / 2;
            g.drawString(t, tx, ty);
            // 角标
            drawCornerLabel(g, x, y, "+4", Color.WHITE, false);
            drawCornerLabel(g, x, y, "+4", Color.WHITE, true);
        }

        /**
         * WILD 牌设计（UNO 官方）：黑底，2×2 四色方块（红黄/蓝绿），与 +4 同款但去掉中央 +4 字
         */
        private void drawWildDesign(Graphics2D g, int x, int y) {
            int cw = 22, ch = 22;
            int cx0 = x + (CARD_W - cw * 2) / 2;
            int cy0 = y + (CARD_H - ch * 2) / 2;
            g.setColor(C_RED);
            g.fillRect(cx0,         cy0,         cw, ch);   // 红
            g.setColor(C_YELLOW);
            g.fillRect(cx0 + cw,    cy0,         cw, ch);   // 黄
            g.setColor(C_BLUE);
            g.fillRect(cx0,         cy0 + ch,    cw, ch);   // 蓝
            g.setColor(C_GREEN);
            g.fillRect(cx0 + cw,    cy0 + ch,    cw, ch);   // 绿
            // 4 色方块白色边框
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(cx0 - 1, cy0 - 1, cw * 2 + 1, ch * 2 + 1);
            g.drawRect(cx0, cy0, cw, ch);
            g.drawRect(cx0 + cw, cy0, cw, ch);
            g.drawRect(cx0, cy0 + ch, cw, ch);
            g.drawRect(cx0 + cw, cy0 + ch, cw, ch);
            g.setStroke(new BasicStroke(1));
            // WILD 牌不写 W，只保留四色方块图案
        }

        /** SKIP 牌：椭圆内画一个空心圆 + 一条斜杠（官方 ⊘ 风格） */
        private void drawSkip(Graphics2D g, int x, int y, Color fg) {
            int cx = x + CARD_W / 2;
            int cy = y + CARD_H / 2;
            int r = 16;
            g.setColor(fg);
            g.setStroke(new BasicStroke(4));
            g.drawOval(cx - r, cy - r, r * 2, r * 2);
            g.drawLine(cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3);
            g.setStroke(new BasicStroke(1));
        }

        /** REVERSE 牌：椭圆内画两个对向箭头（⇄ 风格）
         *  @param mirrored true 时上下箭头互换（即左右镜像） */
        private void drawReverse(Graphics2D g, int x, int y, Color fg, boolean mirrored) {
            int cx = x + CARD_W / 2;
            int cy = y + CARD_H / 2;
            int len = 16;
            int gap = 5;
            g.setColor(fg);
            g.setStroke(new BasicStroke(3));
            // 上方箭头（不镜像：线左→右，箭头指右）
            int topY = cy - gap;
            int topX0 = cx - gap, topX1 = cx - gap + len;
            int botY = cy + gap;
            int botX0 = cx + gap, botX1 = cx + gap - len;
            // 镜像时上下交换：原本"上线+右箭头 / 下线+左箭头" → "上线+左箭头 / 下线+右箭头"
            int upperLineX0, upperLineX1, upperTipDir; // +1=右箭头，-1=左箭头
            int lowerLineX0, lowerLineX1, lowerTipDir;
            if (mirrored) {
                upperLineX0 = botX0; upperLineX1 = botX1; upperTipDir = -1;
                lowerLineX0 = topX0; lowerLineX1 = topX1; lowerTipDir = +1;
            } else {
                upperLineX0 = topX0; upperLineX1 = topX1; upperTipDir = +1;
                lowerLineX0 = botX0; lowerLineX1 = botX1; lowerTipDir = -1;
            }
            // 上方箭头线 + 三角
            g.drawLine(upperLineX0, topY, upperLineX1, topY);
            int[] uxs, uys;
            if (upperTipDir == 1) {
                uxs = new int[] { upperLineX1 + 6, upperLineX1, upperLineX1 };
                uys = new int[] { topY, topY - 4, topY + 4 };
            } else {
                uxs = new int[] { upperLineX1 - 6, upperLineX1, upperLineX1 };
                uys = new int[] { topY, topY - 4, topY + 4 };
            }
            g.fillPolygon(uxs, uys, 3);
            // 下方箭头线 + 三角
            g.drawLine(lowerLineX0, botY, lowerLineX1, botY);
            int[] lxs, lys;
            if (lowerTipDir == 1) {
                lxs = new int[] { lowerLineX1 + 6, lowerLineX1, lowerLineX1 };
                lys = new int[] { botY, botY - 4, botY + 4 };
            } else {
                lxs = new int[] { lowerLineX1 - 6, lowerLineX1, lowerLineX1 };
                lys = new int[] { botY, botY - 4, botY + 4 };
            }
            g.fillPolygon(lxs, lys, 3);
            g.setStroke(new BasicStroke(1));
        }

        private void drawHand(Graphics2D g) {
            Player me = players.get(myPlayerIdx);
            Rectangle[] rs = handCardRects();
            for (int i = 0; i < me.hand.size(); i++) {
                UnoCard c = me.hand.get(i);
                int yShift = (i == selectedHandIdx) ? -10 : 0;
                int drawX = rs[i].x;
                int drawY = rs[i].y + yShift;
                UnoCard.Color displayColor = c.isWild() ? UnoCard.Color.BLACK : c.color;
                drawCard(g, drawX, drawY, c, displayColor, false);
                // 可出提示：轮到我出牌时，所有能出的牌高亮闪烁
                boolean playable = (currentPlayerIdx == myPlayerIdx) && !waitingForColor && !waitingForChallenge
                        && c.canPlayOn(topCard, currentColor, pendingDraws, mode == 1);
                if (playable) {
                    float b = blink(); // 0..1 平滑脉冲
                    // 半透明填充提亮：55..150 alpha
                    g.setColor(new Color(BLINK_GLOW.getRed(), BLINK_GLOW.getGreen(), BLINK_GLOW.getBlue(), (int) (55 + 95 * b)));
                    g.fillRoundRect(drawX, drawY, CARD_W, CARD_H, 10, 10);
                    // 外发光描边：150..255 alpha
                    g.setColor(new Color(BLINK_GLOW.getRed(), BLINK_GLOW.getGreen(), BLINK_GLOW.getBlue(), (int) (150 + 105 * b)));
                    g.setStroke(new BasicStroke(3f));
                    g.drawRoundRect(drawX - 1, drawY - 1, CARD_W + 2, CARD_H + 2, 11, 11);
                    g.setStroke(new BasicStroke(1));
                }
                // 选中只靠上移（-10）反馈，不再额外高亮这张牌
            }
        }

        /** 平滑闪烁脉冲因子 0..1（周期约 650ms，无需新计时器，由 spinTimer 的 45ms 重绘驱动） */
        private float blink() {
            final long period = 650;
            long t = System.currentTimeMillis() % period;
            return (float) (0.5 - 0.5 * Math.cos(t / (double) period * 2 * Math.PI));
        }

        /** 当前是否轮到我、且我手里一张能出的牌都没有（用于提示去摸牌） */
        private boolean myTurnNoPlayable() {
            if (currentPlayerIdx != myPlayerIdx || waitingForColor || waitingForChallenge) return false;
            Player me = players.get(myPlayerIdx);
            for (UnoCard c : me.hand) {
                if (c.canPlayOn(topCard, currentColor, pendingDraws, mode == 1)) return false;
            }
            return true;
        }

        private void drawActionText(Graphics2D g) {
            if (System.currentTimeMillis() - lastActionAt > 2500) return;
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            int w = g.getFontMetrics().stringWidth(lastActionText);
            int x = (W - w) / 2;
            int y = 75;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(x - 12, y - 22, w + 24, 32, 8, 8);
            g.setColor(YELLOW);
            g.drawString(lastActionText, x, y);
        }

        private void drawPendingBadge(Graphics2D g) {
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                if (p.pendingDrawsOnMe <= 0) continue;
                Point pp = playerPositions[i];
                g.setColor(new Color(0xff, 0x55, 0x00));
                g.fillOval(pp.x + AVATAR_R - 8, pp.y - AVATAR_R - 8, 28, 22);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
                String s = "+" + p.pendingDrawsOnMe;
                int sw = g.getFontMetrics().stringWidth(s);
                g.drawString(s, pp.x + AVATAR_R - 8 + (28 - sw) / 2, pp.y - AVATAR_R + 8);
            }
        }

        private void drawPlayingCardAnim(Graphics2D g) {
            long elapsed = System.currentTimeMillis() - playingStartMs;
            double t = Math.min(1.0, elapsed / (double) playingDurationMs);
            // ease-out：先快后慢
            double e = 1 - Math.pow(1 - t, 2.0);
            int x = (int) (playingFromX + (playingToX - playingFromX) * e);
            int y = (int) (playingFromY + (playingToY - playingFromY) * e);
            // 微缩 0.95→1.0 让落点更有重量感
            double scale = 0.92 + 0.08 * e;
            int w = (int) (CARD_W * scale);
            int h = (int) (CARD_H * scale);
            x -= (w - CARD_W) / 2;
            y -= (h - CARD_H) / 2;
            // 阴影
            g.setColor(new Color(0, 0, 0, 80));
            g.fillRoundRect(x + 3, y + 4, w, h, 10, 10);
            drawCard(g, x, y, playingCard, playingDisplayColor, false);
        }
    }

    // ============================================================
    //                在线模式：服务端权威状态同步
    // ============================================================

    /** MessageCenter 路由入口：收到 UNO_STATE 推送 */
    public static void receiveState(int roomId, String body) {
        UnoGame g = activeGames.get(roomId);
        if (g == null) return;
        SwingUtilities.invokeLater(() -> g.applyServerState(body));
    }

    /** MessageCenter 路由入口：收到 DUEL_GAME_OVER 推送 */
    public static void receiveGameOver(int roomId, String overData) {
        UnoGame g = activeGames.get(roomId);
        if (g == null) return;
        SwingUtilities.invokeLater(() -> g.applyGameOver(overData));
    }

    public static boolean hasActiveGame(int roomId) { return activeGames.containsKey(roomId); }

    // ---------- 指令发送 ----------

    /** 出牌：displayIdx 是界面（排序后）下标，需换算成服务端下标 */
    private void sendOnlinePlay(int displayIdx, UnoCard.Color chosen) {
        if (displayIdx < 0 || displayIdx >= myServerIdx.size()) {
            showAction("手牌正在同步，请稍候");
            return;
        }
        final int sIdx = myServerIdx.get(displayIdx);
        final String cc = colorCode(chosen);
        selectedHandIdx = -1;
        turnAnimating = true; // 等服务端回包期间锁住输入，防连点
        Thread t = new Thread(() -> {
            String r = ServerClient.unoPlay(roomId, myUsername, sIdx, cc);
            SwingUtilities.invokeLater(() -> {
                turnAnimating = false;
                if (r == null || !r.startsWith("SUCCESS")) {
                    showAction(errText(r, "出牌失败"));
                    boardPanel.repaint();
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 通用异步指令：失败时把服务端错误文案显示到屏幕中央 */
    private void sendOnlineCmd(java.util.function.Supplier<String> call, String failHint) {
        Thread t = new Thread(() -> {
            String r = call.get();
            if (failHint == null) return;
            if (r == null || !r.startsWith("SUCCESS")) {
                SwingUtilities.invokeLater(() -> {
                    showAction(errText(r, failHint));
                    boardPanel.repaint();
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static String errText(String resp, String fallback) {
        if (resp == null) return fallback;
        int i = resp.indexOf('|');
        return (i >= 0 && i + 1 < resp.length()) ? resp.substring(i + 1) : fallback;
    }

    private static String colorCode(UnoCard.Color c) {
        if (c == null) return "x";
        switch (c) {
            case RED:    return "R";
            case YELLOW: return "Y";
            case GREEN:  return "G";
            case BLUE:   return "B";
            default:     return "x";
        }
    }

    // ---------- 状态解析 ----------

    private static UnoCard.Color colorFromIdx(int i) {
        switch (i) {
            case 0:  return UnoCard.Color.RED;
            case 1:  return UnoCard.Color.YELLOW;
            case 2:  return UnoCard.Color.GREEN;
            case 3:  return UnoCard.Color.BLUE;
            default: return UnoCard.Color.BLACK;
        }
    }

    /** 解码服务端牌编码，如 "R55"=红5 "Gs"=绿跳过 "Kf"=+4 */
    private static UnoCard decodeCard(String s) {
        if (s == null || s.length() < 2) return new UnoCard(UnoCard.Color.RED, UnoCard.Type.NUMBER, 0);
        UnoCard.Color col = colorFromIdx("RYGBK".indexOf(s.charAt(0)));
        char tc = s.charAt(1);
        if (tc >= '0' && tc <= '9') {
            int num = tc - '0';
            if (s.length() > 2) {
                try { num = Integer.parseInt(s.substring(2)); } catch (NumberFormatException ignored) {}
            }
            return new UnoCard(col, UnoCard.Type.NUMBER, num);
        }
        switch (tc) {
            case 's': return new UnoCard(col, UnoCard.Type.SKIP, 0);
            case 'r': return new UnoCard(col, UnoCard.Type.REVERSE, 0);
            case 'd': return new UnoCard(col, UnoCard.Type.DRAW_TWO, 0);
            case 'w': return new UnoCard(UnoCard.Color.BLACK, UnoCard.Type.WILD, 0);
            case 'f': return new UnoCard(UnoCard.Color.BLACK, UnoCard.Type.WILD_DRAW_FOUR, 0);
            default:  return new UnoCard(col, UnoCard.Type.NUMBER, 0);
        }
    }

    /**
     * 应用服务端整盘状态。格式：
     * roomId|色|当前玩家|方向|累加|反转镜像|顶牌|结束|赢家|模式|待质疑者|名字,张数,已喊UNO|…|MYHAND|牌,牌,…
     */
    public void applyServerState(String body) {
        if (!onlineMode || gameOver || body == null || body.isEmpty()) return;
        String[] f = body.split("\\|", -1);
        if (f.length < 13) return;

        int newColorIdx, newCur, newDir, newPending;
        boolean newMirror, srvOver;
        String topEnc, winner, chal, drawnDecide;
        try {
            newColorIdx = Integer.parseInt(f[1]);
            newCur      = Integer.parseInt(f[2]);
            newDir      = Integer.parseInt(f[3]);
            newPending  = Integer.parseInt(f[4]);
            newMirror   = "1".equals(f[5]);
            topEnc      = f[6];
            srvOver     = "1".equals(f[7]);
            winner      = f[8];
            chal        = f[10];
            drawnDecide = f[11];   // "user,handIdx" 或空串
        } catch (NumberFormatException e) {
            return;
        }

        // ---- 玩家名单（name,count,unoFlag）----
        final int P0 = 12; // 玩家条目起始下标
        int mh = -1;
        for (int i = P0; i < f.length; i++) {
            if ("MYHAND".equals(f[i])) { mh = i; break; }
        }
        int endNames = (mh < 0) ? f.length : mh;
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Boolean> unoFlags = new ArrayList<>();
        List<Boolean> hammerFlags = new ArrayList<>(); // 在线：服务端权威；离线：null 走本地推导
        final boolean onlineBlock = onlineMode;
        for (int i = P0; i < endNames; i++) {
            String entry = f[i];
            // 支持 name,hand,uno 或 name,hand,uno,hammer
            String[] parts = entry.split(",", -1);
            if (parts.length < 2) continue;
            names.add(parts[0]);
            counts.add(parseIntSafe(parts[1]));
            unoFlags.add(parts.length > 2 && "1".equals(parts[2]));
            hammerFlags.add(onlineBlock && parts.length > 3 && "1".equals(parts[3]));
        }
        if (names.isEmpty()) return;
        syncPlayers(names);

        // ---- 出牌动画：顶牌变了说明有人出了牌 ----
        UnoCard newTop = decodeCard(topEnc);
        boolean topChanged = onlineStateReceived && prevTopEnc != null && !prevTopEnc.equals(topEnc);

        // ---- 覆盖核心状态 ----
        topCard = newTop;
        currentColor = colorFromIdx(newColorIdx);
        if (newDir != direction) ringSpinUntil = System.currentTimeMillis() + 500; // 换向冲击
        direction = newDir;
        pendingDraws = newPending;
        reverseDesignMirrored = newMirror;
        currentPlayerIdx = (newCur >= 0 && newCur < players.size()) ? newCur : 0;

        // ---- 各家手牌数 / 累加提示 / UNO 锤子 ----
        for (int i = 0; i < players.size() && i < counts.size(); i++) {
            Player p = players.get(i);
            int cnt = counts.get(i);
            Integer before = prevCounts.get(p.name);
            if (before != null && cnt > before && onlineStateReceived) {
                showAction(p.name + " 摸了 " + (cnt - before) + " 张");
            }
            prevCounts.put(p.name, cnt);
            // 别人手牌变 1 张且没喊 UNO → 可以抓。
            // 在线模式使用服务端权威信号（hammerFlags），保证"当前不是他的回合才挂锤"。
            if (onlineMode) {
                boolean serverHammer = i < hammerFlags.size() && hammerFlags.get(i);
                p.showHammer = serverHammer;
                if (p.showHammer && before != null && before != 1) p.hammerShownAt = System.currentTimeMillis();
            } else {
                p.showHammer = (i != myPlayerIdx) && cnt == 1 && !unoFlags.get(i);
                if (p.showHammer && before != null && before != 1) p.hammerShownAt = System.currentTimeMillis();
            }
            if (i != myPlayerIdx) {
                // 用占位牌把手牌数补对，绘制层按 hand.size() 画背面
                while (p.hand.size() > cnt) p.hand.remove(p.hand.size() - 1);
                while (p.hand.size() < cnt) p.hand.add(new UnoCard(UnoCard.Color.BLACK, UnoCard.Type.NUMBER, 0));
            }
            p.pendingDrawsOnMe = 0;
        }
        if (newPending > 0 && currentPlayerIdx >= 0 && currentPlayerIdx < players.size()) {
            players.get(currentPlayerIdx).pendingDrawsOnMe = newPending;
        }

        // ---- 我的手牌（服务端下标 → 界面排序）----
        if (mh >= 0 && mh + 1 < f.length) {
            String hs = f[mh + 1];
            List<UnoCard> raw = new ArrayList<>();
            if (!hs.isEmpty()) {
                for (String cs : hs.split(",")) {
                    if (!cs.isEmpty()) raw.add(decodeCard(cs));
                }
            }
            setMyHandFromServer(raw);
        }
        // 我手牌不是 2 张了，喊 UNO 标记作废
        if (players.get(myPlayerIdx).hand.size() != 2) unoCalled = false;

        // ---- 播放出牌动画 ----
        if (topChanged && prevCurrentIdx >= 0 && prevCurrentIdx < players.size()) {
            animateServerPlay(newTop, prevCurrentIdx, newTop.isWild() ? currentColor : newTop.color);
        }
        prevTopEnc = topEnc;
        prevCurrentIdx = currentPlayerIdx;
        onlineStateReceived = true;

        // ---- +4 质疑窗口 ----
        if (chal != null && !chal.isEmpty() && chal.equals(myUsername)) {
            if (!challengePromptShown) {
                challengePromptShown = true;
                SwingUtilities.invokeLater(this::showOnlineChallengeDialog);
            }
        } else {
            challengePromptShown = false;
        }

        // ---- 摸到的牌恰好能出：问要不要立刻打出 ----
        if (drawnDecide != null && !drawnDecide.isEmpty()) {
            int c = drawnDecide.lastIndexOf(',');
            String who = c > 0 ? drawnDecide.substring(0, c) : drawnDecide;
            if (who.equals(myUsername)) {
                int sIdx = c > 0 ? parseIntSafe(drawnDecide.substring(c + 1)) : -1;
                if (!drawDecidePromptShown) {
                    drawDecidePromptShown = true;
                    final int fIdx = sIdx;
                    SwingUtilities.invokeLater(() -> showDrawDecideDialog(fIdx));
                }
            }
        } else {
            drawDecidePromptShown = false;
        }

        if (srvOver && winner != null && !winner.isEmpty()) showAction(winner + " 出完了！");

        // 自动认罚：被加牌 + 轮到我 + 我没有任何可接牌（无可出的 +2/+4/同色牌）+ 无未决挑战/决策
        // → 不必点摸牌，直接吃下累加张数
        boolean iAmStuck = !srvOver
                && newPending > 0 && newCur == myPlayerIdx
                && (chal == null || chal.isEmpty())
                && (drawnDecide == null || drawnDecide.isEmpty() || !drawnDecide.startsWith(myUsername))
                && !canStack(myPlayerIdx);
        if (iAmStuck && !autoDrawPending) {
            autoDrawPending = true; // 防止同一状态被重复推时反复触发
            SwingUtilities.invokeLater(() -> {
                if (!gameOver && !turnAnimating) onDrawClicked(); // 在线：等价于认罚吃 pendingDraws
            });
        }
        // 状态已翻篇（吃牌清零 / 轮到别人）→ 允许下一轮再自动
        if (newPending == 0 || newCur != myPlayerIdx) autoDrawPending = false;

        // ---- 计时字段（服务端末尾固定为：matchStartMs|matchDurationMs|turnDeadline）----
        if (f.length >= 3) {
            srvMatchStartMs = parseLongSafe(f[f.length - 3]);
            srvMatchDurationMs = parseLongSafe(f[f.length - 2]);
            srvTurnDeadline = parseLongSafe(f[f.length - 1]);
        }

        boardPanel.repaint();
    }

    private static long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    /** 名单变化时重建玩家列表，并重新定位「我」在环上的位置 */
    private void syncPlayers(List<String> names) {
        boolean same = players.size() == names.size();
        if (same) {
            for (int i = 0; i < names.size(); i++) {
                if (!players.get(i).name.equals(names.get(i))) { same = false; break; }
            }
        }
        if (same) {
            int mi = names.indexOf(myUsername);
            if (mi >= 0 && mi != myPlayerIdx) { myPlayerIdx = mi; playerPositions = null; }
            return;
        }
        Map<String, Player> old = new HashMap<>();
        for (Player p : players) old.put(p.name, p);
        List<Player> rebuilt = new ArrayList<>();
        int seedC = Math.abs(roomId * 7919 + 13);
        for (String n : names) {
            Player p = old.get(n);
            if (p == null) p = new Player(n, n.startsWith("机器人"), seedC);
            rebuilt.add(p);
            seedC += 7919;
        }
        players.clear();
        players.addAll(rebuilt);
        int mi = names.indexOf(myUsername);
        myPlayerIdx = mi >= 0 ? mi : 0;
        playerPositions = null; // 强制重算环上坐标
    }

    /** 用服务端顺序的手牌重建界面手牌，同时维护 界面下标 → 服务端下标 的映射 */
    private void setMyHandFromServer(List<UnoCard> cards) {
        Player me = players.get(myPlayerIdx);
        Integer[] order = new Integer[cards.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            UnoCard ca = cards.get(a), cb = cards.get(b);
            int ga = groupOf(ca), gb = groupOf(cb);
            if (ga != gb) return Integer.compare(ga, gb);
            return Integer.compare(rankOf(ca), rankOf(cb));
        });
        me.hand.clear();
        myServerIdx.clear();
        for (Integer i : order) {
            me.hand.add(cards.get(i));
            myServerIdx.add(i);
        }
        if (selectedHandIdx >= me.hand.size()) selectedHandIdx = -1;
    }

    /** 在线出牌动画：从出牌者头像飞到出牌区 */
    private void animateServerPlay(UnoCard c, int fromIdx, UnoCard.Color disp) {
        computePlayerPositions();
        playingCard = c;
        playingDisplayColor = disp;
        playingToX = PLAY_AREA_CX + 20;
        playingToY = PLAY_AREA_CY - CARD_H / 2;
        if (playerPositions != null && fromIdx >= 0 && fromIdx < playerPositions.length) {
            Point pp = playerPositions[fromIdx];
            playingFromX = pp.x - CARD_W / 2;
            playingFromY = pp.y - CARD_H / 2 - 6;
        } else {
            playingFromX = (W - CARD_W) / 2;
            playingFromY = HAND_Y;
        }
        playingCardAnim = true;
        playingStartMs = System.currentTimeMillis();
        if (playAnimTimer != null && playAnimTimer.isRunning()) playAnimTimer.stop();
        playAnimTimer = new javax.swing.Timer(16, e -> {
            double t = (System.currentTimeMillis() - playingStartMs) / (double) playingDurationMs;
            if (t >= 1.0) {
                playingCardAnim = false;
                ((javax.swing.Timer) e.getSource()).stop();
            }
            boardPanel.repaint();
        });
        playAnimTimer.start();
    }

    /**
     * 通用 15 秒倒计时确认框（与在线一致）：超时按 defaultChoice 处理并关闭。
     * 返回 true=点 yesText / false=点 noText（或超时默认）。
     */
    private boolean confirmTimeout(String title, String html, String yesText, String noText, boolean defaultChoice) {
        if (gameOver) return defaultChoice;
        final boolean[] res = {defaultChoice};
        final JDialog d = new JDialog(this, title, true);
        d.setSize(440, 260);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout(8, 8));
        d.getContentPane().setBackground(BG); // 深色背景，浅色 FG 文字才能看清
        JLabel msg = new JLabel("<html><div style='text-align:center;'>" + html + "</div></html>", SwingConstants.CENTER);
        msg.setForeground(FG);
        msg.setBackground(BG);
        msg.setOpaque(true);
        msg.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        d.add(msg, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        btns.setBackground(BG);
        JButton yes = new JButton(yesText);
        JButton no = new JButton(noText);
        for (JButton b : new JButton[]{yes, no}) {
            b.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            b.setPreferredSize(new Dimension(120, 44));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        // 「打出/确认」= 绿色（执行动作）；「不放/不质疑」= 红色（跳过/取消）
        yes.setBackground(C_GREEN); yes.setOpaque(true); yes.setBorderPainted(false);
        no.setBackground(C_RED); no.setOpaque(true); no.setBorderPainted(false);
        final boolean[] done = {false};
        Runnable fire = () -> {
            if (done[0]) return;
            done[0] = true;
            d.dispose();
        };
        yes.addActionListener(e -> { res[0] = true; fire.run(); });
        no.addActionListener(e -> { res[0] = false; fire.run(); });
        btns.add(yes); btns.add(no);
        d.add(btns, BorderLayout.SOUTH);
        // 15 秒倒计时：超时按 defaultChoice 处理
        final int[] left = {15};
        final String defLabel = defaultChoice ? yesText : noText;
        JLabel cd = new JLabel("15 秒后自动处理", SwingConstants.CENTER);
        cd.setForeground(YELLOW); cd.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        d.add(cd, BorderLayout.NORTH);
        final javax.swing.Timer t = new javax.swing.Timer(1000, e -> {
            if (!d.isVisible()) { ((javax.swing.Timer) e.getSource()).stop(); return; }
            left[0]--;
            if (left[0] <= 0) {
                cd.setText("已超时，自动" + defLabel);
                res[0] = defaultChoice;
                fire.run();
                ((javax.swing.Timer) e.getSource()).stop();
            } else {
                cd.setText(left[0] + " 秒后自动" + defLabel);
            }
        });
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent e) { t.stop(); }
        });
        t.start();
        d.setVisible(true);
        t.stop();
        return res[0];
    }

    /** 在线 +4 质疑弹窗（服务端 15 秒不回就当作不质疑） */
    private void showOnlineChallengeDialog() {
        if (gameOver) return;
        boolean ch = confirmTimeout("质疑 +4",
                "上家对你出了 +4（当前色：" + colorCN(currentColor) + "）<br><br>"
                        + "质疑成功：对方自吃 " + Math.max(pendingDraws, 4) + " 张<br>"
                        + "质疑失败：你吃 6 张<br>"
                        + "不质疑：你吃 " + Math.max(pendingDraws, 4) + " 张",
                "质疑", "不质疑", false);
        sendOnlineCmd(() -> ServerClient.unoChallenge(roomId, myUsername, ch), null);
    }

    /** 摸到的牌恰好能出：问玩家要不要立刻打出（服务端权威语义） */
    private void showDrawDecideDialog(int sIdx) {
        if (gameOver) return;
        Player me = players.get(myPlayerIdx);
        int disp = myServerIdx.indexOf(sIdx);
        UnoCard card = (disp >= 0 && disp < me.hand.size()) ? me.hand.get(disp) : null;
        String dn = (card != null)
                ? (card.isWild()
                        ? (card.type == UnoCard.Type.WILD_DRAW_FOUR ? "+4 万能" : "变色万能")
                        : colorCN(card.color) + (card.displayChar().isEmpty() ? "" : " " + card.displayChar()))
                : "一张牌";
        int resp = JOptionPane.showConfirmDialog(this,
                "你摸到 " + dn + "，可以打出，是否打出？",
                "摸牌", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (resp != JOptionPane.YES_OPTION) {
            // 不打出 → 结束回合，交给服务器推进
            sendOnlineCmd(() -> ServerClient.unoDrawDecide(roomId, myUsername, false, "x"), "操作失败");
            return;
        }
        // 要打出：万能牌先选色
        final UnoCard.Color pick = (card != null && card.isWild()) ? chooseOnlineColor() : null;
        final String cc = colorCode(pick);
        turnAnimating = true; // 等服务端回包期间锁输入，防连点
        Thread t = new Thread(() -> {
            String r = ServerClient.unoDrawDecide(roomId, myUsername, true, cc);
            SwingUtilities.invokeLater(() -> {
                turnAnimating = false;
                if (r == null || !r.startsWith("SUCCESS")) {
                    showAction(errText(r, "打出失败"));
                    boardPanel.repaint();
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /** 在线选色对话框（红/黄/绿/蓝），阻塞直到玩家点选，返回所选颜色；15 秒超时自动选最多色 */
    private UnoCard.Color chooseOnlineColor() {
        final UnoCard.Color[] result = { UnoCard.Color.RED }; // 兜底红
        final JDialog d = new JDialog(this, "选择颜色", true);
        d.setUndecorated(true);
        d.setSize(460, 130);
        d.setLocationRelativeTo(this);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 22));
        p.setBackground(new Color(0x1b, 0x1b, 0x29));
        UnoCard.Color[] cardColors = { UnoCard.Color.RED, UnoCard.Color.YELLOW, UnoCard.Color.GREEN, UnoCard.Color.BLUE };
        Color[] btnColors = { C_RED, C_YELLOW, C_GREEN, C_BLUE };
        String[] names = { "红", "黄", "绿", "蓝" };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            JButton btn = new JButton(names[i]);
            btn.setBackground(btnColors[i]);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
            btn.setFocusPainted(false);
            btn.setOpaque(true);
            btn.setBorderPainted(false);
            btn.setPreferredSize(new Dimension(90, 60));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                result[0] = cardColors[idx];
                d.dispose();
            });
            p.add(btn);
        }
        d.setContentPane(p);
        // 15 秒倒计时：超时自动选手牌最多色（平局随机）
        final int[] left = {15};
        final javax.swing.Timer t = new javax.swing.Timer(1000, e -> {
            if (!d.isVisible()) { ((javax.swing.Timer) e.getSource()).stop(); return; }
            left[0]--;
            if (left[0] <= 0) {
                result[0] = mostHandColor();
                d.setTitle("超时，自动选 " + colorCN(result[0]));
                ((javax.swing.Timer) e.getSource()).stop();
                d.dispose();
            } else {
                d.setTitle("选择颜色（" + left[0] + " 秒后自动选最多色）");
            }
        });
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent e) { t.stop(); }
        });
        t.start();
        d.setVisible(true);
        t.stop();
        return result[0];
    }

    /** 手牌中占比最高的非万能色；并列时随机选一个（用于超时自动选色） */
    private UnoCard.Color mostHandColor() {
        Player me = players.get(myPlayerIdx);
        int[] cnt = new int[4];
        for (UnoCard c : me.hand) {
            for (int i = 0; i < 4; i++) if (c.color == colorFromIdx(i)) cnt[i]++;
        }
        int max = -1;
        for (int i = 0; i < 4; i++) if (cnt[i] > max) max = cnt[i];
        List<Integer> best = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (cnt[i] == max) best.add(i);
        int pick = best.get(new java.util.Random().nextInt(best.size()));
        return colorFromIdx(pick);
    }

    /** 在线结算：overData 形如 "名字,WIN:0:0;名字,FAIL:0:37;" */
    private void applyGameOver(String overData) {
        if (onlineGameOverShown) return;
        onlineGameOverShown = true;
        gameOver = true;
        cleanup();

        List<String[]> rows = new ArrayList<>(); // {名字, WIN/FAIL, 扣分}
        if (overData != null) {
            for (String seg : overData.split(";")) {
                if (seg.trim().isEmpty()) continue;
                int comma = seg.lastIndexOf(',');
                if (comma <= 0) continue;
                String name = seg.substring(0, comma);
                String[] rp = seg.substring(comma + 1).split(":");
                String result = rp.length > 0 ? rp[0] : "FAIL";
                int score = (rp.length >= 3) ? parseIntSafe(rp[2]) : 0;
                rows.add(new String[]{ name, result, String.valueOf(score) });
            }
        }
        rows.sort((a, b) -> {
            boolean wa = "WIN".equals(a[1]), wb = "WIN".equals(b[1]);
            if (wa != wb) return wa ? -1 : 1;
            return Integer.compare(parseIntSafe(a[2]), parseIntSafe(b[2]));
        });

        StringBuilder sb = new StringBuilder("游戏结束！\n\n最终排名：\n");
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            sb.append(i + 1).append(". ").append(r[0]);
            if ("WIN".equals(r[1])) sb.append("   出完，胜！");
            else sb.append("   剩牌扣 ").append(r[2]).append(" 分");
            if (r[0].equals(myUsername)) sb.append("   ← 你");
            sb.append("\n");
        }
        if (rows.isEmpty()) sb.append("（本局提前结束）\n");
        sb.append("\n手牌扣分规则：数字牌=面值，功能牌=20，万能=50");
        JOptionPane.showMessageDialog(this, sb.toString(), "UNO 排名", JOptionPane.INFORMATION_MESSAGE);

        if (onCloseCallback != null) onCloseCallback.run();
        dispose();
    }
}
