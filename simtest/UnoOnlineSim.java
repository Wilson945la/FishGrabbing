import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UnoOnlineSim {
    static final String HOST = "127.0.0.1";
    static final int PORT = 8899;

    // 推送 socket：注册为 user，接收 PUSH|<sender>|<msg>
    static class PushListener {
        Socket sock;
        BufferedReader in;
        final BlockingQueue<String> q = new LinkedBlockingQueue<>();
        volatile boolean running = true;
        PushListener(String user) throws IOException {
            sock = new Socket();
            sock.connect(new InetSocketAddress(HOST, PORT), 5000);
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            out.println("PUSH_REGISTER|" + user);
            String ok = in.readLine();
            if (!"PUSH_OK".equals(ok)) {
                System.out.println("[推送] 注册失败: " + ok);
                running = false;
                return;
            }
            new Thread(() -> {
                try {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        if (line.equals("PONG")) continue;
                        if (line.startsWith("PUSH|")) {
                            String[] p = line.split("\\|", 3);
                            if (p.length == 3) q.add(p[2]);
                        }
                    }
                } catch (Exception e) { /* 连接关闭 */ }
            }, "Push-Listener").start();
        }
        String poll(long ms) throws InterruptedException {
            return q.poll(ms, TimeUnit.MILLISECONDS);
        }
        void close() { running = false; try { sock.close(); } catch (Exception e) {} }
    }

    static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // 与服务器校验一致的可出判定
    static boolean canPlay(String c, String top, int activeColor, int pending) {
        int color = "RYGBK".indexOf(c.charAt(0));
        char tc = c.charAt(1);
        int type = (tc >= '0' && tc <= '9') ? (tc - '0')
                : (tc == 's' ? 10 : tc == 'r' ? 11 : tc == 'd' ? 12 : tc == 'w' ? 13 : 14);
        int tcolor = "RYGBK".indexOf(top.charAt(0));
        char ttc = top.charAt(1);
        int ttype = (ttc >= '0' && ttc <= '9') ? (ttc - '0')
                : (ttc == 's' ? 10 : ttc == 'r' ? 11 : ttc == 'd' ? 12 : ttc == 'w' ? 13 : 14);
        boolean wild = (type == 13 || type == 14);
        if (pending > 0) {
            if (type == 12 && ttype == 12) return true;
            if (type == 14 && (ttype == 12 || ttype == 14)) return true;
            return false;
        }
        if (wild) return true;
        if (color == activeColor) return true;
        if (type <= 9 && ttype <= 9 && c.substring(2,3).equals(top.substring(2,3))) return true;
        if (type >= 10 && !wild && type == ttype) return true;
        return false;
    }

    static int decideToggle = 0;

    static void playGame(String me, int roomId, PushListener pl) throws Exception {
        int maxTurns = 300;
        boolean drewDecisionSeen = false;
        for (int turn = 0; turn < maxTurns; turn++) {
            String raw = ServerClient.unoState(roomId, me);
            if (!raw.startsWith("SUCCESS")) { Thread.sleep(300); continue; }
            String state = raw.substring("SUCCESS|".length());
            String[] f = state.split("\\|");
            if (f.length < 13) { Thread.sleep(300); continue; }
            int colorIdx = parseIntSafe(f[1], 0);
            int curIdx = parseIntSafe(f[2], 0);
            int pending = parseIntSafe(f[4], 0);
            String top = f[6];
            boolean over = "1".equals(f[7]);
            if (over) { System.out.println("  [" + me + "] 对局结束 winner=" + f[8]); return; }

            // 解析玩家列表与我的手牌
            int mh = -1;
            for (int i = 12; i < f.length; i++) if ("MYHAND".equals(f[i])) { mh = i; break; }
            List<String> players = new ArrayList<>();
            for (int i = 12; i < (mh < 0 ? f.length : mh); i++) players.add(f[i]);
            List<String> hand = new ArrayList<>();
            if (mh >= 0 && mh + 1 < f.length) {
                for (String c : f[mh + 1].split(",")) if (!c.isEmpty()) hand.add(c);
            }
            int myIdx = -1;
            for (int i = 0; i < players.size(); i++) {
                String nm = players.get(i).split(",")[0];
                if (nm.equals(me)) { myIdx = i; break; }
            }
            if (myIdx != curIdx) { Thread.sleep(400); continue; }

            // 处理 +4 质疑（f[10] 为需要决策质疑的玩家）
            String chal = f[10];
            if (chal != null && !chal.isEmpty() && chal.equals(me)) {
                decideToggle++;
                boolean accept = (decideToggle % 2 == 1);
                System.out.println("  [" + me + "] 处理 +4 质疑: " + (accept ? "接受(不质疑)" : "质疑"));
                String r = ServerClient.unoChallenge(roomId, me, accept);
                if (!r.startsWith("SUCCESS")) System.out.println("   质疑被拒: " + r);
                Thread.sleep(300);
                continue;
            }

            // 摸牌决策
            String drawnDecide = f[11];
            if (drawnDecide != null && !drawnDecide.isEmpty()) {
                String who = drawnDecide.contains(",") ? drawnDecide.substring(0, drawnDecide.lastIndexOf(',')) : drawnDecide;
                if (who.equals(me)) {
                    drewDecisionSeen = true;
                    decideToggle++;
                    boolean play = (decideToggle % 2 == 1);
                    int sIdx = drawnDecide.contains(",") ? parseIntSafe(drawnDecide.substring(drawnDecide.lastIndexOf(',') + 1), -1) : -1;
                    String colorArg = "x";
                    if (sIdx >= 0 && sIdx < hand.size() && hand.get(sIdx).charAt(0) == 'K') colorArg = "R";
                    System.out.println("  [" + me + "] 摸到的牌可出(idx=" + sIdx + " " + (sIdx < hand.size() ? hand.get(sIdx) : "?") + ") 决策=" + (play ? "打出" : "过牌"));
                    String r = ServerClient.unoDrawDecide(roomId, me, play, colorArg);
                    if (!r.startsWith("SUCCESS")) System.out.println("   决策被拒: " + r);
                    Thread.sleep(300);
                    continue;
                }
            }

            // 找可出的牌
            int playIdx = -1;
            for (int i = 0; i < hand.size(); i++) {
                if (canPlay(hand.get(i), top, colorIdx, pending)) { playIdx = i; break; }
            }
            if (playIdx >= 0) {
                String c = hand.get(playIdx);
                String colorArg = (c.charAt(0) == 'K') ? "R" : "x";
                System.out.println("  [" + me + "] 出第" + playIdx + "张 " + c + " 选色=" + colorArg + " (剩" + hand.size() + ")");
                String r = ServerClient.unoPlay(roomId, me, playIdx, colorArg);
                if (!r.startsWith("SUCCESS")) System.out.println("   出牌被拒: " + r);
                if (hand.size() <= 2) ServerClient.unoCallUno(roomId, me);
            } else {
                System.out.println("  [" + me + "] 无牌可出，摸牌 (pending=" + pending + ")");
                ServerClient.unoDraw(roomId, me);
            }
            Thread.sleep(300);
        }
        System.out.println("  [" + me + "] 达到回合上限，退出循环 (drewDecisionSeen=" + drewDecisionSeen + ")");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) { System.out.println("用法: UnoOnlineSim host <username> | join <roomId> <username>"); return; }
        String mode = args[0];
        if ("host".equals(mode)) {
            String me = args.length > 1 ? args[1] : "甲";
            ServerClient.setCurrentUser(me);
            PushListener pl = new PushListener(me);
            if (!pl.running) { System.out.println("推送注册失败"); return; }
            String createResp = ServerClient.duelCreate(me, "0", 4, "UNO");
            System.out.println("duelCreate -> " + createResp);
            if (!createResp.startsWith("SUCCESS")) { System.out.println("建房失败"); return; }
            String st = createResp.substring("SUCCESS|".length());
            int roomId = parseIntSafe(st.split("\\|")[0], -1);
            System.out.println("房间号=" + roomId);
            // 加 2 个机器人，留 1 个真人位给 joiner
            for (int i = 0; i < 2; i++) {
                String r = ServerClient.duelAddBot(roomId, me);
                System.out.println("addBot" + (i+1) + " -> " + r);
            }
            // 等待 joiner 加入（通过 DUEL_STATE 推送里出现对方名字判断）
            System.out.println("等待 joiner 加入...");
            boolean joined = false;
            String joinerName = "乙";
            for (int i = 0; i < 30; i++) {
                String m = pl.poll(1000);
                if (m == null) continue;
                if (m.startsWith("DUEL_STATE:") && m.contains(joinerName)) { joined = true; break; }
            }
            System.out.println("joiner 已加入 = " + joined);
            // 准备（触发 ALL_READY + 推送 DUEL_GAME_START）
            String rr = ServerClient.duelReady(roomId, me);
            System.out.println("duelReady(host) -> " + rr);
            // 等待 DUEL_GAME_START 推送
            boolean gotStart = false;
            for (int i = 0; i < 20; i++) {
                String m = pl.poll(1000);
                if (m == null) continue;
                System.out.println("  [push] " + (m.length() > 80 ? m.substring(0, 80) + "..." : m));
                if (m.startsWith("DUEL_GAME_START:")) { gotStart = true; break; }
            }
            System.out.println("收到 DUEL_GAME_START = " + gotStart);
            if (gotStart) playGame(me, roomId, pl);
            pl.close();
            System.exit(0);
        } else if ("join".equals(mode)) {
            int roomId = Integer.parseInt(args[1]);
            String me = args.length > 2 ? args[2] : "乙";
            ServerClient.setCurrentUser(me);
            PushListener pl = new PushListener(me);
            if (!pl.running) { System.out.println("推送注册失败"); return; }
            String jr = ServerClient.duelJoin(roomId, me);
            System.out.println("duelJoin -> " + jr);
            String rr = ServerClient.duelReady(roomId, me);
            System.out.println("duelReady(join) -> " + rr);
            // 等一会看是否收到开始推送
            for (int i = 0; i < 10; i++) {
                String m = pl.poll(800);
                if (m == null) continue;
                System.out.println("  [push] " + (m.length() > 80 ? m.substring(0, 80) + "..." : m));
            }
            playGame(me, roomId, pl);
            pl.close();
            System.exit(0);
        }
    }
}
