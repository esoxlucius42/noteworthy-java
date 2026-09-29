package dev.noteworthy.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

final class AppIcon {
    private static final int DESIGN_SIZE = 128;

    private AppIcon() { }

    static List<Image> images() {
        return List.of(16, 24, 32, 48, 64, 128, 256).stream()
                .map(AppIcon::render)
                .map(image -> (Image) image)
                .toList();
    }

    private static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.scale(size / (double) DESIGN_SIZE, size / (double) DESIGN_SIZE);

        graphics.setColor(new Color(18, 31, 48));
        graphics.fill(new RoundRectangle2D.Double(4, 4, 120, 120, 30, 30));
        graphics.setColor(new Color(58, 91, 116));
        graphics.setStroke(new BasicStroke(2.5f));
        graphics.draw(new RoundRectangle2D.Double(5.25, 5.25, 117.5, 117.5, 28, 28));

        graphics.setColor(new Color(225, 237, 243));
        graphics.fill(new RoundRectangle2D.Double(27, 15, 70, 94, 11, 11));

        Path2D fold = new Path2D.Double();
        fold.moveTo(75, 16);
        fold.lineTo(96, 37);
        fold.lineTo(75, 37);
        fold.closePath();
        graphics.setColor(new Color(173, 202, 214));
        graphics.fill(fold);

        graphics.setColor(new Color(90, 125, 145));
        graphics.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(40, 51, 79, 51);
        graphics.drawLine(40, 66, 81, 66);
        graphics.drawLine(40, 81, 65, 81);

        graphics.setColor(new Color(46, 155, 112));
        graphics.fill(new RoundRectangle2D.Double(68, 70, 48, 48, 24, 24));
        graphics.setColor(new Color(234, 250, 239));
        graphics.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Double();
        check.moveTo(79, 94);
        check.lineTo(89, 104);
        check.lineTo(106, 85);
        graphics.draw(check);

        graphics.dispose();
        return image;
    }
}