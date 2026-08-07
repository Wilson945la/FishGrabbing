import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞行棋对决游戏页面
 * 十字形棋盘：15x15 网格，臂宽 7、短边 3、中心 7x7。
 * 外圈 56 步赛道（含4个起点），有效路径 53 步（其他色起点跳过）。
 * 起点：绿(0,10) 红(10,14) 蓝(14,4) 黄(4,0)
 * 颜色顺时针循环（无视起点）：蓝→黄→绿→红。四色 6 格航道，中心四色扇形。
 * 颜色：0红 1黄 2蓝 3绿。
 */
public class AeroChessGame extends JFrame {

    private static final Color BG = new Color(22, 24, 27);
    private static final Color CARD_BG = new Color(32, 34, 37);

    // 颜色顺序：0红 1黄 2蓝 3绿
    private static final Color RED    = new Color(232, 70, 90);
    private static final Color YELLOW = new Color(255, 205, 60);
    private static final Color BLUE   = new Color(85, 180, 230);
    private static final Color GREEN  = new Color(95, 195, 80);
    private static final Color[] PLAYER_COLORS = { RED, YELLOW, BLUE, GREEN };
    // 棋子专用深色，与格子底色区分
    private static final Color[] PIECE_COLORS = {
        new Color(170, 25, 40),    // 深红
        new Color(190, 135, 0),    // 深金
        new Color(15, 85, 155),    // 深蓝
        new Color(25, 110, 25),    // 深绿
    };
    private static final String[] COLOR_NAMES = { "红", "黄", "蓝", "绿" };

    // ===== 棋盘几何 =====
    private static final int N = 15;           // 15x15 网格
    private static final int CELL = 55;        // 每格 55 像素
    private static final int PAD = 20;         // 棋盘内边距
    private static final int C0 = 4;           // 中心起始行/列
    private static final int C1 = 10;          // 中心结束行/列（中心 7x7: rows 4-10, cols 4-10）

    private static final int T_EMPTY = 0;
    private static final int T_PATH  = 2;
    private static final int T_START = 3;  // 起点（白色格子）
    private static final int T_HOME  = 4;
    private static final int T_CENTER = 5;

    private static final int[][] CELL_TYPE  = new int[N][N];
    private static final int[][] CELL_COLOR = new int[N][N];
    static { initCellMap(); }

    /** 判断 (r,c) 是否在十字形内 */
    private static boolean inCross(int r, int c) {
        boolean horiz = (r >= C0 && r <= C1);  // 水平臂：rows 4-10 全列
        boolean vert  = (c >= C0 && c <= C1);   // 垂直臂：cols 4-10 全行
        return horiz || vert;
    }

    /** 初始化十字形格子地图 */
    private static void initCellMap() {
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                CELL_TYPE[r][c] = T_EMPTY;
                CELL_COLOR[r][c] = -1;
            }

        // ===== 外圈 56 步赛道：十字形轮廓，顺时针 =====
        int[][] outerCoords = {
            {0,7},{0,8},{0,9},{0,10},{1,10},{2,10},{3,10},{4,10},{4,11},{4,12},{4,13},
            {4,14},{5,14},{6,14},{7,14},{8,14},{9,14},{10,14},{10,13},{10,12},{10,11},
            {10,10},{11,10},{12,10},{13,10},{14,10},{14,9},{14,8},{14,7},{14,6},{14,5},
            {14,4},{13,4},{12,4},{11,4},{10,4},{10,3},{10,2},{10,1},{10,0},{9,0},{8,0},
            {7,0},{6,0},{5,0},{4,0},{4,1},{4,2},{4,3},{4,4},{3,4},{2,4},{1,4},{0,4},{0,5},{0,6}
        };

        if (outerCoords.length != 56) throw new IllegalStateException("outer path count=" + outerCoords.length);

        // 颜色：顺时针蓝→黄→绿→红循环，无视4个起点（起点为白色）
        int[] CYCLE = {2, 1, 3, 0}; // 蓝, 黄, 绿, 红
        int[] START_PATH_IDX = {3, 17, 31, 45};
        int[] START_COL_IDX = {3, 0, 2, 1}; // 绿, 红, 蓝, 黄
        int nonStartPos = 0;
        for (int i = 0; i < 56; i++) {
            int r = outerCoords[i][0], c = outerCoords[i][1];
            boolean isStart = false;
            int startColor = -1;
            for (int s = 0; s < 4; s++) {
                if (i == START_PATH_IDX[s]) { isStart = true; startColor = START_COL_IDX[s]; break; }
            }
            if (isStart) {
                CELL_TYPE[r][c] = T_START;
                CELL_COLOR[r][c] = startColor;
            } else {
                CELL_TYPE[r][c] = T_PATH;
                CELL_COLOR[r][c] = CYCLE[(nonStartPos + 2) % 4];
                nonStartPos++;
            }
        }

        // ===== 四色航道（沿中间线到中心，各 6 格）=====
        for (int r = 1; r <= 6; r++) { CELL_TYPE[r][7] = T_HOME; CELL_COLOR[r][7] = 3; }   // 绿航道
        for (int c = 8; c <= 13; c++) { CELL_TYPE[7][c] = T_HOME; CELL_COLOR[7][c] = 0; }   // 红航道
        for (int r = 8; r <= 13; r++) { CELL_TYPE[r][7] = T_HOME; CELL_COLOR[r][7] = 2; }   // 蓝航道
        for (int c = 1; c <= 6; c++) { CELL_TYPE[7][c] = T_HOME; CELL_COLOR[7][c] = 1; }    // 黄航道

        // ===== 中心终点格 =====
        CELL_TYPE[7][7] = T_CENTER;
        CELL_COLOR[7][7] = -1;
    }

    // ===== 游戏逻辑常量 =====
    private static final int HOME_CELL = -1;
    private static final int FINISH_CELL = -2;

    private static final int OUTER_CELLS = 56;          // 原始外圈步数（含起点）
    private static final int EFFECTIVE_OUTER = 51;       // 有效外圈步数（跳过其他色起点 + 终点入口前2格）
    private static final int HOME_RUNWAY_CELLS = 6;
    private static final int TOTAL_PROGRESS = EFFECTIVE_OUTER + HOME_RUNWAY_CELLS; // 57
    private static final int FINISH_PROGRESS = TOTAL_PROGRESS - 1; // 56 = 最后航道格 = 终点
    // 每色终点入口在外圈中的index：走到此格后下一步转向航道而非继续外圈
    private static final int[] TURN_IN_INDEX = { 14, 42, 28, 0 }; // 红(7,14) 黄(7,0) 蓝(14,7) 绿(0,7)

    // 每色起点：绿(0,10), 红(10,14), 蓝(14,4), 黄(4,0)
    private static final int[] START_KEY = {
        10 * N + 14,    // 红 (10,14) → 路径 idx 17
        4 * N + 0,      // 黄 (4,0) → 路径 idx 45
        14 * N + 4,     // 蓝 (14,4) → 路径 idx 31
        0 * N + 10      // 绿 (0,10) → 路径 idx 3
    };

    // 每色起点在外圈赛道中的绝对 index
    private static final int[] START_INDEX = { 17, 45, 31, 3 };

    // 起飞虚线：有效 progress=18 起飞 → progress=32 直达对面
    private static final int FLY_FROM_STEP = 18;
    private static final int FLY_TO_STEP = 30;

    // 飞行虚线碰撞：每色飞行时检查特定位置的特定敌色棋子并撞回
    // {checkRow, checkCol, targetColor}  颜色: 0红 1黄 2蓝 3绿
    private static final int[][] FLY_COLLISION = {
        { 7, 3, 1 },    // 红(0)飞→检查(7,3)的黄(1)
        { 7, 11, 0 },   // 黄(1)飞→检查(7,11)的红(0)
        { 3, 7, 3 },    // 蓝(2)飞→检查(3,7)的绿(3)
        { 11, 7, 2 },   // 绿(3)飞→检查(11,7)的蓝(2)
    };

    // 外圈 56 步 cellKey 列表（从 index 0=绿起点 开始顺时针）
    private static final int[] OUTER_PATH_CELLKEY = new int[OUTER_CELLS];
    static { buildOuterPath(); }

    private static void buildOuterPath() {
        int[][] coords = {
            {0,7},{0,8},{0,9},{0,10},{1,10},{2,10},{3,10},{4,10},{4,11},{4,12},{4,13},
            {4,14},{5,14},{6,14},{7,14},{8,14},{9,14},{10,14},{10,13},{10,12},{10,11},
            {10,10},{11,10},{12,10},{13,10},{14,10},{14,9},{14,8},{14,7},{14,6},{14,5},
            {14,4},{13,4},{12,4},{11,4},{10,4},{10,3},{10,2},{10,1},{10,0},{9,0},{8,0},
            {7,0},{6,0},{5,0},{4,0},{4,1},{4,2},{4,3},{4,4},{3,4},{2,4},{1,4},{0,4},{0,5},{0,6}
        };
        for (int i = 0; i < OUTER_CELLS; i++)
            OUTER_PATH_CELLKEY[i] = coords[i][0] * N + coords[i][1];
    }

    // ===== 有效路径：每色 51 格（自身起点 + 50 个格，跳过其他色起点 + 终点入口后2格）=====
    private static final int[][] EFFECTIVE_PATH = new int[4][EFFECTIVE_OUTER];
    static {
        for (int color = 0; color < 4; color++) {
            EFFECTIVE_PATH[color][0] = OUTER_PATH_CELLKEY[START_INDEX[color]];
            int pos = 1;
            int stopAfter = (TURN_IN_INDEX[color] + 1) % OUTER_CELLS; // 终点入口的下一格停止
            for (int step = 1; step < OUTER_CELLS; step++) {
                int idx = (START_INDEX[color] + step) % OUTER_CELLS;
                if (idx == stopAfter) break; // 到达终点入口的下一格，停止
                boolean isOtherStart = false;
                for (int s = 0; s < 4; s++) {
                    if (s != color && idx == START_INDEX[s]) { isOtherStart = true; break; }
                }
                if (isOtherStart) continue;
                EFFECTIVE_PATH[color][pos] = OUTER_PATH_CELLKEY[idx];
                pos++;
            }
            if (pos != EFFECTIVE_OUTER)
                throw new IllegalStateException("effective path len=" + pos + " for color " + color);
        }
    }

    // 航道 cellKey（6 格，从外向内）
    private static final int[][] HOME_RUNWAY_KEYS = new int[4][HOME_RUNWAY_CELLS];
    static {
        for (int i = 0; i < HOME_RUNWAY_CELLS; i++) {
            HOME_RUNWAY_KEYS[3][i] = (1 + i) * N + 7;
            HOME_RUNWAY_KEYS[0][i] = 7 * N + (13 - i);
            HOME_RUNWAY_KEYS[2][i] = (13 - i) * N + 7;
            HOME_RUNWAY_KEYS[1][i] = 7 * N + (1 + i);
        }
    }

    // ===== 游戏状态 =====
    private String username;
    private int roomId;
    private long seed;
    private String mode;
    private List<String> allPlayers;
    private JFrame parentRoom;

    // 自定义规则参数
    private Set<Integer> takeoffValues = new HashSet<>(java.util.Arrays.asList(6)); // 起飞点数
    private int laps = 1;                    // 行走圈数
    private int effectiveOuter = EFFECTIVE_OUTER;           // 实际外圈步数 = 51 * laps
    private int totalProgress = TOTAL_PROGRESS;             // = effectiveOuter + 6
    private int finishProgress = FINISH_PROGRESS;           // = totalProgress - 1

    private Map<String, Integer> playerColorMap = new LinkedHashMap<>();
    private int currentPlayerIndex = -1;
    private boolean gameOver = false;

    private int[][] pieces = new int[4][4];
    private int[][] pieceProgress = new int[4][4];
    private Map<Integer, List<int[]>> boardPieces = new LinkedHashMap<>();

    private int lastDice = 0;
    private int sixCount = 0;

    // ===== 点击移动状态 =====
    private boolean waitingForClick = false;
    private int movableColor = -1;
    private boolean isAnimating = false;

    // ===== 高亮脉冲动画 =====
    private boolean pulseState = false;
    private javax.swing.Timer pulseTimer;

    private static final ConcurrentHashMap<Integer, AeroChessGame> activeGames = new ConcurrentHashMap<>();

    public AeroChessGame(String username, int roomId, long seed, String mode,
                         List<String> allPlayers, JFrame parentRoom) {
        this.username = username;
        this.roomId = roomId;
        this.seed = seed;
        this.mode = mode;
        this.allPlayers = new ArrayList<>(allPlayers);
        this.parentRoom = parentRoom;

        // 解析自定义模式参数
        if (mode != null && mode.startsWith("自定义;")) {
            String[] parts = mode.split(";");
            if (parts.length >= 2) {
                takeoffValues = new HashSet<>();
                for (String s : parts[1].split(",")) {
                    try { takeoffValues.add(Integer.parseInt(s.trim())); } catch (Exception ignored) {}
                }
                if (takeoffValues.isEmpty()) takeoffValues.add(6);
            }
            if (parts.length >= 3) {
                try { laps = Math.max(1, Integer.parseInt(parts[2].trim())); } catch (Exception ignored) {}
            }
            effectiveOuter = EFFECTIVE_OUTER * laps;
            totalProgress = effectiveOuter + HOME_RUNWAY_CELLS;
            finishProgress = totalProgress - 1;
        }

        activeGames.put(roomId, this);
        initGameState();

        setTitle("飞行棋 · " + (laps > 1 ? laps + "圈·" : "") + mode);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(
                        AeroChessGame.this,
                        "确定要退出本局游戏吗？",
                        "退出确认",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;
                activeGames.remove(roomId);
                stopPulse();
                MessageCenter.disposeActive();
                // 后台通知服务器，触发房间重置（移除机器人+重置准备状态）
                new Thread(() -> {
                    if (roomId > 0) {
                        ServerClient.duelGameResult(roomId, username, "QUIT", System.currentTimeMillis());
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (parentRoom != null && parentRoom.isDisplayable()) {
                            if (parentRoom instanceof AeroChessMatchRoom) {
                                ((AeroChessMatchRoom) parentRoom).resetForNewGame();
                            }
                            parentRoom.setVisible(true);
                            parentRoom.setLocationRelativeTo(null);
                        } else {
                            FishGrabbingHome.showActiveInstance();
                        }
                        dispose();
                    });
                }).start();
            }
        });

        buildUI();
        pack();
        setLocationRelativeTo(null);
        refreshUI();
    }

    private void initGameState() {
        Random rnd = new Random(seed);
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < 4; i++) colors.add(i);
        Collections.shuffle(colors, rnd);
        for (int i = 0; i < allPlayers.size() && i < 4; i++) {
            playerColorMap.put(allPlayers.get(i), colors.get(i));
        }
        currentPlayerIndex = rnd.nextInt(allPlayers.size());
        for (int c = 0; c < 4; c++) {
            Arrays.fill(pieces[c], HOME_CELL);
            Arrays.fill(pieceProgress[c], HOME_CELL);
        }
    }

    private int myColor() {
        Integer c = playerColorMap.get(username);
        return c == null ? 0 : c;
    }
    private String currentPlayer() { return allPlayers.get(currentPlayerIndex); }
    private boolean isMyTurn() {
        return !gameOver && currentPlayerIndex >= 0 && currentPlayer().equals(username);
    }
    private int getCurrentPlayerColor() {
        if (gameOver || currentPlayerIndex < 0) return -1;
        return playerColorMap.getOrDefault(currentPlayer(), -1);
    }

    // ===== UI =====
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private DicePanel dicePanel;
    private JButton rollBtn;
    private JLabel[] playerLabels = new JLabel[4];

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(15, 17, 20));
        topBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        JLabel titleLabel = new JLabel("飞行棋 · " + mode);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 17));
        titleLabel.setForeground(Color.WHITE);
        topBar.add(titleLabel, BorderLayout.WEST);
        statusLabel = new JLabel("游戏中...");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(180, 183, 186));
        topBar.add(statusLabel, BorderLayout.EAST);
        main.add(topBar, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        main.add(boardPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(BG);
        rightPanel.setPreferredSize(new Dimension(230, 0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

        JPanel playersPanel = new JPanel();
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBackground(CARD_BG);
        playersPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 83, 86)), "玩家",
                javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP,
                new Font("Microsoft YaHei", Font.BOLD, 13), Color.WHITE));
        for (int i = 0; i < 4; i++) {
            playerLabels[i] = new JLabel(" ");
            playerLabels[i].setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            playerLabels[i].setForeground(Color.WHITE);
            playerLabels[i].setOpaque(true);
            playerLabels[i].setBackground(CARD_BG);
            playerLabels[i].setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            playerLabels[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            playersPanel.add(playerLabels[i]);
        }
        rightPanel.add(playersPanel);
        rightPanel.add(Box.createVerticalStrut(14));

        dicePanel = new DicePanel();
        dicePanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        dicePanel.setMaximumSize(new Dimension(110, 110));
        rightPanel.add(dicePanel);
        rightPanel.add(Box.createVerticalStrut(10));

        rollBtn = new JButton("掷骰子");
        rollBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        rollBtn.setForeground(Color.WHITE);
        rollBtn.setBackground(new Color(0, 120, 215));
        rollBtn.setFocusPainted(false);
        rollBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        rollBtn.setMaximumSize(new Dimension(150, 42));
        rollBtn.addActionListener(e -> onRollDice());
        rightPanel.add(rollBtn);
        rightPanel.add(Box.createVerticalStrut(10));

        // 空格键掷骰子
        KeyStroke spaceKey = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(spaceKey, "rollDice");
        getRootPane().getActionMap().put("rollDice", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRollDice();
            }
        });

        StringBuilder takeoffStr = new StringBuilder();
        for (int v = 1; v <= 6; v++) if (takeoffValues.contains(v)) { if (takeoffStr.length() > 0) takeoffStr.append("/"); takeoffStr.append(v); }
        JLabel hint = new JLabel("<html><center>摇到" + takeoffStr + "起飞·摇6可再掷<br>掷骰后点击棋子移动<br>同色跳格·飞行线直达<br>连摇3个6全部回机场<br>" + (laps > 1 ? "走" + laps + "圈进终点线<br>" : "") + "按空格键可掷骰子</center></html>", JLabel.CENTER);
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(new Color(150, 153, 156));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(hint);

        main.add(rightPanel, BorderLayout.EAST);
        getContentPane().add(main);
        setSize(1050, 900);
    }

    // ===== 掷骰与回合 =====
    private void onRollDice() {
        if (!isMyTurn()) return;
        if (!dicePanel.isIdle()) return;
        if (waitingForClick) return;
        if (isAnimating) return;

        Random rnd = new Random();
        final int finalDice = rnd.nextInt(6) + 1;

        rollBtn.setEnabled(false);
        dicePanel.roll(finalDice, () -> {
            lastDice = finalDice;
            int color = myColor();
            if (lastDice == 6) sixCount++; else sixCount = 0;

            if (!takeoffValues.contains(lastDice) && allAtHome(color)) { endTurn(); return; }

            List<int[]> moves = getValidMoves(color);
            if (moves.isEmpty()) { endTurn(); return; }

            // 等待玩家点击棋子
            waitingForClick = true;
            movableColor = color;
            statusLabel.setText("掷出 " + finalDice + " 点，请点击要移动的棋子");
            startPulse();
            boardPanel.repaint();
        });
    }

    /** 获取当前可移动的棋子列表：{color, pieceIndex, type(0=移动,1=起飞)} */
    private List<int[]> getValidMoves(int color) {
        List<int[]> moves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (pieces[color][i] >= 0 && pieces[color][i] != FINISH_CELL) {
                moves.add(new int[]{color, i, 0});
            }
        }
        if (takeoffValues.contains(lastDice)) {
            for (int i = 0; i < 4; i++) {
                if (pieces[color][i] == HOME_CELL) {
                    moves.add(new int[]{color, i, 1});
                }
            }
        }
        return moves;
    }

    /** 玩家点击棋子后执行 */
    private void onPieceClicked(int color, int pieceIndex, boolean isHome) {
        if (!waitingForClick || color != movableColor) return;
        if (isAnimating) return;

        waitingForClick = false;
        stopPulse();
        isAnimating = true;

        if (isHome) {
            takeOffAnimated(color, pieceIndex, () -> afterMoveComplete(color));
        } else {
            movePieceAnimated(color, pieceIndex, lastDice, () -> afterMoveComplete(color));
        }
    }

    /** 移动/跳格/飞行全部完成后的收尾 */
    private void afterMoveComplete(int color) {
        isAnimating = false;
        if (checkWin(color)) {
            gameOver(color);
            return;
        }
        // 连摇3个6：所有未到终点的飞机返回机场
        if (sixCount >= 3) {
            sendAllPlanesHome(color);
            endTurn();
            return;
        }
        if (lastDice == 6) {
            refreshUI();
            return;
        }
        endTurn();
    }

    /** 连摇3个6惩罚：所有未到终点的飞机返回机场 */
    private void sendAllPlanesHome(int color) {
        for (int i = 0; i < 4; i++) {
            if (pieces[color][i] != FINISH_CELL) {
                int oldKey = pieces[color][i];
                if (oldKey >= 0) removePieceFromCell(color, i, oldKey);
                pieces[color][i] = HOME_CELL;
                pieceProgress[color][i] = HOME_CELL;
            }
        }
        boardPanel.repaint();
        statusLabel.setText(COLOR_NAMES[color] + "色连续3次6点，所有飞机返回机场！");
    }

    /** 起飞动画：从基地到起点格 */
    private void takeOffAnimated(int color, int pieceIndex, Runnable onComplete) {
        if (pieces[color][pieceIndex] != HOME_CELL) { onComplete.run(); return; }
        int startKey = START_KEY[color];
        pieces[color][pieceIndex] = startKey;
        pieceProgress[color][pieceIndex] = 0;
        putPieceOnCell(color, pieceIndex, startKey);
        boardPanel.repaint();
        javax.swing.Timer t = new javax.swing.Timer(300, e -> {
            checkCollision(color, startKey);
            boardPanel.repaint();
            onComplete.run();
        });
        t.setRepeats(false);
        t.start();
    }

    /** 逐格移动动画：基本移动 */
    private void movePieceAnimated(int color, int pieceIndex, int steps, Runnable onComplete) {
        int oldProg = pieceProgress[color][pieceIndex];
        if (oldProg < 0) { onComplete.run(); return; }

        // 构建每步的进度列表：先正向走到终点，多出的步数从终点连续倒退
        List<Integer> stepProgs = new ArrayList<>();
        int currentProg = oldProg;
        int remaining = steps;
        boolean bounced = false;
        while (remaining > 0 && currentProg < finishProgress) {
            currentProg++;
            remaining--;
            stepProgs.add(currentProg);
        }
        while (remaining > 0) {
            currentProg--;
            remaining--;
            stepProgs.add(currentProg);
            bounced = true;
        }

        final boolean didBounce = bounced;
        animateStepByStep(color, pieceIndex, stepProgs, 0, 200, () -> {
            int finalKey = pieces[color][pieceIndex];
            int finalProg = pieceProgress[color][pieceIndex];

            // 在最终落点检查碰撞
            if (finalKey != FINISH_CELL && finalKey >= 0) {
                checkCollision(color, finalKey);
            }

            // 到达终点
            if (finalProg == finishProgress) {
                removePieceFromCell(color, pieceIndex, finalKey);
                pieces[color][pieceIndex] = FINISH_CELL;
                boardPanel.repaint();
                onComplete.run();
                return;
            }

            // 倒退（回弹）不触发跳格/飞行规则
            if (didBounce) {
                onComplete.run();
                return;
            }

            // 移动后规则检查（跳格、飞行）
            postMoveCheckAnimated(color, pieceIndex, false, onComplete);
        });
    }

    /** 逐格动画核心：按步进列表一格一格移动 */
    private void animateStepByStep(int color, int pieceIndex, List<Integer> stepProgs,
                                   int index, int delay, Runnable onComplete) {
        if (index >= stepProgs.size()) {
            onComplete.run();
            return;
        }
        int newProg = stepProgs.get(index);
        int newKey = positionToCellKey(color, newProg);
        int oldKey = pieces[color][pieceIndex];

        removePieceFromCell(color, pieceIndex, oldKey);
        pieceProgress[color][pieceIndex] = newProg;
        pieces[color][pieceIndex] = newKey;
        if (newKey != FINISH_CELL) {
            putPieceOnCell(color, pieceIndex, newKey);
        }
        boardPanel.repaint();

        javax.swing.Timer t = new javax.swing.Timer(delay, e ->
                animateStepByStep(color, pieceIndex, stepProgs, index + 1, delay, onComplete));
        t.setRepeats(false);
        t.start();
    }

    /** 移动后规则检查（动画版）：同色跳格 + 起飞虚线 */
    private void postMoveCheckAnimated(int color, int pieceIndex, boolean isAfterJump, Runnable onComplete) {
        int prog = pieceProgress[color][pieceIndex];
        if (prog >= effectiveOuter || prog < 0) { onComplete.run(); return; }

        int lapProg = prog % EFFECTIVE_OUTER;
        if (lapProg == FLY_FROM_STEP) {
            doTakeoffAnimated(color, pieceIndex, () -> {
                if (!isAfterJump) {
                    trySameColorJumpAnimated(color, pieceIndex, onComplete);
                } else {
                    onComplete.run();
                }
            });
        } else if (!isAfterJump) {
            trySameColorJumpAnimated(color, pieceIndex, onComplete);
        } else {
            onComplete.run();
        }
    }

    /** 同色跳格（动画版）：逐格跳到下一个同色格 */
    private void trySameColorJumpAnimated(int color, int pieceIndex, Runnable onComplete) {
        int curProg = pieceProgress[color][pieceIndex];
        if (curProg < 0 || curProg >= effectiveOuter) { onComplete.run(); return; }
        int curKey = pieces[color][pieceIndex];
        if (curKey < 0) { onComplete.run(); return; }
        if (cellColorAtKey(curKey) != color) { onComplete.run(); return; }

        // 在当前圈内搜索下一个同色格
        int lapStart = (curProg / EFFECTIVE_OUTER) * EFFECTIVE_OUTER;
        int lapEnd = Math.min(lapStart + EFFECTIVE_OUTER, effectiveOuter);
        int nextProg = -1;
        for (int p = curProg + 1; p < lapEnd; p++) {
            int key = positionToCellKey(color, p);
            if (cellColorAtKey(key) == color) { nextProg = p; break; }
        }
        if (nextProg == -1) { onComplete.run(); return; }
        if (hasHeteroStackBetween(color, curProg, nextProg)) { onComplete.run(); return; }

        // 逐格动画跳到目标
        List<Integer> stepProgs = new ArrayList<>();
        for (int p = curProg + 1; p <= nextProg; p++) stepProgs.add(p);
        animateStepByStep(color, pieceIndex, stepProgs, 0, 120, () -> {
            // 跳格落点检查碰撞
            int toKey = pieces[color][pieceIndex];
            if (toKey >= 0) checkCollision(color, toKey);
            boardPanel.repaint();

            // 跳格后落到起飞线 → 起飞（但不接着跳格）
            int newProg = pieceProgress[color][pieceIndex];
            if (newProg % EFFECTIVE_OUTER == FLY_FROM_STEP) {
                doTakeoffAnimated(color, pieceIndex, onComplete);
            } else {
                onComplete.run();
            }
        });
    }

    /** 起飞虚线（动画版）：从起飞线飞到对面 */
    private void doTakeoffAnimated(int color, int pieceIndex, Runnable onComplete) {
        int fromProg = pieceProgress[color][pieceIndex];
        int lapStart = (fromProg / EFFECTIVE_OUTER) * EFFECTIVE_OUTER;
        int flyToProg = lapStart + FLY_TO_STEP;
        if (fromProg == flyToProg) { onComplete.run(); return; }

        int fromKey = pieces[color][pieceIndex];
        int toKey = positionToCellKey(color, flyToProg);
        removePieceFromCell(color, pieceIndex, fromKey);
        boardPanel.repaint();

        // 飞行延迟（展示起飞过程）
        javax.swing.Timer t = new javax.swing.Timer(400, e -> {
            pieceProgress[color][pieceIndex] = flyToProg;
            pieces[color][pieceIndex] = toKey;
            putPieceOnCell(color, pieceIndex, toKey);
            checkCollision(color, toKey);
            // 飞行虚线碰撞：检查特定位置的特定敌色棋子并撞回
            checkFlyCollision(color);
            boardPanel.repaint();
            onComplete.run();
        });
        t.setRepeats(false);
        t.start();
    }

    /** 飞行虚线碰撞规则：不同色飞行时检查特定位置的特定敌色棋子并撞回机场 */
    private void checkFlyCollision(int flyingColor) {
        int[] collision = FLY_COLLISION[flyingColor];
        int checkRow = collision[0];
        int checkCol = collision[1];
        int targetColor = collision[2];
        int cellKey = checkRow * N + checkCol;

        List<int[]> cell = boardPieces.get(cellKey);
        if (cell == null || cell.isEmpty()) return;

        List<int[]> victims = new ArrayList<>();
        for (int[] p : cell) {
            if (p[0] == targetColor) victims.add(p);
        }
        if (victims.isEmpty()) return;

        for (int[] v : victims) {
            pieces[v[0]][v[1]] = HOME_CELL;
            pieceProgress[v[0]][v[1]] = HOME_CELL;
        }
        cell.removeAll(victims);
        statusLabel.setText(COLOR_NAMES[flyingColor] + "色飞行撞回 " + COLOR_NAMES[targetColor] + "色飞机！");
    }

    /** 相对进度转 cellKey（0=起点, 1-50=外圈每圈51格, 航道6格, 最后=终点） */
    private int positionToCellKey(int color, int progress) {
        if (progress >= totalProgress) return FINISH_CELL;
        if (progress < 0) return HOME_CELL;
        if (progress < effectiveOuter) {
            // 外圈：用模运算获取当前圈对应的格子
            return EFFECTIVE_PATH[color][progress % EFFECTIVE_OUTER];
        }
        int runwayIdx = progress - effectiveOuter;
        if (runwayIdx < HOME_RUNWAY_CELLS) {
            return HOME_RUNWAY_KEYS[color][runwayIdx];
        }
        return FINISH_CELL;
    }

    /**
     * 检查 fromProg+1 到 toProg-1 之间（不含两端）是否有异色叠机
     */
    private boolean hasHeteroStackBetween(int color, int fromProg, int toProg) {
        for (int p = fromProg + 1; p < toProg; p++) {
            int cellKey = positionToCellKey(color, p);
            List<int[]> cell = boardPieces.get(cellKey);
            if (cell == null || cell.size() < 2) continue;
            Set<Integer> colors = new HashSet<>();
            for (int[] piece : cell) colors.add(piece[0]);
            if (colors.size() >= 2) return true;
        }
        return false;
    }

    /** 获取 cellKey 对应格子的颜色 */
    private int cellColorAtKey(int cellKey) {
        int row = cellKey / N;
        int col = cellKey % N;
        if (row < 0 || row >= N || col < 0 || col >= N) return -1;
        return CELL_COLOR[row][col];
    }

    private void checkCollision(int currentColor, int cellKey) {
        if (cellKey < 0) return;
        List<int[]> cell = boardPieces.get(cellKey);
        if (cell == null || cell.isEmpty()) return;
        List<int[]> victims = new ArrayList<>();
        for (int[] p : cell) if (p[0] != currentColor) victims.add(p);
        if (victims.isEmpty()) return;
        for (int[] v : victims) {
            pieces[v[0]][v[1]] = HOME_CELL;
            pieceProgress[v[0]][v[1]] = HOME_CELL;
        }
        cell.removeAll(victims);
        StringBuilder msg = new StringBuilder(COLOR_NAMES[currentColor] + "色击落 ");
        for (int[] v : victims) msg.append(COLOR_NAMES[v[0]]).append("色 ");
        msg.append("飞机！");
        statusLabel.setText(msg.toString());
    }

    private void putPieceOnCell(int color, int pieceIndex, int cellKey) {
        if (cellKey < 0) return;
        boardPieces.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(new int[]{color, pieceIndex});
    }
    private void removePieceFromCell(int color, int pieceIndex, int cellKey) {
        if (cellKey < 0) return;
        List<int[]> cell = boardPieces.get(cellKey);
        if (cell == null) return;
        cell.removeIf(p -> p[0] == color && p[1] == pieceIndex);
    }

    private boolean allAtHome(int color) {
        for (int p : pieces[color]) if (p != HOME_CELL) return false;
        return true;
    }
    private boolean hasPlaneAtHome(int color) {
        for (int p : pieces[color]) if (p == HOME_CELL) return true;
        return false;
    }
    private boolean hasPlaneOnBoard(int color) {
        for (int p : pieces[color]) if (p >= 0) return true;
        return false;
    }
    private boolean checkWin(int color) {
        for (int p : pieces[color]) if (p != FINISH_CELL) return false;
        return true;
    }

    private void endTurn() {
        sixCount = 0;
        currentPlayerIndex = (currentPlayerIndex + 1) % allPlayers.size();
        refreshUI();
    }

    // ===== 机器人 AI =====
    private boolean isBot(String name) {
        return name != null && name.startsWith("机器人");
    }

    /** 如果当前轮到机器人，延迟触发自动回合 */
    private void maybeStartBotTurn() {
        if (gameOver) return;
        if (isAnimating) return;
        String cur = currentPlayer();
        if (!isBot(cur)) return;
        if (!dicePanel.isIdle()) return;
        javax.swing.Timer t = new javax.swing.Timer(800, e -> botRollDice());
        t.setRepeats(false);
        t.start();
    }

    /** 机器人摇骰子并自动行动 */
    private void botRollDice() {
        if (gameOver) return;
        if (!isBot(currentPlayer())) return;
        if (!dicePanel.isIdle()) return;
        if (isAnimating) return;

        Random rnd = new Random();
        final int finalDice = rnd.nextInt(6) + 1;
        final int color = playerColorMap.get(currentPlayer());

        statusLabel.setText(COLOR_NAMES[color] + "色机器人正在摇骰子...");

        dicePanel.roll(finalDice, () -> {
            lastDice = finalDice;
            if (lastDice == 6) sixCount++; else sixCount = 0;

            if (!takeoffValues.contains(lastDice) && allAtHome(color)) { endTurn(); return; }

            if (takeoffValues.contains(lastDice) && hasPlaneAtHome(color)) {
                boolean shouldMoveBoard = false;
                for (int i = 0; i < 4; i++) {
                    if (pieces[color][i] >= 0 && pieceProgress[color][i] > 40) {
                        shouldMoveBoard = true;
                        break;
                    }
                }
                if (!shouldMoveBoard) {
                    int homeIdx = -1;
                    for (int i = 0; i < 4; i++) {
                        if (pieces[color][i] == HOME_CELL) { homeIdx = i; break; }
                    }
                    if (homeIdx >= 0) {
                        isAnimating = true;
                        takeOffAnimated(color, homeIdx, () -> afterMoveComplete(color));
                    } else {
                        endTurn();
                    }
                    return;
                }
            }
            isAnimating = true;
            botChooseMove(color);
        });
    }

    /** 机器人选择棋子移动（AI 策略：优先进度最大的棋子） */
    private void botChooseMove(int color) {
        List<Integer> movable = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (pieces[color][i] >= 0) movable.add(i);
        if (movable.isEmpty()) { isAnimating = false; endTurn(); return; }

        int bestIdx = movable.get(0);
        for (int idx : movable) {
            if (pieceProgress[color][idx] > pieceProgress[color][bestIdx]) {
                bestIdx = idx;
            }
        }
        movePieceAnimated(color, bestIdx, lastDice, () -> afterMoveComplete(color));
    }

    // ===== 高亮脉冲 =====
    private void startPulse() {
        if (pulseTimer != null) pulseTimer.stop();
        pulseState = false;
        pulseTimer = new javax.swing.Timer(500, e -> {
            pulseState = !pulseState;
            boardPanel.repaint();
        });
        pulseTimer.start();
    }

    private void stopPulse() {
        if (pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
    }

    private void refreshUI() {
        for (int i = 0; i < 4; i++) {
            String playerName = "";
            for (Map.Entry<String, Integer> e : playerColorMap.entrySet()) {
                if (e.getValue() == i) { playerName = e.getKey(); break; }
            }
            int finished = 0;
            for (int p : pieces[i]) if (p == FINISH_CELL) finished++;
            String text = String.format("%s %s：%d/4 终点", COLOR_NAMES[i], playerName.isEmpty() ? "（空）" : playerName, finished);
            playerLabels[i].setText(text);
            playerLabels[i].setForeground(Color.WHITE);
            playerLabels[i].setBackground(CARD_BG);
            if (i == playerColorMap.getOrDefault(currentPlayer(), -1)) {
                playerLabels[i].setBackground(new Color(80, 83, 86));
                playerLabels[i].setForeground(PLAYER_COLORS[i]);
            }
        }
        if (!gameOver) {
            String cur = currentPlayer();
            statusLabel.setText("当前回合：" + cur + "（" + COLOR_NAMES[playerColorMap.get(cur)] + "色）");
            rollBtn.setEnabled(isMyTurn() && !waitingForClick && !isAnimating);
            rollBtn.setBackground(isMyTurn() && !waitingForClick && !isAnimating ? new Color(0, 120, 215) : new Color(100, 100, 100));
        }
        boardPanel.repaint();
        maybeStartBotTurn();
    }

    private void gameOver(int winnerColor) {
        gameOver = true;
        stopPulse();
        rollBtn.setEnabled(false);
        MessageCenter.disposeActive();

        String winnerName = "";
        for (Map.Entry<String, Integer> e : playerColorMap.entrySet()) {
            if (e.getValue() == winnerColor) { winnerName = e.getKey(); break; }
        }

        JOptionPane.showMessageDialog(this,
            COLOR_NAMES[winnerColor] + "色 " + winnerName + " 获胜！",
            "游戏结束", JOptionPane.INFORMATION_MESSAGE);

        // 发送结果到服务器，然后自动返回房间
        final String fName = winnerName;
        new Thread(() -> {
            if (roomId > 0) {
                String result = username.equals(fName) ? "WIN" : "FAIL";
                ServerClient.duelGameResult(roomId, username, result, System.currentTimeMillis());
                // 等待服务器处理完毕（移除机器人、重置准备状态）
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
            SwingUtilities.invokeLater(() -> {
                if (parentRoom != null && parentRoom.isDisplayable()) {
                    if (parentRoom instanceof AeroChessMatchRoom) {
                        ((AeroChessMatchRoom) parentRoom).resetForNewGame();
                    }
                    parentRoom.setVisible(true);
                    parentRoom.setLocationRelativeTo(null);
                } else {
                    FishGrabbingHome.showActiveInstance();
                }
                dispose();
            });
        }).start();
    }

    public static void receiveGameOver(int roomId, String results) { }
    public static void receiveMovePush(int roomId, String pushData) {
        AeroChessGame game = activeGames.get(roomId);
        if (game == null) return;
        SwingUtilities.invokeLater(() -> game.refreshUI());
    }

    @Override
    public void dispose() {
        activeGames.remove(roomId);
        stopPulse();
        super.dispose();
    }

    // ===== 棋盘绘制 =====
    class BoardPanel extends JPanel {
        private static final int BOARD_PX = N * CELL + PAD * 2;
        private static final int HANGAR_D = 30;
        private static final int BASE_PAD = CELL / 2;

        // 四色基地起始行列
        private final int[][] baseStarts = {{11, 11}, {0, 0}, {11, 0}, {0, 11}}; // 红, 黄, 蓝, 绿

        BoardPanel() {
            setPreferredSize(new Dimension(BOARD_PX + 20, BOARD_PX + 20));
            setBackground(BG);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleBoardClick(e.getX(), e.getY());
                }
            });
        }

        /** 处理棋盘点击：检测点击了哪个棋子 */
        private void handleBoardClick(int mx, int my) {
            if (!waitingForClick) return;
            int color = movableColor;
            if (color < 0) return;

            int offX = (getWidth() - BOARD_PX) / 2;
            int offY = (getHeight() - BOARD_PX) / 2;
            int ox = PAD, oy = PAD;

            // 1. 检测点击了棋盘上的哪个格子
            int bx = mx - offX - ox;
            int by = my - offY - oy;
            if (bx >= 0 && by >= 0 && bx < N * CELL && by < N * CELL) {
                int col = bx / CELL;
                int row = by / CELL;
                int cellKey = row * N + col;
                List<int[]> cell = boardPieces.get(cellKey);
                if (cell != null) {
                    for (int[] p : cell) {
                        if (p[0] == color) {
                            onPieceClicked(color, p[1], false);
                            return;
                        }
                    }
                }
            }

            // 2. 检测点击了基地内的棋子（起飞）
            if (takeoffValues.contains(lastDice) && hasPlaneAtHome(color)) {
                int startRow = baseStarts[color][0];
                int startCol = baseStarts[color][1];
                int baseX = offX + ox + startCol * CELL + BASE_PAD;
                int baseY = offY + oy + startRow * CELL + BASE_PAD;
                int size = 3 * CELL;
                if (mx >= baseX && mx <= baseX + size &&
                    my >= baseY && my <= baseY + size) {
                    for (int i = 0; i < 4; i++) {
                        if (pieces[color][i] == HOME_CELL) {
                            onPieceClicked(color, i, true);
                            return;
                        }
                    }
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int offX = (getWidth() - BOARD_PX) / 2;
            int offY = (getHeight() - BOARD_PX) / 2;
            g2.translate(offX, offY);

            int ox = PAD, oy = PAD;

            // 1. 棋盘整体背景
            g2.setColor(new Color(240, 234, 218));
            g2.fillRoundRect(ox, oy, N * CELL, N * CELL, 12, 12);

            // 2. 四角基地
            drawCornerBase(g2, 1, ox, oy, 0, 0);      // 黄-左上角
            drawCornerBase(g2, 3, ox, oy, 0, 11);      // 绿-右上角
            drawCornerBase(g2, 2, ox, oy, 11, 0);      // 蓝-左下角
            drawCornerBase(g2, 0, ox, oy, 11, 11);     // 红-右下角

            // 3. 十字形赛道区域背景
            int ARM_W = C1 - C0 + 1;
            g2.setColor(new Color(252, 246, 230));
            g2.fillRoundRect(ox + C0*CELL, oy, ARM_W*CELL, N*CELL, 8, 8);
            g2.fillRoundRect(ox, oy + C0*CELL, N*CELL, ARM_W*CELL, 8, 8);

            // 4. 画十字形内所有格子
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    int t = CELL_TYPE[r][c];
                    if (t == T_EMPTY) continue;
                    int x = ox + c * CELL;
                    int y = oy + r * CELL;
                    int col = CELL_COLOR[r][c];
                    switch (t) {
                        case T_PATH:   drawPathCell(g2, col, x, y); break;
                        case T_START:  drawStartCell(g2, col, x, y); break;
                        case T_HOME:   drawHomeCell(g2, col, x, y); break;
                        case T_CENTER: drawCenterCell(g2, x, y); break;
                    }
                }
            }

            // 5. 画四条飞跃虚线
            drawFlyLines(g2, ox, oy);

            drawAllPieces(g2, ox, oy);
            g2.dispose();
        }

        private void drawPathCell(Graphics2D g2, int colorIdx, int x, int y) {
            g2.setColor(PLAYER_COLORS[colorIdx]);
            g2.fillRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 5, 5);
        }

        private void drawStartCell(Graphics2D g2, int colorIdx, int x, int y) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 5, 5);
            g2.setColor(PLAYER_COLORS[colorIdx]);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 5, 5);
            int d = CELL / 2 - 2;
            int cx = x + (CELL - d) / 2;
            int cy = y + (CELL - d) / 2;
            g2.setColor(PLAYER_COLORS[colorIdx]);
            g2.fillOval(cx, cy, d, d);
        }

        private void drawHomeCell(Graphics2D g2, int colorIdx, int x, int y) {
            g2.setColor(PLAYER_COLORS[colorIdx]);
            g2.fillRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, CELL / 3, CELL / 3);
        }

        private void drawCenterCell(Graphics2D g2, int x, int y) {
            // 顺时针旋转45°：各扇形起始角 -45°
            g2.setColor(PLAYER_COLORS[3]);
            g2.fillArc(x + 2, y + 2, CELL - 4, CELL - 4, 45, 90);
            g2.setColor(PLAYER_COLORS[0]);
            g2.fillArc(x + 2, y + 2, CELL - 4, CELL - 4, 315, 90);
            g2.setColor(PLAYER_COLORS[2]);
            g2.fillArc(x + 2, y + 2, CELL - 4, CELL - 4, 225, 90);
            g2.setColor(PLAYER_COLORS[1]);
            g2.fillArc(x + 2, y + 2, CELL - 4, CELL - 4, 135, 90);
        }

        /** 画四条飞跃虚线 */
        private void drawFlyLines(Graphics2D g2, int ox, int oy) {
            float[] dash = {10f, 6f};
            BasicStroke stroke = new BasicStroke(3f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, dash, 0f);
            Object oldStroke = g2.getStroke();
            g2.setStroke(stroke);

            int[][] lines = {
                {3, 4, 4, 10},     // 蓝
                {4, 11, 10, 10},   // 黄
                {11, 10, 10, 4},   // 绿
                {10, 3, 4, 4},     // 红
            };
            Color[] lineColors = { BLUE, YELLOW, GREEN, RED };

            for (int i = 0; i < lines.length; i++) {
                int r1 = lines[i][0], c1 = lines[i][1];
                int r2 = lines[i][2], c2 = lines[i][3];
                int x1 = ox + c1 * CELL + CELL / 2;
                int y1 = oy + r1 * CELL + CELL / 2;
                int x2 = ox + c2 * CELL + CELL / 2;
                int y2 = oy + r2 * CELL + CELL / 2;
                g2.setColor(lineColors[i]);
                g2.drawLine(x1, y1, x2, y2);
            }
            g2.setStroke((Stroke) oldStroke);
        }

        /** 画四角基地 */
        private void drawCornerBase(Graphics2D g2, int colorIdx, int ox, int oy, int startRow, int startCol) {
            int bx = ox + startCol * CELL + BASE_PAD;
            int by = oy + startRow * CELL + BASE_PAD;
            int size = 3 * CELL;
            g2.setColor(PLAYER_COLORS[colorIdx]);
            g2.fillRoundRect(bx, by, size, size, 14, 14);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(bx, by, size, size, 14, 14);
            int sp = size / 4;
            int[][] slots = {
                { bx + sp,       by + sp },
                { bx + size - sp, by + sp },
                { bx + sp,       by + size - sp },
                { bx + size - sp, by + size - sp }
            };
            for (int[] sc : slots) {
                g2.setColor(Color.WHITE);
                g2.fillOval(sc[0] - HANGAR_D/2, sc[1] - HANGAR_D/2, HANGAR_D, HANGAR_D);
                g2.setColor(new Color(100, 100, 100));
                g2.setStroke(new BasicStroke(1));
                g2.drawOval(sc[0] - HANGAR_D/2, sc[1] - HANGAR_D/2, HANGAR_D, HANGAR_D);
            }
            int count = 0;
            for (int i = 0; i < 4; i++) if (pieces[colorIdx][i] == HOME_CELL) count++;
            boolean homeClickable = waitingForClick && colorIdx == movableColor && takeoffValues.contains(lastDice);
            int slotIdx = 0;
            // 机场中的棋子
            for (int i = 0; i < 4; i++) {
                if (pieces[colorIdx][i] == HOME_CELL) {
                    drawPieceOnCell(g2, colorIdx, slots[slotIdx][0], slots[slotIdx][1], HANGAR_D + 6, homeClickable);
                    slotIdx++;
                }
            }
            // 已到终点的棋子显示打勾
            for (int i = 0; i < 4; i++) {
                if (pieces[colorIdx][i] == FINISH_CELL) {
                    drawCheckmark(g2, colorIdx, slots[slotIdx][0], slots[slotIdx][1], HANGAR_D + 6, 1);
                    slotIdx++;
                }
            }
        }

        private void drawAllPieces(Graphics2D g2, int ox, int oy) {
            for (Map.Entry<Integer, List<int[]>> entry : boardPieces.entrySet()) {
                int cellKey = entry.getKey();
                int r = cellKey / N, c = cellKey % N;
                if (r < 0 || r >= N || c < 0 || c >= N) continue;
                int x = ox + c * CELL;
                int y = oy + r * CELL;
                List<int[]> ps = entry.getValue();
                for (int i = 0; i < ps.size(); i++) {
                    int[] p = ps.get(i);
                    int dx = (i % 2) * 5 - 2;
                    int dy = (i / 2) * 5 - 2;
                    boolean clickable = waitingForClick && p[0] == movableColor;
                    drawPieceOnCell(g2, p[0], x + CELL / 2 + dx, y + CELL / 2 + dy, CELL - 12, clickable);
                }
            }
        }

        /** 画终点打勾标记 */
        private void drawCheckmark(Graphics2D g2, int colorIdx, int cx, int cy, int size, int count) {
            int r = size / 2;
            // 背景圆（彩色）
            g2.setColor(PIECE_COLORS[colorIdx]);
            g2.fillOval(cx - r, cy - r, size, size);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(cx - r, cy - r, size, size);
            // 打勾
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] xPts = { cx - (int)(r * 0.45), cx - (int)(r * 0.1), cx + (int)(r * 0.5) };
            int[] yPts = { cy + (int)(r * 0.05), cy + (int)(r * 0.4), cy - (int)(r * 0.35) };
            g2.drawPolyline(xPts, yPts, 3);
            // 数量标记
            if (count > 1) {
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
                String num = "x" + count;
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(num, cx + r - fm.stringWidth(num) - 2, cy - r + fm.getAscent() + 2);
            }
        }

        private void drawPieceOnCell(Graphics2D g2, int colorIdx, int cx, int cy, int size, boolean clickable) {
            int r = size / 2;

            // ===== 高亮：当前回合玩家的棋子 =====
            int currentColor = getCurrentPlayerColor();
            if (colorIdx == currentColor && !gameOver) {
                // 柔光底圈
                int glowR = r + 3;
                g2.setColor(new Color(255, 235, 100, 90));
                g2.fillOval(cx - glowR, cy - glowR, glowR * 2, glowR * 2);
            }

            // ===== 可点击脉冲：等待玩家点击时 =====
            if (clickable) {
                int glowR = r + 5;
                g2.setColor(pulseState ? new Color(255, 255, 255, 220) : new Color(255, 255, 255, 80));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval(cx - glowR, cy - glowR, glowR * 2, glowR * 2);
            }

            // 外圈底色（白色填充）
            g2.setColor(Color.WHITE);
            g2.fillOval(cx - r, cy - r, size, size);

            // 外层粗描边圆环（彩色）
            Color pc = PIECE_COLORS[colorIdx];
            int outerRing = Math.max(3, size / 10);
            g2.setStroke(new BasicStroke(outerRing));
            g2.setColor(pc);
            g2.drawOval(cx - r + outerRing / 2, cy - r + outerRing / 2,
                    size - outerRing, size - outerRing);

            // 内层细描边圆环（同色）
            int innerRing = Math.max(1, outerRing / 3);
            int pad = Math.max(4, size / 6);
            int ir = r - pad;
            g2.setStroke(new BasicStroke(innerRing));
            g2.drawOval(cx - ir, cy - ir, ir * 2, ir * 2);

            // 中心区域画彩色飞机剪影
            int bodySize = ir * 2 - 4;
            if (bodySize < 8) return;

            Graphics2D g3 = (Graphics2D) g2.create();
            g3.translate(cx, cy);
            double[] ROT = { -Math.PI * 3 / 4, Math.PI / 4, -Math.PI / 4, Math.PI * 3 / 4 };
            g3.rotate(ROT[colorIdx]);
            g3.setColor(pc);

            int L = bodySize;
            int t = Math.max(2, bodySize / 11);
            int W = bodySize;
            int Tw = Math.max(4, bodySize / 3);

            // 1. 机身
            g3.fillOval(-t, -L / 2, t * 2, L);

            // 2. 主翼
            int[] wingR = { t, t, W / 2 };
            int[] wingRY = { -L / 6, L / 6, 0 };
            g3.fillPolygon(wingR, wingRY, 3);
            int[] wingL = { -t, -t, -W / 2 };
            int[] wingLY = { -L / 6, L / 6, 0 };
            g3.fillPolygon(wingL, wingLY, 3);

            // 3. 尾翼
            int[] tailR = { t, t, Tw / 2 };
            int[] tailRY = { L / 4, L / 2 - t, L / 2 - t };
            g3.fillPolygon(tailR, tailRY, 3);
            int[] tailL = { -t, -t, -Tw / 2 };
            int[] tailLY = { L / 4, L / 2 - t, L / 2 - t };
            g3.fillPolygon(tailL, tailLY, 3);

            // 4. 机头深色圆点
            g3.setColor(new Color(0, 0, 0, 130));
            int nR = Math.max(1, t - 1);
            g3.fillOval(-nR, -L / 2 + 1, nR * 2, nR * 2);

            g3.dispose();
        }
    }

    // ===== 视觉化骰子面板 =====
    class DicePanel extends JPanel {
        private static final int DICE_SIZE = 80;
        private int value = 0;
        private javax.swing.Timer animTimer;
        private int animTick = 0;
        private int targetValue = 0;
        private Runnable onComplete;

        DicePanel() {
            setPreferredSize(new Dimension(DICE_SIZE + 16, DICE_SIZE + 16));
            setMaximumSize(new Dimension(DICE_SIZE + 16, DICE_SIZE + 16));
            setOpaque(false);
        }

        boolean isIdle() { return animTimer == null || !animTimer.isRunning(); }

        void setValue(int v) {
            this.value = v;
            this.targetValue = v;
            repaint();
        }

        /** 滚动动画：先快速切换随机值，~600ms 后停在 finalValue */
        void roll(int finalValue, Runnable onDone) {
            if (animTimer != null) animTimer.stop();
            this.targetValue = finalValue;
            this.onComplete = onDone;
            this.animTick = 0;
            animTimer = new javax.swing.Timer(55, e -> {
                animTick++;
                if (animTick >= 11) {
                    value = finalValue;
                    ((javax.swing.Timer) e.getSource()).stop();
                    animTimer = null;
                    repaint();
                    if (onComplete != null) onComplete.run();
                } else {
                    value = (int) (Math.random() * 6) + 1;
                    repaint();
                }
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int size = DICE_SIZE;
            int x = cx - size / 2;
            int y = cy - size / 2;

            // 阴影
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRoundRect(x + 3, y + 4, size, size, 14, 14);

            // 骰子主体
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, size, size, 14, 14);
            g2.setColor(new Color(170, 170, 175));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, size, size, 14, 14);

            if (value >= 1 && value <= 6) {
                drawPips(g2, value, x, y, size);
            } else {
                g2.setColor(new Color(190, 190, 195));
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 36));
                FontMetrics fm = g2.getFontMetrics();
                String q = "?";
                int qx = cx - fm.stringWidth(q) / 2;
                int qy = cy + fm.getAscent() / 2 - 6;
                g2.drawString(q, qx, qy);
            }

            g2.dispose();
        }

        private void drawPips(Graphics2D g2, int v, int x, int y, int size) {
            int pad = size / 6;
            int cell = (size - 2 * pad) / 2;
            int pipSize = size / 9;

            int[][] centers = new int[9][2];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    centers[i * 3 + j][0] = x + pad + cell * j;
                    centers[i * 3 + j][1] = y + pad + cell * i;
                }
            }

            int[][] layout = {
                {},
                { 4 },
                { 0, 8 },
                { 0, 4, 8 },
                { 0, 2, 6, 8 },
                { 0, 2, 4, 6, 8 },
                { 0, 2, 3, 5, 6, 8 }
            };

            g2.setColor(new Color(20, 20, 23));
            for (int idx : layout[v]) {
                int px = centers[idx][0];
                int py = centers[idx][1];
                g2.fillOval(px - pipSize / 2, py - pipSize / 2, pipSize, pipSize);
            }
        }
    }
}
