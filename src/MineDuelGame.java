import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫雷对决游戏页面
 * 左侧：自己的棋盘（可操作）
 * 右侧：对手棋盘（只读，实时同步）
 */
public class MineDuelGame extends JFrame {

    private static final int HIDDEN = 0, REVEALED = 1, FLAGGED = 2;
    private static final Color BG = new Color(50, 53, 56);
    private static final Color CARD_BG = new Color(60, 63, 65);
    private static final Color[] NUM_CLR = {
        null, Color.BLUE, new Color(0, 100, 0), Color.RED,
        new Color(0, 0, 139), new Color(128, 0, 0),
        Color.CYAN, Color.BLACK, Color.GRAY
    };

    // 游戏参数
    private int ROWS, COLS, MINES;
    private String mode;
    private String username;
    private int roomId;
    private long seed;

    // 自己的棋盘
    private int[][] board;
    private int[][] state;
    private JButton[][] buttons;
    private boolean gameOver = false;
    private boolean localFinished = false;  // 本地玩家已完成（失败/胜利/超时），但游戏整体可能还在等别人
    private boolean iFinished = false;
    private boolean iFailed = false;
    private long myFinishTime = 0;
    private int flagsPlaced = 0;

    // 计时器
    private long startTime;
    private long elapsedMs = 0;
    private javax.swing.Timer timer;
    private JLabel timerLabel;
    private long timeLimitMs;  // 用时限制（毫秒）

    // 结果轮询（兜底机制，防止推送未到达）
    private javax.swing.Timer resultPollTimer;
    private boolean resultDialogShown = false;

    // 对手
    private List<String> opponents;
    // opponentName -> {state: int[][], result: "WIN:time" or "FAIL:time", finished: boolean, failed: boolean}
    private Map<String, OpponentData> opponentData;

    // 对手面板
    private JPanel opponentsPanel;

    // 活跃游戏实例（用于接收推送）
    private static final ConcurrentHashMap<Integer, MineDuelGame> activeGames = new ConcurrentHashMap<>();

    // 父房间引用（用于返回房间）
    private JFrame parentRoom;

    // 状态标签
    private JLabel statusLabel;

    // 九宫格高亮（鼠标悬停时高亮以目标格为中心的3x3区域）
    private int hoverR = -1, hoverC = -1;
    private final Set<String> highlightedCells = new HashSet<>();
    private final Map<String, Color> savedCellColors = new HashMap<>();

    // 机器人模拟
    private boolean hasBots = false;
    private int botDifficulty = 0;  // 0=初级(纯随机), 1=中级(简单推理), 2=高级(概率推算)
    private final Map<String, BotState> botStates = new LinkedHashMap<>();

    /** 单个机器人的游戏状态 */
    static class BotState {
        int[][] board;
        int[][] state;
        int rows, cols, mines;
        int flagsPlaced = 0;
        int moveCount = 0;
        boolean finished = false;
        boolean failed = false;
        long startTime;
        javax.swing.Timer moveTimer;
    }

    public MineDuelGame(String username, int roomId, long seed, String mode,
                        List<String> allPlayers, JFrame parentRoom) {
        this.username = username;
        this.roomId = roomId;
        // 使用服务器统一分发的种子，保证所有玩家棋盘一致
        this.seed = seed;
        this.mode = mode;
        this.parentRoom = parentRoom;

        // 解析难度
        parseDifficulty(mode);

        // 提取对手列表
        this.opponents = new ArrayList<>();
        for (String p : allPlayers) {
            if (!p.equals(username)) {
                opponents.add(p);
            }
        }

        // 初始化对手数据
        this.opponentData = new LinkedHashMap<>();
        for (String opp : opponents) {
            opponentData.put(opp, new OpponentData());
        }

        // 注册活跃游戏
        activeGames.put(roomId, this);

        // 生成棋盘（用种子保证所有玩家棋盘一致）
        generateBoard();

        setTitle("扫雷对决 · " + mode);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                // 确保所有后台任务停止
                stopTimer();
                if (resultPollTimer != null) resultPollTimer.stop();
                activeGames.remove(roomId);
                // 回到房间
                if (parentRoom != null && parentRoom.isDisplayable()) {
                    if (parentRoom instanceof MineMatchRoom) {
                        ((MineMatchRoom) parentRoom).resetForNewGame();
                    }
                    parentRoom.setVisible(true);
                    parentRoom.setLocationRelativeTo(null);
                }
            }
        });

        buildUI();
        pack();
        setLocationRelativeTo(null);

        // 启动计时器
        startGameTimer();

        // 检测并启动机器人模拟
        startBotSimulation();
    }

    /** 解析难度字符串获取棋盘尺寸和用时限制 */
    private void parseDifficulty(String mode) {
        if ("初级".equals(mode)) {
            ROWS = 9; COLS = 9; MINES = 10;
            timeLimitMs = 5 * 60 * 1000L;  // 5分钟
            botDifficulty = 0;
        } else if ("中级".equals(mode)) {
            ROWS = 16; COLS = 16; MINES = 40;
            timeLimitMs = 10 * 60 * 1000L;  // 10分钟
            botDifficulty = 1;
        } else if ("高级".equals(mode)) {
            ROWS = 16; COLS = 30; MINES = 99;
            timeLimitMs = 20 * 60 * 1000L;  // 20分钟
            botDifficulty = 2;
        } else if (mode.startsWith("自定义-")) {
            // 格式: "自定义-16x16(40雷)"
            try {
                String spec = mode.substring("自定义-".length());
                int xIdx = spec.indexOf('x');
                int pIdx = spec.indexOf('(');
                int rIdx = spec.indexOf("雷");
                ROWS = Integer.parseInt(spec.substring(0, xIdx));
                COLS = Integer.parseInt(spec.substring(xIdx + 1, pIdx));
                MINES = Integer.parseInt(spec.substring(pIdx + 1, rIdx));
            } catch (Exception e) {
                ROWS = 9; COLS = 9; MINES = 10;
            }
            timeLimitMs = 10 * 60 * 1000L;  // 自定义默认10分钟
        } else {
            ROWS = 9; COLS = 9; MINES = 10;
            timeLimitMs = 5 * 60 * 1000L;
        }
    }

    /** 用种子生成棋盘 */
    private void generateBoard() {
        board = new int[ROWS][COLS];
        state = new int[ROWS][COLS];
        Random rnd = new Random(seed);
        int placed = 0;
        while (placed < MINES) {
            int r = rnd.nextInt(ROWS);
            int c = rnd.nextInt(COLS);
            if (board[r][c] != -1) {
                board[r][c] = -1;
                placed++;
            }
        }
        // 计算数字
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == -1) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && board[nr][nc] == -1) count++;
                    }
                }
                board[r][c] = count;
            }
        }
    }

    /** 构建UI */
    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);

        // 顶部栏
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(40, 43, 46));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        JLabel titleLabel = new JLabel("扫雷对决 · " + mode);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topRight.setBackground(new Color(40, 43, 46));
        timerLabel = new JLabel("用时: 00:00");
        timerLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
        timerLabel.setForeground(new Color(255, 200, 50));
        topRight.add(timerLabel);

        statusLabel = new JLabel("游戏中...");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 183, 186));
        topRight.add(statusLabel);

        topBar.add(topRight, BorderLayout.EAST);
        main.add(topBar, BorderLayout.NORTH);

        // 中央分左右
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerSize(3);
        splitPane.setBackground(BG);
        splitPane.setLeftComponent(buildOwnBoardPanel());
        splitPane.setRightComponent(buildOpponentsPanel());
        splitPane.setResizeWeight(0.55); // 左边占比

        main.add(splitPane, BorderLayout.CENTER);

        // 底���提示
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.setBackground(new Color(40, 43, 46));
        JLabel hint = new JLabel("左键翻开 | 右键插旗 | 最快完成者获胜");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        hint.setForeground(new Color(150, 153, 156));
        bottom.add(hint);
        main.add(bottom, BorderLayout.SOUTH);

        getContentPane().add(main);

        // 大小根据难度调整
        int cs = (ROWS > 16 || COLS > 16) ? 28 : 38;
        int ownW = COLS * cs + 20;
        int ownH = ROWS * cs + 60;
        int oppW = opponents.size() > 1 ? 280 : 200;
        int oppH = ownH;
        int winW = ownW + oppW + 50;
        int winH = Math.max(ownH, 500) + 80;

        setSize(winW, winH);
    }

    /** 构建自己的棋盘面板 */
    private JPanel buildOwnBoardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));

        JLabel ownLabel = new JLabel(" 我的棋盘", JLabel.LEFT);
        ownLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        ownLabel.setForeground(new Color(0, 180, 255));
        panel.add(ownLabel, BorderLayout.NORTH);

        int cs = (ROWS > 16 || COLS > 16) ? 28 : 38;
        int fontSize = (ROWS > 16 || COLS > 16) ? 12 : 16;

        JPanel gridPanel = new JPanel(new GridLayout(ROWS, COLS, 1, 1));
        gridPanel.setBackground(Color.DARK_GRAY);
        buttons = new JButton[ROWS][COLS];

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(cs, cs));
                btn.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setFocusPainted(false);
                btn.setOpaque(true);
                int row = r, col = c;
                btn.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        if (gameOver || localFinished) return;
                        if (e.getButton() == MouseEvent.BUTTON3) rightClick(row, col);
                        else if (e.getButton() == MouseEvent.BUTTON1) leftClick(row, col);
                    }
                    public void mouseEntered(MouseEvent e) {
                        if (gameOver || localFinished) return;
                        clearHighlight();
                        hoverR = row;
                        hoverC = col;
                        applyHighlight(row, col);
                    }
                    public void mouseExited(MouseEvent e) {
                        if (gameOver || localFinished) return;
                        clearHighlight();
                        hoverR = -1;
                        hoverC = -1;
                    }
                });
                buttons[r][c] = btn;
                gridPanel.add(btn);
            }
        }

        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    /** 构建对手面板 */
    private JPanel buildOpponentsPanel() {
        opponentsPanel = new JPanel();
        opponentsPanel.setBackground(BG);
        opponentsPanel.setLayout(new BoxLayout(opponentsPanel, BoxLayout.Y_AXIS));
        opponentsPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));

        JLabel oppLabel = new JLabel(" 对手状态", JLabel.LEFT);
        oppLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        oppLabel.setForeground(new Color(255, 150, 50));
        oppLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        opponentsPanel.add(oppLabel);
        opponentsPanel.add(Box.createVerticalStrut(10));

        for (String opp : opponents) {
            opponentsPanel.add(buildOpponentCard(opp));
            opponentsPanel.add(Box.createVerticalStrut(10));
        }

        JScrollPane scroll = new JScrollPane(opponentsPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    /** 单个对手卡片（用JPanel绘制mini棋盘） */
    private JPanel buildOpponentCard(String oppName) {
        OpponentData data = opponentData.get(oppName);

        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 83, 86), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setMaximumSize(new Dimension(260, 400));

        // 对手名字和状态
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        JLabel nameLabel = new JLabel(oppName);
        nameLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        nameLabel.setForeground(Color.WHITE);
        header.add(nameLabel, BorderLayout.WEST);

        data.statusLabel = new JLabel("");
        data.statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        data.statusLabel.setForeground(new Color(150, 153, 156));
        header.add(data.statusLabel, BorderLayout.EAST);

        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(header);
        card.add(Box.createVerticalStrut(6));

        // Mini棋盘
        data.boardPanel = new OpponentBoardPanel(oppName);
        int miniSize = (ROWS > 16 || COLS > 16) ? 10 : 16;
        int pw = COLS * miniSize + 12;
        int ph = ROWS * miniSize + 12;
        data.boardPanel.setPreferredSize(new Dimension(pw, ph));
        data.boardPanel.setMaximumSize(new Dimension(pw, ph));
        data.boardPanel.setMinimumSize(new Dimension(pw, ph));
        data.boardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(data.boardPanel);

        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /** 对手mini棋盘面板（绘制） */
    class OpponentBoardPanel extends JPanel {
        private String oppName;

        OpponentBoardPanel(String oppName) {
            this.oppName = oppName;
            setBackground(new Color(45, 48, 51));
            setBorder(BorderFactory.createLineBorder(new Color(70, 73, 76), 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            OpponentData data = opponentData.get(oppName);
            if (data == null) return;

            int miniSize = (ROWS > 16 || COLS > 16) ? 10 : 16;
            int startX = (getWidth() - COLS * miniSize) / 2;
            int startY = (getHeight() - ROWS * miniSize) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int x = startX + c * miniSize;
                    int y = startY + r * miniSize;
                    int st = data.state[r][c];

                    if (st == HIDDEN) {
                        g2.setColor(new Color(70, 73, 76));
                        g2.fillRect(x + 1, y + 1, miniSize - 2, miniSize - 2);
                    } else if (st == FLAGGED) {
                        g2.setColor(new Color(180, 50, 50));
                        g2.fillRect(x + 1, y + 1, miniSize - 2, miniSize - 2);
                        // 画小旗
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, miniSize - 4));
                        FontMetrics fm = g2.getFontMetrics();
                        String flag = "\uD83D\uDEA9";
                        int fw = fm.stringWidth(flag);
                        int fh = fm.getAscent();
                        g2.drawString(flag, x + (miniSize - fw) / 2, y + (miniSize + fh) / 2 - 2);
                    } else if (st == REVEALED) {
                        int val = data.values[r][c];
                        if (val == -1) {
                            // 雷
                            g2.setColor(new Color(255, 100, 100));
                            g2.fillRect(x + 1, y + 1, miniSize - 2, miniSize - 2);
                            g2.setColor(Color.BLACK);
                            g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, miniSize - 4));
                            FontMetrics fm = g2.getFontMetrics();
                            String bomb = "\uD83D\uDCA3";
                            int bw = fm.stringWidth(bomb);
                            int bh = fm.getAscent();
                            g2.drawString(bomb, x + (miniSize - bw) / 2, y + (miniSize + bh) / 2 - 2);
                        } else if (val == 0) {
                            g2.setColor(new Color(200, 200, 200));
                            g2.fillRect(x + 1, y + 1, miniSize - 2, miniSize - 2);
                        } else {
                            g2.setColor(Color.WHITE);
                            g2.fillRect(x + 1, y + 1, miniSize - 2, miniSize - 2);
                            // 画数字
                            if (miniSize >= 14) {
                                g2.setColor(NUM_CLR[Math.min(val, NUM_CLR.length - 1)]);
                                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, miniSize - 4));
                                FontMetrics fm = g2.getFontMetrics();
                                String num = String.valueOf(val);
                                int nw = fm.stringWidth(num);
                                int nh = fm.getAscent();
                                g2.drawString(num, x + (miniSize - nw) / 2, y + (miniSize + nh) / 2 - 2);
                            } else {
                                // 太小就不画数字，用颜色
                                g2.setColor(NUM_CLR[Math.min(val, NUM_CLR.length - 1)]);
                                g2.fillRect(x + 3, y + 3, miniSize - 6, miniSize - 6);
                            }
                        }
                    }

                    // 网格线
                    g2.setColor(new Color(50, 53, 56));
                    g2.drawRect(x, y, miniSize, miniSize);
                }
            }
            g2.dispose();
        }
    }

    /** 对手数据 */
    class OpponentData {
        int[][] state;          // 棋盘状态 (HIDDEN/REVEALED/FLAGGED)
        int[][] values;         // 已揭示格子的值（-2=未知，-1=雷，0-8=数字）
        JPanel boardPanel;      // mini棋盘面板引用
        JLabel statusLabel;     // 状态标签引用
        String result = null;   // "WIN:time" or "FAIL:time"
        boolean finished = false;
        boolean failed = false;

        OpponentData() {
            state = new int[ROWS][COLS];
            values = new int[ROWS][COLS];
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    values[r][c] = -2; // -2 表示未知
        }
    }

    // ===== 游戏操作 =====

    private void leftClick(int r, int c) {
        if (state[r][c] == REVEALED || state[r][c] == FLAGGED) return;
        if (board[r][c] == -1) {
            // 踩雷：失败
            revealMines(r, c);
            localFinished = true;
            iFailed = true;
            iFinished = true;
            myFinishTime = elapsedMs;
            stopTimer();
            statusLabel.setText("你踩雷了！等待其他玩家完成...");
            // 发送失败结果
            sendResult("FAIL");
            return;
        }
        reveal(r, c);
        // 同步到服务器
        syncReveal(r, c, board[r][c]);
        checkWin();
    }

    private void rightClick(int r, int c) {
        if (state[r][c] == REVEALED) return;
        if (state[r][c] == HIDDEN) {
            state[r][c] = FLAGGED;
            flagsPlaced++;
            buttons[r][c].setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            buttons[r][c].setText("\uD83D\uDEA9");
            buttons[r][c].setForeground(Color.RED);
            syncFlag(r, c);
        } else {
            state[r][c] = HIDDEN;
            flagsPlaced--;
            buttons[r][c].setText("");
            syncUnflag(r, c);
        }
    }

    /** 九宫格高亮：以(r,c)为中心的3x3区域 */
    private void applyHighlight(int r, int c) {
        highlightedCells.clear();
        savedCellColors.clear();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS) {
                    if (state[nr][nc] == HIDDEN || state[nr][nc] == FLAGGED) {
                        String key = nr + "," + nc;
                        // 保存原始背景色再设高亮
                        savedCellColors.put(key, buttons[nr][nc].getBackground());
                        buttons[nr][nc].setBackground(new Color(100, 180, 255));
                        highlightedCells.add(key);
                    }
                }
            }
        }
    }

    /** 清除九宫格高亮 */
    private void clearHighlight() {
        for (String key : highlightedCells) {
            String[] parts = key.split(",");
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            if (state[r][c] != REVEALED) {
                // 恢复原始背景色
                buttons[r][c].setBackground(savedCellColors.get(key));
            }
        }
        highlightedCells.clear();
        savedCellColors.clear();
    }

    private void reveal(int r, int c) {
        if (r < 0 || r >= ROWS || c < 0 || c >= COLS || state[r][c] != HIDDEN || board[r][c] == -1) return;
        state[r][c] = REVEALED;
        JButton b = buttons[r][c];
        int fontSize = (ROWS > 16 || COLS > 16) ? 12 : 16;
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
        b.setBackground(Color.WHITE);
        b.setEnabled(false);
        if (board[r][c] > 0) {
            b.setText(String.valueOf(board[r][c]));
            b.setForeground(NUM_CLR[Math.min(board[r][c], NUM_CLR.length - 1)]);
        } else {
            b.setText("");
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                    if (dr != 0 || dc != 0) reveal(r + dr, c + dc);
        }
    }

    private void revealMines(int clickedR, int clickedC) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == -1 && state[r][c] != REVEALED) {
                    buttons[r][c].setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
                    buttons[r][c].setText("\uD83D\uDCA3");
                    buttons[r][c].setForeground(Color.RED);
                    buttons[r][c].setBackground(new Color(255, 150, 150));
                    buttons[r][c].setEnabled(false);
                    // 不再为每颗雷都发同步请求，避免海量请求阻塞结果上报
                }
            }
        }
        // 只同步被点击的那颗雷的位置
        if (clickedR >= 0 && clickedC >= 0) {
            syncReveal(clickedR, clickedC, -1);
            // 标记点击的雷
            buttons[clickedR][clickedC].setBackground(Color.RED);
        }
    }

    private void checkWin() {
        int unrevealed = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (board[r][c] != -1 && state[r][c] != REVEALED) unrevealed++;
        if (unrevealed == 0) {
            localFinished = true;
            iFinished = true;
            iFailed = false;
            myFinishTime = elapsedMs;
            stopTimer();
            statusLabel.setText("你完成了！等待其他玩家...");
            sendResult("WIN");
        }
    }

    /** 用时耗尽：强制判输 */
    private void triggerTimeOut() {
        localFinished = true;
        iFailed = true;
        iFinished = true;
        myFinishTime = elapsedMs;
        stopTimer();
        statusLabel.setText("时间到！你超时了！");
        // 揭示所有雷
        revealMines(-1, -1);
        sendResult("FAIL");
    }

    // ===== 与服务器同步 =====

    private void syncReveal(int r, int c, int value) {
        new Thread(() -> ServerClient.duelGameReveal(roomId, username, r, c, value)).start();
    }

    private void syncFlag(int r, int c) {
        new Thread(() -> ServerClient.duelGameFlag(roomId, username, r, c)).start();
    }

    private void syncUnflag(int r, int c) {
        new Thread(() -> ServerClient.duelGameUnflag(roomId, username, r, c)).start();
    }

    private void sendResult(String result) {
        new Thread(() -> {
            String resp = ServerClient.duelGameResult(roomId, username, result, myFinishTime);
            // 如果自己是最后一个上报的，服务器直接返回 ALL_DONE，不走轮询
            if (resp != null && resp.startsWith("SUCCESS|ALL_DONE|")) {
                String resultsData = resp.substring("SUCCESS|ALL_DONE|".length());
                SwingUtilities.invokeLater(() -> {
                    if (!resultDialogShown) {
                        handleGameOver(resultsData);
                    }
                });
            }
        }).start();
        // 启动轮询兜底，每 1.5 秒检查一次结果
        startResultPolling();
    }

    /** 启动结果轮询（兜底，防止 PUSH 丢失导致永远卡住） */
    private void startResultPolling() {
        if (resultPollTimer != null) resultPollTimer.stop();
        resultPollTimer = new javax.swing.Timer(1500, e -> {
            if (resultDialogShown) {
                resultPollTimer.stop();
                return;
            }
            new Thread(() -> {
                String resp = ServerClient.duelGameResults(roomId);
                if (resp.startsWith("SUCCESS|ALL_DONE|")) {
                    String resultsData = resp.substring("SUCCESS|ALL_DONE|".length());
                    SwingUtilities.invokeLater(() -> {
                        if (!resultDialogShown) {
                            handleGameOver(resultsData);
                        }
                    });
                }
            }).start();
        });
        resultPollTimer.start();
    }

    // ===== 接收推送 =====

    /** 接收来自MessageCenter的对手操作推送 */
    public static void receiveCellPush(int roomId, String pushData) {
        MineDuelGame game = activeGames.get(roomId);
        if (game != null) {
            SwingUtilities.invokeLater(() -> game.handleCellPush(pushData));
        }
    }

    private void handleCellPush(String pushData) {
        // 格式: "username:REVEAL:row:col:value" 或 "username:FLAG:row:col" 或 "username:UNFLAG:row:col"
        String[] parts = pushData.split(":");
        if (parts.length < 4) return;
        String oppName = parts[0];
        String action = parts[1];
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);

        OpponentData data = opponentData.get(oppName);
        if (data == null) return;

        if ("REVEAL".equals(action)) {
            data.state[row][col] = REVEALED;
            if (parts.length >= 5) {
                int value = Integer.parseInt(parts[4]);
                data.values[row][col] = value;
            }
        } else if ("FLAG".equals(action)) {
            data.state[row][col] = FLAGGED;
        } else if ("UNFLAG".equals(action)) {
            data.state[row][col] = HIDDEN;
        }

        // 重绘对手面板
        if (data.boardPanel != null) data.boardPanel.repaint();
    }

    /** 接收游戏结束推送 */
    public static void receiveGameOver(int roomId, String resultsData) {
        MineDuelGame game = activeGames.get(roomId);
        if (game != null) {
            SwingUtilities.invokeLater(() -> game.handleGameOver(resultsData));
        }
    }

    private void handleGameOver(String resultsData) {
        if (resultDialogShown) return;
        resultDialogShown = true;
        if (resultPollTimer != null) resultPollTimer.stop();
        Map<String, String> results = new LinkedHashMap<>();
        for (String entry : resultsData.split(";")) {
            String[] kv = entry.split(",", 2);
            if (kv.length == 2) {
                results.put(kv[0], kv[1]);
            }
        }

        // 更新所有对手数据
        for (Map.Entry<String, String> e : results.entrySet()) {
            String name = e.getKey();
            String resultStr = e.getValue();
            if (name.equals(username)) continue;

            OpponentData data = opponentData.get(name);
            if (data != null) {
                data.result = resultStr;
                data.finished = true;
                if (resultStr.startsWith("FAIL")) data.failed = true;
                if (data.statusLabel != null) {
                    String[] rp = resultStr.split(":");
                    String timeStr = rp.length >= 2 ? formatTime(Long.parseLong(rp[1])) : "";
                    data.statusLabel.setText(resultStr.startsWith("WIN") ? "完成 用时 " + timeStr : "失败 用时 " + timeStr);
                }
                if (data.boardPanel != null) data.boardPanel.repaint();
            }
        }

        // 确定赢家（全部失败时 winner 为 null）
        String winner = determineWinner(results);
        boolean iAmWinner = winner != null && username.equals(winner);

        // 全部游戏结束
        stopTimer();
        stopBotTimers();
        gameOver = true;

        // 显示结果对话框
        showResultDialog(winner, results, iAmWinner);
    }

    /** 判定赢家 */
    private String determineWinner(Map<String, String> results) {
        // 收集完成者
        List<String> winners = new ArrayList<>();
        long bestTime = Long.MAX_VALUE;
        String bestPlayer = null;

        // 收集失败者
        List<String> failures = new ArrayList<>();
        long lastFailTime = 0;
        String lastFailPlayer = null;

        for (Map.Entry<String, String> e : results.entrySet()) {
            String[] rp = e.getValue().split(":");
            String type = rp[0];
            long time = rp.length >= 2 ? Long.parseLong(rp[1]) : 0;

            if ("WIN".equals(type)) {
                winners.add(e.getKey());
                if (time < bestTime) {
                    bestTime = time;
                    bestPlayer = e.getKey();
                }
            } else {
                failures.add(e.getKey());
                if (time > lastFailTime) {
                    lastFailTime = time;
                    lastFailPlayer = e.getKey();
                }
            }
        }

        if (!winners.isEmpty()) {
            // 有完成者：最快完成的赢
            return bestPlayer;
        } else {
            // 全部失败（含超时）：无人获胜
            return null;
        }
    }

    /** 显示结果对话框 */
    private void showResultDialog(String winner, Map<String, String> results, boolean iAmWinner) {
        JDialog resultDialog = new JDialog(this, "游戏结束", true);
        resultDialog.setLayout(new BorderLayout());
        resultDialog.setSize(400, 360);
        resultDialog.setLocationRelativeTo(this);
        resultDialog.setResizable(false);
        resultDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        final int[] action = {0}; // 0=未选择, 1=回到主页, 2=回到房间

        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(25, 30, 20, 30));

        // 大标题
        String titleText;
        Color titleColor;
        if (winner == null) {
            titleText = "全部失败！";
            titleColor = new Color(220, 150, 50);
        } else if (iAmWinner) {
            titleText = "YOU WIN!";
            titleColor = new Color(50, 220, 50);
        } else {
            titleText = "YOU LOSE!";
            titleColor = new Color(220, 50, 50);
        }
        JLabel bigLabel = new JLabel(titleText, JLabel.CENTER);
        bigLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 36));
        bigLabel.setForeground(titleColor);
        bigLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(bigLabel);
        content.add(Box.createVerticalStrut(20));

        // 排名列表：按顺序 #1, #2, #3, #4（全部失败时按原始顺序）
        int rank = 1;
        List<String> orderedPlayers;
        if (winner != null) {
            orderedPlayers = new ArrayList<>();
            orderedPlayers.add(winner);
            for (String p : results.keySet()) {
                if (!p.equals(winner)) orderedPlayers.add(p);
            }
        } else {
            orderedPlayers = new ArrayList<>(results.keySet());
        }

        for (String player : orderedPlayers) {
            String resultStr = results.get(player);
            String[] rp = resultStr.split(":");
            String type = rp[0];
            String timeStr = rp.length >= 2 ? formatTime(Long.parseLong(rp[1])) : "-";

            JPanel row = new JPanel(new BorderLayout(20, 0));
            row.setBackground(BG);
            row.setMaximumSize(new Dimension(340, 30));

            String prefix = "#" + rank + " ";
            rank++;
            String displayName = player.equals(username) ? player + "（你）" : player;
            JLabel nameLabel = new JLabel(prefix + displayName);
            nameLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            // 高亮当前用户自己（而非第一名）
            nameLabel.setForeground(player.equals(username) ? new Color(255, 200, 50) : Color.WHITE);
            row.add(nameLabel, BorderLayout.WEST);

            String statusText = "WIN".equals(type) ? "完成" : "失败";
            JLabel statusLbl = new JLabel(statusText + " 用时 " + timeStr);
            statusLbl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            statusLbl.setForeground("WIN".equals(type) ? new Color(100, 200, 100) : new Color(200, 100, 100));
            row.add(statusLbl, BorderLayout.EAST);

            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(row);
            content.add(Box.createVerticalStrut(6));
        }

        content.add(Box.createVerticalStrut(15));

        // 按钮行
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnRow.setBackground(BG);

        JButton homeBtn = new JButton("回到主页");
        homeBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        homeBtn.setForeground(Color.WHITE);
        homeBtn.setBackground(new Color(0, 120, 215));
        homeBtn.setFocusPainted(false);
        homeBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        homeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        homeBtn.addActionListener(e -> {
            action[0] = 1;
            resultDialog.dispose();
        });

        JButton roomBtn = new JButton("回到房间");
        roomBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        roomBtn.setForeground(Color.WHITE);
        roomBtn.setBackground(new Color(80, 83, 86));
        roomBtn.setFocusPainted(false);
        roomBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        roomBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        roomBtn.addActionListener(e -> {
            action[0] = 2;
            resultDialog.dispose();
        });

        btnRow.add(homeBtn);
        btnRow.add(roomBtn);
        content.add(btnRow);

        resultDialog.add(content, BorderLayout.CENTER);
        resultDialog.setVisible(true);
        // 对话框关闭后执行

        activeGames.remove(roomId);
        switch (action[0]) {
            case 1: // 回到主页
                if (parentRoom != null) {
                    parentRoom.dispose();
                }
                dispose();
                JFrame minesweeper = null;
                if (parentRoom instanceof MineMatchRoom) {
                    minesweeper = ((MineMatchRoom) parentRoom).getParentMinesweeper();
                }
                // 兜底：尝试全局引用
                if (minesweeper == null) {
                    minesweeper = MineMatchRoom.getGlobalMinesweeper();
                }
                if (minesweeper != null && minesweeper.isDisplayable()) {
                    minesweeper.setVisible(true);
                    minesweeper.setLocationRelativeTo(null);
                } else {
                    // 没有扫雷窗口 → 回到摸鱼中心
                    FishGrabbingHome.showActiveInstance();
                }
                break;
            case 2: // 回到房间
            default:
                dispose();
                if (parentRoom != null) {
                    if (parentRoom instanceof MineMatchRoom) {
                        ((MineMatchRoom) parentRoom).resetForNewGame();
                    }
                    parentRoom.setVisible(true);
                    parentRoom.setLocationRelativeTo(null);
                }
                break;
        }
    }

    // ===== 计时器 =====

    private void startGameTimer() {
        startTime = System.currentTimeMillis();
        timer = new javax.swing.Timer(200, e -> {
            if (!gameOver) {
                elapsedMs = System.currentTimeMillis() - startTime;
                timerLabel.setText("用时: " + formatTime(elapsedMs));
                // 超时检测
                if (elapsedMs >= timeLimitMs) {
                    triggerTimeOut();
                }
            }
        });
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) timer.stop();
    }

    private void stopBotTimers() {
        for (BotState bs : botStates.values()) {
            if (bs.moveTimer != null) bs.moveTimer.stop();
        }
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    /** 为机器人生成独立棋盘（与玩家布雷不同） */
    private void generateBotBoard(int[][] botBoard, long botSeed) {
        Random rnd = new Random(botSeed);
        int placed = 0;
        while (placed < MINES) {
            int r = rnd.nextInt(ROWS);
            int c = rnd.nextInt(COLS);
            if (botBoard[r][c] != -1) {
                botBoard[r][c] = -1;
                placed++;
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (botBoard[r][c] == -1) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && botBoard[nr][nc] == -1) count++;
                    }
                }
                botBoard[r][c] = count;
            }
        }
    }

    // ===== 机器人模拟 =====

    /** 检测并启动机器人模拟 */
    private void startBotSimulation() {
        List<String> botNames = new ArrayList<>();
        for (String opp : opponents) {
            if (opp.startsWith("机器人")) {
                botNames.add(opp);
            }
        }
        if (botNames.isEmpty()) return;
        hasBots = true;

        // 为每个机器人创建独立的棋盘并进行模拟
        for (String botName : botNames) {
            BotState bs = new BotState();
            bs.rows = ROWS;
            bs.cols = COLS;
            bs.mines = MINES;
            bs.startTime = startTime;
            // 机器人生成自己的独立棋盘（不同 seed，布雷位置不同）
            bs.board = new int[ROWS][COLS];
            bs.state = new int[ROWS][COLS];
            long botSeed = seed * 31 + botName.hashCode();
            generateBotBoard(bs.board, botSeed);

            botStates.put(botName, bs);

            // 机器人在自己的棋盘上操作，模拟人类节奏——越低级越慢
            Random rnd = new Random();
            // 模拟人类思考时间：初级 3-8s, 中级 5-12s, 高级 3-8s
            int[] delays = {3000, 8000, 5000, 12000, 3000, 8000};
            int minDelay = delays[botDifficulty * 2];
            int maxDelay = delays[botDifficulty * 2 + 1];
            int initialDelay = 1500 + rnd.nextInt(2000);
            bs.moveTimer = new javax.swing.Timer(initialDelay, e -> {
                if (bs.finished) {
                    bs.moveTimer.stop();
                    return;
                }
                simulateBotMove(botName, bs);
                if (!bs.finished) {
                    bs.moveTimer.setDelay(minDelay + new Random().nextInt(maxDelay - minDelay + 1));
                }
            });
            bs.moveTimer.setRepeats(true);
            bs.moveTimer.start();
        }
    }

    /** 模拟机器人走一步 */
    private void simulateBotMove(String botName, BotState bs) {
        if (bs.finished) return;

        // 收集所有未揭示的格子
        List<int[]> candidates = new ArrayList<>();
        for (int r = 0; r < bs.rows; r++) {
            for (int c = 0; c < bs.cols; c++) {
                if (bs.state[r][c] == HIDDEN) {
                    candidates.add(new int[]{r, c});
                }
            }
        }

        if (candidates.isEmpty()) {
            bs.finished = true;
            bs.failed = false;
            long botTime = System.currentTimeMillis() - bs.startTime;
            new Thread(() -> ServerClient.duelGameResult(roomId, botName, "WIN", botTime)).start();
            return;
        }

        // 安全启动保护：越高级的机器人越谨慎，前几步避开雷
        int safeMoves;
        if (botDifficulty == 0) safeMoves = 0;          // 初级无保护，随便炸
        else if (botDifficulty == 1) safeMoves = 20;     // 中级前20步安全
        else safeMoves = 40;                              // 高级前40步安全

        List<int[]> safeCandidates = candidates;
        if (bs.moveCount < safeMoves) {
            safeCandidates = new ArrayList<>();
            for (int[] cnd : candidates) {
                if (bs.board[cnd[0]][cnd[1]] != -1) {
                    safeCandidates.add(cnd);
                }
            }
            if (safeCandidates.isEmpty()) safeCandidates = candidates; // 极端兜底
        }

        bs.moveCount++;

        // 中级/高级：先自动标旗确定是雷的格子
        if (botDifficulty >= 1) {
            Set<String> certainMines = new HashSet<>();
            Set<String> certainSafe = new HashSet<>();
            analyzeDeterministic(bs, certainMines, certainSafe);
            for (String key : certainMines) {
                String[] p = key.split(",");
                int mr = Integer.parseInt(p[0]), mc = Integer.parseInt(p[1]);
                if (bs.state[mr][mc] == HIDDEN) {
                    bs.state[mr][mc] = FLAGGED;
                    bs.flagsPlaced++;
                    updateOpponentFlagFromBot(botName, mr, mc);
                }
            }
            // 如果标旗后有确定安全格，重算 candidates
            candidates.clear();
            for (int r2 = 0; r2 < bs.rows; r2++)
                for (int c2 = 0; c2 < bs.cols; c2++)
                    if (bs.state[r2][c2] == HIDDEN) candidates.add(new int[]{r2, c2});
        }

        // 按难度选择格子
        int[] cell;
        if (botDifficulty == 0) {
            cell = pickCellEasy(safeCandidates);
        } else if (botDifficulty == 1) {
            cell = pickCellMedium(bs, safeCandidates);
        } else {
            cell = pickCellHard(bs, safeCandidates);
        }
        int r = cell[0], c = cell[1];

        if (bs.board[r][c] == -1) {
            // 踩到雷 → 机器人输了
            bs.finished = true;
            bs.failed = true;
            bs.state[r][c] = REVEALED;
            updateOpponentFromBot(botName, r, c, -1);
            long botTime = System.currentTimeMillis() - bs.startTime;
            new Thread(() -> ServerClient.duelGameResult(roomId, botName, "FAIL", botTime)).start();
        } else {
            // 正常翻开（递归展开所有相连0格）
            List<int[]> revealed = revealBotCell(bs, r, c);
            for (int[] rc : revealed) {
                updateOpponentFromBot(botName, rc[0], rc[1], bs.board[rc[0]][rc[1]]);
            }
            // 检查是否所有非雷格子都已揭示
            int unrevealed = 0;
            for (int i = 0; i < bs.rows; i++) {
                for (int j = 0; j < bs.cols; j++) {
                    if (bs.state[i][j] == HIDDEN && bs.board[i][j] != -1) unrevealed++;
                }
            }
            if (unrevealed == 0) {
                // 自动标旗所有雷
                for (int i = 0; i < bs.rows; i++) {
                    for (int j = 0; j < bs.cols; j++) {
                        if (bs.board[i][j] == -1 && bs.state[i][j] != FLAGGED) {
                            bs.state[i][j] = FLAGGED;
                            updateOpponentFlagFromBot(botName, i, j);
                        }
                    }
                }
                bs.finished = true;
                bs.failed = false;
                long botTime = System.currentTimeMillis() - bs.startTime;
                new Thread(() -> ServerClient.duelGameResult(roomId, botName, "WIN", botTime)).start();
            }
        }
    }

    // ---------- 机器人 AI 策略 ----------

    /** 初级：纯随机 */
    private int[] pickCellEasy(List<int[]> candidates) {
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    /** 中级：简单推理——优先安全格子，避开已知雷，否则95%概率避开雷区+偏好边缘角 */
    private int[] pickCellMedium(BotState bs, List<int[]> candidates) {
        Set<String> certainMines = new HashSet<>();
        Set<String> certainSafe = new HashSet<>();
        analyzeDeterministic(bs, certainMines, certainSafe);

        // 优先选确定安全的
        for (int[] cnd : candidates) {
            if (certainSafe.contains(cnd[0] + "," + cnd[1])) return cnd;
        }

        // 排除确定是雷的
        List<int[]> safeish = new ArrayList<>();
        for (int[] cnd : candidates) {
            if (!certainMines.contains(cnd[0] + "," + cnd[1])) safeish.add(cnd);
        }
        if (safeish.isEmpty()) safeish = candidates;

        // 95%概率从安全区中偏好边缘/角落（统计上边上雷密度更低）
        if (new Random().nextInt(100) < 95) {
            List<int[]> edgeCells = new ArrayList<>();
            List<int[]> innerCells = new ArrayList<>();
            for (int[] cnd : safeish) {
                int r = cnd[0], c = cnd[1];
                if (r == 0 || r == bs.rows - 1 || c == 0 || c == bs.cols - 1) {
                    edgeCells.add(cnd);
                } else {
                    innerCells.add(cnd);
                }
            }
            // 70%概率选边缘，30%选内部
            if (!edgeCells.isEmpty() && (innerCells.isEmpty() || new Random().nextInt(100) < 70)) {
                return edgeCells.get(new Random().nextInt(edgeCells.size()));
            }
            if (!innerCells.isEmpty()) {
                return innerCells.get(new Random().nextInt(innerCells.size()));
            }
        }
        return pickCellEasy(candidates);
    }

    /** 高级：概率推算 —— 确定安全格直接选，否则每个候选格算雷概率选最低，偏好已揭示区域附近 */
    private int[] pickCellHard(BotState bs, List<int[]> candidates) {
        Set<String> certainMines = new HashSet<>();
        Set<String> certainSafe = new HashSet<>();
        analyzeDeterministic(bs, certainMines, certainSafe);

        // 确定安全的直接选
        for (int[] cnd : candidates) {
            if (certainSafe.contains(cnd[0] + "," + cnd[1])) return cnd;
        }

        // 排除确定是雷的
        List<int[]> filtered = new ArrayList<>();
        for (int[] cnd : candidates) {
            if (!certainMines.contains(cnd[0] + "," + cnd[1])) filtered.add(cnd);
        }
        if (filtered.isEmpty()) filtered = candidates;
        if (filtered.size() == 1) return filtered.get(0);

        // 全局雷密度作为基准概率
        double globalDensity = (double) bs.mines / (bs.rows * bs.cols);

        // 分离：有约束的 vs 无约束的
        List<int[]> constrained = new ArrayList<>();
        List<int[]> unconstrained = new ArrayList<>();
        double[] constrainedProbs = null;

        for (int[] cnd : filtered) {
            int r = cnd[0], c = cnd[1];
            boolean hasConstraint = false;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = r + dr, nc = c + dc;
                    if (nr < 0 || nr >= bs.rows || nc < 0 || nc >= bs.cols) continue;
                    if (bs.state[nr][nc] == REVEALED && bs.board[nr][nc] > 0) {
                        hasConstraint = true;
                        break;
                    }
                }
                if (hasConstraint) break;
            }
            if (hasConstraint) constrained.add(cnd);
            else unconstrained.add(cnd);
        }

        // 优先从有约束的格子里选最低概率的
        if (!constrained.isEmpty()) {
            constrainedProbs = new double[constrained.size()];
            double bestProb = Double.MAX_VALUE;
            int bestIdx = 0;
            for (int idx = 0; idx < constrained.size(); idx++) {
                int r = constrained.get(idx)[0], c = constrained.get(idx)[1];
                constrainedProbs[idx] = calcMineProbability(bs, r, c);
                if (constrainedProbs[idx] < bestProb) { bestProb = constrainedProbs[idx]; bestIdx = idx; }
            }
            return constrained.get(bestIdx);
        }

        // 没有约束的格子：优先选已揭示区域旁边的边缘格子，方便扩展推理区域
        List<int[]> adjacent = new ArrayList<>();
        List<int[]> isolated = new ArrayList<>();
        for (int[] cnd : unconstrained) {
            int r = cnd[0], c = cnd[1];
            boolean hasRevealedNeighbor = false;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = r + dr, nc = c + dc;
                    if (nr < 0 || nr >= bs.rows || nc < 0 || nc >= bs.cols) continue;
                    if (bs.state[nr][nc] == REVEALED) { hasRevealedNeighbor = true; break; }
                }
                if (hasRevealedNeighbor) break;
            }
            if (hasRevealedNeighbor) adjacent.add(cnd);
            else isolated.add(cnd);
        }

        // 70%选已揭示附近的，30%选边缘（扩张新区域）
        if (!adjacent.isEmpty() && !isolated.isEmpty()) {
            if (new Random().nextInt(100) < 70) {
                return adjacent.get(new Random().nextInt(adjacent.size()));
            }
        }
        if (!adjacent.isEmpty()) {
            return adjacent.get(new Random().nextInt(adjacent.size()));
        }
        // 都没有则选边缘格子（更安全）
        List<int[]> edgeCells = new ArrayList<>();
        for (int[] cnd : unconstrained) {
            int r = cnd[0], c = cnd[1];
            if (r == 0 || r == bs.rows - 1 || c == 0 || c == bs.cols - 1) edgeCells.add(cnd);
        }
        if (!edgeCells.isEmpty()) return edgeCells.get(new Random().nextInt(edgeCells.size()));
        return unconstrained.get(new Random().nextInt(unconstrained.size()));
    }

    /** 分析确定性的雷和安全格子 */
    private void analyzeDeterministic(BotState bs, Set<String> mines, Set<String> safe) {
        for (int r = 0; r < bs.rows; r++) {
            for (int c = 0; c < bs.cols; c++) {
                if (bs.state[r][c] != REVEALED || bs.board[r][c] <= 0) continue;
                int num = bs.board[r][c];
                int flagged = 0, hidden = 0;
                List<int[]> hiddenCells = new ArrayList<>();
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr < 0 || nr >= bs.rows || nc < 0 || nc >= bs.cols) continue;
                        if (bs.state[nr][nc] == FLAGGED) flagged++;
                        else if (bs.state[nr][nc] == HIDDEN) hiddenCells.add(new int[]{nr, nc});
                    }
                }
                if (flagged == num) {
                    for (int[] h : hiddenCells) safe.add(h[0] + "," + h[1]);
                } else if (flagged + hiddenCells.size() == num) {
                    for (int[] h : hiddenCells) mines.add(h[0] + "," + h[1]);
                }
            }
        }
    }

    /** 计算某个隐藏格子的雷概率（基于周围已揭示数字格子的约束） */
    private double calcMineProbability(BotState bs, int r, int c) {
        double totalRisk = 0;
        int constraints = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr < 0 || nr >= bs.rows || nc < 0 || nc >= bs.cols) continue;
                if (bs.state[nr][nc] == REVEALED && bs.board[nr][nc] > 0) {
                    int num = bs.board[nr][nc];
                    int flagged = 0, hidden = 0;
                    for (int dr2 = -1; dr2 <= 1; dr2++) {
                        for (int dc2 = -1; dc2 <= 1; dc2++) {
                            int nnr = nr + dr2, nnc = nc + dc2;
                            if (nnr < 0 || nnr >= bs.rows || nnc < 0 || nnc >= bs.cols) continue;
                            if (bs.state[nnr][nnc] == FLAGGED) flagged++;
                            else if (bs.state[nnr][nnc] == HIDDEN) hidden++;
                        }
                    }
                    if (hidden > 0) {
                        totalRisk += (double)(num - flagged) / hidden;
                        constraints++;
                    }
                }
            }
        }
        return constraints > 0 ? totalRisk / constraints : (double) bs.mines / (bs.rows * bs.cols);
    }

    // ---------- 机器人 AI 策略结束 ----------

    /** 把机器人的操作同步到对手面板（本地立即更新，同时上报服务器供其他玩家同步） */
    private void updateOpponentFromBot(String botName, int r, int c, int value) {
        OpponentData data = opponentData.get(botName);
        if (data != null) {
            data.state[r][c] = REVEALED;
            data.values[r][c] = value;
            if (data.boardPanel != null) data.boardPanel.repaint();
        }
        // 上报服务器，其他人类玩家也能看到机器人进度
        new Thread(() -> ServerClient.duelGameReveal(roomId, botName, r, c, value)).start();
    }

    /** 把机器人的标旗操作同步到对手面板 */
    private void updateOpponentFlagFromBot(String botName, int r, int c) {
        OpponentData data = opponentData.get(botName);
        if (data != null) {
            data.state[r][c] = FLAGGED;
            if (data.boardPanel != null) data.boardPanel.repaint();
        }
        new Thread(() -> ServerClient.duelGameFlag(roomId, botName, r, c)).start();
    }

    /** 递归揭示机器人棋盘（模拟 flood-fill），返回本次揭示的所有格子 */
    private List<int[]> revealBotCell(BotState bs, int r, int c) {
        List<int[]> revealed = new ArrayList<>();
        revealBotCellRecursive(bs, r, c, revealed);
        return revealed;
    }

    private void revealBotCellRecursive(BotState bs, int r, int c, List<int[]> revealed) {
        if (r < 0 || r >= bs.rows || c < 0 || c >= bs.cols) return;
        if (bs.state[r][c] != HIDDEN) return;
        bs.state[r][c] = REVEALED;
        revealed.add(new int[]{r, c});
        if (bs.board[r][c] == 0) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    revealBotCellRecursive(bs, r + dr, c + dc, revealed);
                }
            }
        }
    }

    /** 释放资源 */
    @Override
    public void dispose() {
        stopTimer();
        if (resultPollTimer != null) resultPollTimer.stop();
        // 停止所有机器人计时器
        for (BotState bs : botStates.values()) {
            if (bs.moveTimer != null) bs.moveTimer.stop();
        }
        botStates.clear();
        activeGames.remove(roomId);
        super.dispose();
    }
}
