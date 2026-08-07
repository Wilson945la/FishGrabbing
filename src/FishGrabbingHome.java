import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class FishGrabbingHome extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);

    private static FishGrabbingHome activeInstance = null;

    public static FishGrabbingHome getActiveInstance() { return activeInstance; }
    public static void hideActiveInstance() {
        if (activeInstance != null) {
            // 隐藏主页时停止动画Timer，避免后台空转
            if (activeInstance.redrawTimer != null) activeInstance.redrawTimer.stop();
            activeInstance.setVisible(false);
        }
    }
    public static void showActiveInstance() {
        if (activeInstance != null) {
            activeInstance.setVisible(true);
            activeInstance.setLocationRelativeTo(null);
            // 恢复动画Timer
            if (activeInstance.redrawTimer != null && !activeInstance.redrawTimer.isRunning()) {
                activeInstance.redrawTimer.start();
            }
        }
    }

    private String username = "";
    private int userId = 0;
    private javax.swing.Timer redrawTimer;

    public void setUserId(int userId) { this.userId = userId; }

    public FishGrabbingHome() {
        this(null, true, 0);
    }

    public FishGrabbingHome(String displayName, boolean loggedIn, int fish) {
        activeInstance = this;
        this.username = displayName;

        // 注册 ShutdownHook，确保进程退出时清理资源
        ServerClient.registerShutdownHook();

        setTitle("\u6478\u9c7c\u4e2d\u5fc3");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (redrawTimer != null) redrawTimer.stop();
                String u = ServerClient.getCurrentUser(); if (u != null) { ServerClient.setUserState(u, 0); }
                MessageCenter.stopGlobalPush();
                ServerClient.shutdown();
                dispose();
                System.exit(0);
            }
        });

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(380, 500));

        JPanel top = new JPanel();
        top.setBackground(BG);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        if (username != null && !username.isEmpty()) {
            JLabel userIcon = createEmojiLabel("\uD83D\uDC64", 18);
            userIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            userIcon.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    setVisible(false);
                    new PersonalCenter(username, FishGrabbingHome.this).setVisible(true);
                }
            });

            JLabel userLabel = new JLabel(username);
            userLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            userLabel.setForeground(new Color(100, 255, 100));
            userLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            userLabel.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    setVisible(false);
                    new PersonalCenter(username, FishGrabbingHome.this).setVisible(true);
                }
                public void mouseEntered(MouseEvent e) { userLabel.setForeground(Color.WHITE); }
                public void mouseExited(MouseEvent e) { userLabel.setForeground(new Color(100, 255, 100)); }
            });

            JLabel fishLabel = new JLabel(" \u9c7c\u5e01:" + fish);
            fishLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            fishLabel.setForeground(new Color(255, 215, 0));

            // 好友链接（右上角）
            JLabel mailLink = createEmojiLabel("\u2709", 18);
            mailLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            mailLink.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    // 不隐藏摸鱼中心，直接打开消息中心
                    MessageCenter mc = new MessageCenter(username, FishGrabbingHome.this, userId);
                    mc.setVisible(true);
                    mc.showNotifications();
                }
            });
            mailLink.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

            JLabel friendsLink = new JLabel("好友");
            friendsLink.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            friendsLink.setForeground(new Color(100, 200, 255));
            friendsLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            friendsLink.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    setVisible(false);
                    new FriendsPage(username, FishGrabbingHome.this, userId).setVisible(true);
                }
                public void mouseEntered(MouseEvent e) { friendsLink.setForeground(Color.WHITE); }
                public void mouseExited(MouseEvent e) { friendsLink.setForeground(new Color(100, 200, 255)); }
            });

            JPanel rightLinks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightLinks.setBackground(BG);
            rightLinks.add(mailLink);
            rightLinks.add(friendsLink);

            JPanel userInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            userInfo.setBackground(BG);
            userInfo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            userInfo.add(userIcon);
            userInfo.add(userLabel);
            userInfo.add(fishLabel);

            JPanel userRow = new JPanel(new BorderLayout());
            userRow.setBackground(BG);
            userRow.add(userInfo, BorderLayout.WEST);
            userRow.add(rightLinks, BorderLayout.EAST);
            userRow.setBorder(BorderFactory.createEmptyBorder(15, 10, 2, 10));

            top.add(userRow);
        }

        JPanel titleArea = new JPanel();
        titleArea.setBackground(BG);
        titleArea.setLayout(new BoxLayout(titleArea, BoxLayout.Y_AXIS));
        titleArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel fishPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        fishPanel.setBackground(BG);
        fishPanel.add(createEmojiLabel("\uD83D\uDC1F", 50));
        titleArea.add(fishPanel);
        JLabel title = new JLabel("\u6478\u9c7c\u4e2d\u5fc3");
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleArea.add(title);
        titleArea.add(Box.createVerticalStrut(4));
        JPanel catRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        catRow.setBackground(BG);
        catRow.add(createPixelCat(64));
        titleArea.add(catRow);
        titleArea.add(Box.createVerticalStrut(10));
        top.add(titleArea);

        main.add(top, BorderLayout.NORTH);

        JPanel bp = new JPanel();
        bp.setBackground(BG);
        bp.setLayout(new BoxLayout(bp, BoxLayout.Y_AXIS));
        bp.setBorder(BorderFactory.createEmptyBorder(20, 60, 10, 60));

        JButton msBtn = gameBtn("\u626b\u96f7");
        msBtn.addActionListener(e -> {
            setVisible(false);
            Minesweeper ms = new Minesweeper();
            ms.setUserId(userId);
            ms.setHomeFrame(this);
            ms.setVisible(true);
        });
        bp.add(msBtn);

        bp.add(Box.createVerticalStrut(10));

        JButton tetrisBtn = gameBtn("\u4fc4\u7f57\u65af\u65b9\u5757");
        tetrisBtn.addActionListener(e -> {
            setVisible(false);
            TetrisHome tetris = new TetrisHome();
            tetris.setHomeFrame(this);
            tetris.setUserId(userId);
            tetris.setVisible(true);
        });
        bp.add(tetrisBtn);

        bp.add(Box.createVerticalStrut(10));

        JButton aeroChessBtn = gameBtn("飞行棋");
        aeroChessBtn.addActionListener(e -> {
            setVisible(false);
            AeroChessHome aeroChess = new AeroChessHome();
            aeroChess.setHomeFrame(this);
            aeroChess.setUserId(userId);
            aeroChess.setVisible(true);
        });
        bp.add(aeroChessBtn);

        bp.add(Box.createVerticalStrut(10));

        JButton game2048Btn = gameBtn("2048");
        game2048Btn.addActionListener(e -> {
            setVisible(false);
            Game2048Home home2048 = new Game2048Home();
            home2048.setHomeFrame(this);
            home2048.setUserId(userId);
            home2048.setVisible(true);
        });
        bp.add(game2048Btn);

        bp.add(Box.createVerticalStrut(10));

        JButton unoBtn = gameBtn("UNO");
        unoBtn.addActionListener(e -> {
            setVisible(false);
            UnoHome uno = new UnoHome(username, this);
            uno.setVisible(true);
        });
        bp.add(unoBtn);

        // 滚动面板：不改主页大小，内容多时可上下拖动
        JScrollPane scrollPane = new JScrollPane(bp);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // 始终显示竖向滚动条，保证在线/离线模式外观一致（内容放得下时为禁用态灰色槽）
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        // 透明背景
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        bp.setOpaque(false);

        main.add(scrollPane, BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setBackground(BG);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JLabel ver = new JLabel("摸鱼神器 v1.3", JLabel.CENTER);
        ver.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        ver.setForeground(new Color(120, 123, 126));
        bot.add(ver, BorderLayout.CENTER);

        JButton actionBtn;
        if (loggedIn) {
            actionBtn = txtBtn("\u9000\u51fa\u767b\u5f55", Color.WHITE, new Color(80,83,86), 90, 32);
            actionBtn.addActionListener(e -> {
                // 清除当前用户状态，避免离线模式下仍能使用需要匹配的功能
                String u = ServerClient.getCurrentUser();
                if (u != null) {
                    try { ServerClient.setUserState(u, 0); } catch (Exception ignored) {}
                }
                ServerClient.setCurrentUser(null);
                MessageCenter.stopGlobalPush();
                if (redrawTimer != null) redrawTimer.stop();
                setVisible(false);
                new LoginFrame().setVisible(true);
            });
        } else {
            actionBtn = txtBtn("\u524d\u5f80\u767b\u5f55", Color.WHITE, new Color(80,83,86), 90, 28);
            actionBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            actionBtn.addActionListener(e -> {
                setVisible(false);
                new LoginFrame().setVisible(true);
            });
        }
        actionBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        actionBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { actionBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { actionBtn.setBackground(BTN_BASE); }
        });
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setBackground(BG);
        actionPanel.add(actionBtn);
        bot.add(actionPanel, BorderLayout.EAST);
        main.add(bot, BorderLayout.SOUTH);

        getContentPane().add(main);
        pack();
        setLocationRelativeTo(null);
    }

    private JLabel createPixelCat(int size) {
        final int s = Math.max(1, size / 18);
        final int w = 18 * s, h = 18 * s;
        final int imgW = w + 150;
        final String[] kaomoji = {
            "ฅ^••^ฅ", "(=^･ω･^=)", "(=ω①=)", "ฅ(*д*๑)ฅ",
            "ヾ(=^･ω･^=)〃", "(=｀ω´=)", "(=^‥^=)", "ฅ( ̳͒•ˑ• ̳͒ฅ)",
            "(=^･ｪ･^=)", "(^・ω・^ฅ)", "(=ω①=)ﾉ", "ฅ(≧ω≦)ฅ",
        };
        final java.util.Random rnd = new java.util.Random();

        int[][] cat = {
            {0,0,0,0,0,0,0,1,1,1,0,0,0,0,0,0,0,0},
            {0,0,0,1,3,3,0,0,0,0,3,3,1,0,0,0,0,0},
            {0,0,1,3,4,8,4,0,0,4,8,4,3,1,0,0,0,0},
            {0,1,3,4,4,4,4,4,4,4,4,4,4,3,1,0,0,0},
            {1,3,4,4,4,4,4,4,4,4,4,4,4,4,3,1,0,0},
            {1,3,4,4,5,2,5,4,4,5,2,5,4,4,4,1,0,0},
            {1,3,4,4,5,6,5,4,4,5,6,5,4,4,4,1,0,0},
            {1,3,7,7,5,5,4,4,4,5,5,4,7,7,4,1,0,0},
            {1,3,7,7,4,4,4,1,1,4,4,4,7,7,4,1,0,0},
            {1,3,4,4,2,2,1,1,1,1,2,2,4,4,4,1,0,0},
            {1,3,4,4,4,4,2,2,2,2,4,4,4,4,3,1,0,0},
            {0,1,3,4,4,4,2,2,2,2,4,4,4,4,3,1,0,0},
            {0,1,3,4,4,4,4,4,4,4,4,4,4,4,3,1,0,0},
            {0,0,1,3,4,4,4,4,4,4,4,4,3,1,0,0,0,0},
            {0,0,1,3,4,2,2,4,4,2,2,4,3,1,0,0,0,0},
            {0,0,0,1,3,4,4,4,4,4,4,3,1,0,4,3,0,0},
            {0,0,0,1,3,4,4,4,4,4,4,3,1,0,3,1,0,0},
            {0,0,0,0,0,0,0,0,0,0,0,0,0,1,3,1,0,0},
        };
        Color[] colors = {
            new Color(0,0,0,0), new Color(0,0,0), new Color(255,255,255),
            new Color(174,174,174), new Color(222,222,222), new Color(114,154,188),
            new Color(50,50,72), new Color(252,191,194), new Color(240,168,170),
        };

        // Pre-render cat
        final BufferedImage catImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = catImg.createGraphics();
        for (int r = 0; r < 18; r++) {
            for (int c = 0; c < 18; c++) {
                if (cat[r][c] > 0) {
                    cg.setColor(colors[cat[r][c]]);
                    cg.fillRect(c * s, r * s, s, s);
                }
            }
        }
        cg.dispose();

        // State
        final String[] bubbleText = {null};
        final long[] bubbleTime = {0};

        final BufferedImage img = new BufferedImage(imgW, h, BufferedImage.TYPE_INT_ARGB);
        final JLabel label = new JLabel(new ImageIcon(img));
        label.setPreferredSize(new Dimension(imgW, h));
        label.setOpaque(false);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Initial draw
        Graphics2D gInit = img.createGraphics();
        gInit.drawImage(catImg, 0, 0, null);
        gInit.dispose();

        // Single redraw timer (every 50ms) - handles both shake and bubble
        final int[] shakeOff = {0};
        final boolean[] shaking = {false};

        this.redrawTimer = new javax.swing.Timer(50, ev -> {
            Graphics2D g2 = img.createGraphics();
            // Full clear using AlphaComposite.Clear — avoids ghost pixels from translate
            g2.setComposite(java.awt.AlphaComposite.Clear);
            g2.fillRect(0, 0, imgW, h);
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            g2.translate(shakeOff[0], 0);
            g2.drawImage(catImg, 0, 0, null);
            
            long elapsed = System.currentTimeMillis() - bubbleTime[0];
            boolean show = bubbleText[0] != null && elapsed < 1500;
            if (show) {
                Font f = new Font("Microsoft YaHei", Font.BOLD, 14);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                String t = bubbleText[0];
                int tw = fm.stringWidth(t);
                int bw = tw + 16;
                int bh = fm.getHeight() + 8;
                int bx = w - 2;
                int by = 5;
                g2.setColor(new Color(255, 250, 230));
                g2.fillRoundRect(bx, by, bw, bh, 8, 8);
                g2.setColor(new Color(200, 180, 230));
                g2.drawRoundRect(bx, by, bw, bh, 8, 8);
                g2.setColor(new Color(80, 60, 120));
                g2.drawString(t, bx + 8, by + fm.getAscent() + 4);
            }
            g2.dispose();
            label.setIcon(new ImageIcon(img));
            
            // Clear bubble state after timeout
            if (!shaking[0] && bubbleText[0] != null && elapsed >= 1500) {
                bubbleText[0] = null;
            }
        });
        this.redrawTimer.start();

        label.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                bubbleText[0] = kaomoji[rnd.nextInt(kaomoji.length)];
                bubbleTime[0] = System.currentTimeMillis();
                shaking[0] = true;

                final int shakeAmount = Math.max(3, s * 2);
                final int[] offsets = {shakeAmount, -shakeAmount, shakeAmount, -shakeAmount,
                                       shakeAmount, -shakeAmount, shakeAmount, -shakeAmount,
                                       shakeAmount/2, -shakeAmount/2, shakeAmount/2, -shakeAmount/2,
                                       shakeAmount/4, -shakeAmount/4, 0};
                final int[] step = {0};
                
                javax.swing.Timer shakeTimer = new javax.swing.Timer(30, ev -> {
                    if (step[0] >= offsets.length) {
                        shakeOff[0] = 0;
                        shaking[0] = false;
                        ((javax.swing.Timer) ev.getSource()).stop();
                        return;
                    }
                    shakeOff[0] = offsets[step[0]];
                    step[0]++;
                });
                shakeTimer.start();
            }
        });

        return label;
    }

    private JLabel createEmojiLabel(String emoji, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Font f = new Font("Segoe UI Emoji", Font.PLAIN, size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(emoji)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(emoji, x, y);
        g.dispose();
        return new JLabel(new ImageIcon(img));
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

    public static void main(String[] args) {
        System.setProperty("sun.java2d.dpiaware", "true");
        System.setProperty("sun.java2d.uiScale", "1.0");
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
