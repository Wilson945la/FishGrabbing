import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class AeroChessPreview {
    public static void main(String[] args) {
        AeroChessGame game = new AeroChessGame("玩家1", 1, 12345L, "经典",
                Arrays.asList("玩家1", "玩家2", "玩家3", "玩家4"), null);
        game.setVisible(true);
        try {
            Thread.sleep(300);

            // 关闭 AeroChessGame 之外的所有窗口（消息中心、匹配房间等）
            for (Window w : Window.getWindows()) {
                if (w != game && w.isDisplayable() && w.isVisible()) {
                    try { w.dispose(); } catch (Exception ignored) {}
                }
            }
            Thread.sleep(200);

            // 直接把 AeroChessGame 绘制到 BufferedImage，避免被屏幕其他窗口遮挡
            int w0 = game.getWidth();
            int h0 = game.getHeight();
            BufferedImage img = new BufferedImage(w0, h0, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            game.paint(g2);
            g2.dispose();
            ImageIO.write(img, "png",
                    new File("C:/Users/caohua/Desktop/IDEAproject/test/飞行棋预览.png"));
            System.out.println("preview saved: " + w0 + "x" + h0);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
