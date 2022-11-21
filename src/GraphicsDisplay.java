import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;

import javax.swing.JPanel;

public class GraphicsDisplay extends JPanel {

    private ArrayList<Double[]> graphicsData;
    private ArrayList<Double[]> originalData;
    private int selectedMarker = -1;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;

    private double scaleX;
    private double scaleY;

    private double[][] viewport = new double[2][2];
    private ArrayList<double[][]> undoHistory = new ArrayList(5);
    private boolean showAxis = true;
    private boolean showMarkers = true;
    private boolean rotateLeft = false;

    private Font axisFont;
    private Font labelsFont;

    private BasicStroke axisStroke;
    private BasicStroke markerStroke;
    private BasicStroke gridStroke;
    private BasicStroke selectionStroke;
    private static DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance();

    private boolean scaleMode = false;
    private boolean changeMode = false;
    private boolean showGrid = false;
    private double[] originalPoint = new double[2];
    private Rectangle2D.Double selectionRect = new Rectangle2D.Double();

    public GraphicsDisplay() {
        setBackground(Color.WHITE);
        axisStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, null, 0.0f);
        markerStroke = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 5.0f, new float[]{4, 1, 1, 1, 2, 1, 1, 1, 4}, 0.0f);
        selectionStroke = new BasicStroke(1.0F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0F, new float[]{10, 10}, 0.0F);
        gridStroke = new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 5.0f, new float[]{5, 5}, 2.0f);
        axisFont = new Font("Serif", Font.BOLD, 36);
        labelsFont = new java.awt.Font("Serif", Font.PLAIN, 10);
        addMouseMotionListener(new MouseMotionHandler());
        addMouseListener(new MouseHandler());
    }

    public void showGraphics(ArrayList<Double[]> graphicsData) {
        this.graphicsData = graphicsData;


        this.originalData = new ArrayList<>(graphicsData.size());

        for (Double[] point : graphicsData) {
            Double[] newPoint = new Double[2];
            newPoint[0] = point[0];
            newPoint[1] = point[1];
            this.originalData.add(newPoint);
        }

        this.minX = ((Double[]) graphicsData.get(0))[0];
        this.maxX = ((Double[]) graphicsData.get(graphicsData.size() - 1))[0];
        this.minY = ((Double[]) graphicsData.get(0))[1];
        this.maxY = this.minY;

        for (int i = 1; i < graphicsData.size(); i++) {
            if (((Double[]) graphicsData.get(i))[1] < this.minY) {
                this.minY = ((Double[]) graphicsData.get(i))[1];
            }
            if (((Double[]) graphicsData.get(i))[1] > this.maxY) {
                this.maxY = ((Double[]) graphicsData.get(i))[1];
            }
        }

        zoomToRegion(minX, maxY, maxX, minY);
    }

    public void zoomToRegion(double x1, double y1, double x2, double y2) {
        this.viewport[0][0] = x1;
        this.viewport[0][1] = y1;
        this.viewport[1][0] = x2;
        this.viewport[1][1] = y2;
        this.repaint();
    }

    public void setShowAxis(boolean showAxis) {
        this.showAxis = showAxis;
        repaint();
    }

    public void setShowMarkers(boolean showMarkers) {
        this.showMarkers = showMarkers;
        repaint();
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        repaint();
    }

    protected Point2D.Double xyToPoint(double x, double y) {
        double deltaX = x - viewport[0][0];
        double deltaY = viewport[0][1] - y;
        return new Point2D.Double(deltaX * scaleX, deltaY * scaleY);
    }

    protected double[] translatePointToXY(int x, int y) {
        return new double[]{this.viewport[0][0] + x / this.scaleX, this.viewport[0][1] - y / this.scaleY};
    }

    protected Point2D.Double shiftPoint(Point2D.Double src, double deltaX, double deltaY) {
        Point2D.Double dest = new Point2D.Double();
        dest.setLocation(src.getX() + deltaX, src.getY() + deltaY);
        return dest;
    }

    protected void paintGrid(Graphics2D canvas) {
        canvas.setStroke(gridStroke);
        canvas.setColor(Color.GRAY);

        double pos = viewport[0][0];
        ;
        double step = (viewport[1][0] - viewport[0][0]) / 10;

        while (pos < viewport[1][0]) {
            canvas.draw(new Line2D.Double(xyToPoint(pos, viewport[0][1]), xyToPoint(pos, viewport[1][1])));
            pos += step;
        }
        canvas.draw(new Line2D.Double(xyToPoint(viewport[1][0], viewport[0][1]), xyToPoint(viewport[1][0], viewport[1][1])));

        pos = viewport[1][1];
        step = (viewport[0][1] - viewport[1][1]) / 10;
        while (pos < viewport[0][1]) {
            canvas.draw(new Line2D.Double(xyToPoint(viewport[0][0], pos), xyToPoint(viewport[1][0], pos)));
            pos = pos + step;
        }
        canvas.draw(new Line2D.Double(xyToPoint(viewport[0][0], viewport[0][1]), xyToPoint(viewport[1][0], viewport[0][1])));
    }

    protected void paintGraphics(Graphics2D canvas) {
        canvas.setStroke(this.markerStroke);
        canvas.setColor(Color.RED);

        Double currentX = null;
        Double currentY = null;
        for (Double[] point : this.graphicsData) {
            if ((point[0] >= this.viewport[0][0]) && (point[1] <= this.viewport[0][1]) &&
                    (point[0] <= this.viewport[1][0]) && (point[1] >= this.viewport[1][1])) {
                if ((currentX != null) && (currentY != null)) {
                    canvas.draw(new Line2D.Double(xyToPoint(currentX, currentY),
                            xyToPoint(point[0], point[1])));
                }
                currentX = point[0];
                currentY = point[1];
            }
        }
    }

    protected void paintAxis(Graphics2D canvas) {
        canvas.setStroke(this.axisStroke);
        canvas.setColor(java.awt.Color.BLACK);
        canvas.setFont(this.axisFont);
        FontRenderContext context = canvas.getFontRenderContext();
        if (!(viewport[0][0] > 0 || viewport[1][0] < 0)) {
            canvas.draw(new Line2D.Double(xyToPoint(0, viewport[0][1]),
                    xyToPoint(0, viewport[1][1])));
            canvas.draw(new Line2D.Double(xyToPoint(-(viewport[1][0] - viewport[0][0]) * 0.0025,
                    viewport[0][1] - (viewport[0][1] - viewport[1][1]) * 0.015), xyToPoint(0, viewport[0][1])));
            canvas.draw(new Line2D.Double(xyToPoint((viewport[1][0] - viewport[0][0]) * 0.0025,
                    viewport[0][1] - (viewport[0][1] - viewport[1][1]) * 0.015),
                    xyToPoint(0, viewport[0][1])));
            Rectangle2D bounds = axisFont.getStringBounds("y", context);
            Point2D.Double labelPos = xyToPoint(0.0, viewport[0][1]);
            canvas.drawString("y", (float) labelPos.x + 10, (float) (labelPos.y + bounds.getHeight() / 2));
        }
        if (!(viewport[1][1] > 0.0D || viewport[0][1] < 0.0D)) {
            canvas.draw(new Line2D.Double(xyToPoint(viewport[0][0], 0),
                    xyToPoint(viewport[1][0], 0)));
            canvas.draw(new Line2D.Double(xyToPoint(viewport[1][0] - (viewport[1][0] - viewport[0][0]) * 0,
                    (viewport[0][1] - viewport[1][1]) * 0.005), xyToPoint(viewport[1][0], 0)));
            canvas.draw(new Line2D.Double(xyToPoint(viewport[1][0] - (viewport[1][0] - viewport[0][0]) * 0.01,
                    -(viewport[0][1] - viewport[1][1]) * 0.005), xyToPoint(viewport[1][0], 0)));
            Rectangle2D bounds = axisFont.getStringBounds("x", context);
            Point2D.Double labelPos = xyToPoint(this.viewport[1][0], 0.0D);
            canvas.drawString("x", (float) (labelPos.x - bounds.getWidth() - 10), (float) (labelPos.y - bounds.getHeight() / 2));
        }
    }

    protected void paintMarkers(Graphics2D canvas) {
        canvas.setStroke(this.markerStroke);
        canvas.setColor(Color.RED);
        canvas.setPaint(Color.RED);
        GeneralPath lastMarker = null;
        int i = -1;
        for (Double[] point : graphicsData) {
            i++;

            if (isSpecialPoint(point[1]))
                canvas.setColor(Color.GREEN);
            else
                canvas.setColor(Color.RED);

            // markers
            GeneralPath star = new GeneralPath();
            Point2D.Double center = xyToPoint(point[0], point[1]);
            star.moveTo(center.getX(), center.getY());
            star.lineTo(star.getCurrentPoint().getX(), star.getCurrentPoint().getY() - 5);
            star.moveTo(star.getCurrentPoint().getX() - 3, star.getCurrentPoint().getY());
            star.lineTo(star.getCurrentPoint().getX() + 6, star.getCurrentPoint().getY());
            star.moveTo(center.getX(), center.getY());
            star.lineTo(star.getCurrentPoint().getX(), star.getCurrentPoint().getY() + 5);
            star.moveTo(star.getCurrentPoint().getX() - 3, star.getCurrentPoint().getY());
            star.lineTo(star.getCurrentPoint().getX() + 6, star.getCurrentPoint().getY());
            star.moveTo(center.getX(), center.getY());
            star.lineTo(star.getCurrentPoint().getX() - 5, star.getCurrentPoint().getY());
            star.moveTo(star.getCurrentPoint().getX(), star.getCurrentPoint().getY() - 3);
            star.lineTo(star.getCurrentPoint().getX(), star.getCurrentPoint().getY() + 6);
            star.moveTo(center.getX(), center.getY());
            star.lineTo(star.getCurrentPoint().getX() + 5, star.getCurrentPoint().getY());
            star.moveTo(star.getCurrentPoint().getX(), star.getCurrentPoint().getY() - 3);
            star.lineTo(star.getCurrentPoint().getX(), star.getCurrentPoint().getY() + 6);
            if (i == this.selectedMarker) {
                lastMarker = star;
            } else {
                canvas.draw(star);
                canvas.fill(star);
            }
        }

        if (lastMarker != null) {
            canvas.setColor(Color.BLUE);
            canvas.setPaint(Color.BLUE);
            canvas.draw(lastMarker);
            canvas.fill(lastMarker);
        }
    }

    protected boolean isSpecialPoint(double y) {
        int func = (int)y;

        while (func > 0){
            if((func % 10) % 2 != 0)
                return false;
        }

        return true;
    }

    private void paintLabels(Graphics2D canvas) {
        // Подписи координат и сетки
        canvas.setColor(Color.BLACK);
        canvas.setFont(this.labelsFont);
        FontRenderContext context = canvas.getFontRenderContext();
        double labelYPos;
        double labelXPos;
        if (!(viewport[1][1] >= 0 || viewport[0][1] <= 0))
            labelYPos = 0;
        else labelYPos = viewport[1][1];
        if (!(viewport[0][0] >= 0 || viewport[1][0] <= 0.0D))
            labelXPos = 0;
        else labelXPos = viewport[0][0];
        double pos = viewport[0][0];
        double step = (viewport[1][0] - viewport[0][0]) / 10;
        while (pos < viewport[1][0]) {
            java.awt.geom.Point2D.Double point = xyToPoint(pos, labelYPos);
            String label = formatter.format(pos);
            Rectangle2D bounds = labelsFont.getStringBounds(label, context);
            canvas.drawString(label, (float) (point.getX() + 5), (float) (point.getY() - bounds.getHeight()));
            pos = pos + step;
        }
        pos = viewport[1][1];
        step = (viewport[0][1] - viewport[1][1]) / 10.0D;
        while (pos < viewport[0][1]) {
            Point2D.Double point = xyToPoint(labelXPos, pos);
            String label = formatter.format(pos);
            Rectangle2D bounds = labelsFont.getStringBounds(label, context);
            canvas.drawString(label, (float) (point.getX() + 5), (float) (point.getY() - bounds.getHeight()));
            pos = pos + step;
        }
        if (selectedMarker >= 0) {
            Point2D.Double point = xyToPoint(((Double[]) graphicsData.get(selectedMarker))[0],
                    ((Double[]) graphicsData.get(selectedMarker))[1]);
            String label = "X=" + formatter.format(((Double[]) graphicsData.get(selectedMarker))[0]) +
                    ", Y=" + formatter.format(((Double[]) graphicsData.get(selectedMarker))[1]);
            Rectangle2D bounds = labelsFont.getStringBounds(label, context);
            canvas.setColor(Color.BLACK);
            canvas.drawString(label, (float) (point.getX() + 5.0D), (float) (point.getY() - bounds.getHeight()));
        }
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        scaleX = this.getSize().getWidth() / (this.viewport[1][0] - this.viewport[0][0]);
        scaleY = this.getSize().getHeight() / (this.viewport[0][1] - this.viewport[1][1]);
        if ((this.graphicsData == null) || (this.graphicsData.size() == 0)) return;

        Graphics2D canvas = (Graphics2D) g;
        Stroke oldStroke = canvas.getStroke();
        Color oldColor = canvas.getColor();
        Font oldFont = canvas.getFont();
        Paint oldPaint = canvas.getPaint();

        if (rotateLeft) {
            AffineTransform at = AffineTransform.getRotateInstance(-Math.PI/2, getSize().getWidth()/2, getSize().getHeight()/2);
            at.concatenate(new AffineTransform(getSize().getHeight()/getSize().getWidth(), 0.0, 0.0, getSize().getWidth()/getSize().getHeight(),
                    (getSize().getWidth()-getSize().getHeight())/2, (getSize().getHeight()-getSize().getWidth())/2));
            canvas.setTransform(at);
        }

        if (showGrid)
            paintGrid(canvas);

        if (showAxis) {
            paintAxis(canvas);
            paintLabels(canvas);
        }

        paintGraphics(canvas);

        if (showMarkers)
            paintMarkers(canvas);

        paintSelection(canvas);
        canvas.setFont(oldFont);
        canvas.setPaint(oldPaint);
        canvas.setColor(oldColor);
        canvas.setStroke(oldStroke);
    }

    private void paintSelection(Graphics2D canvas) {
        if (!scaleMode) return;
        canvas.setStroke(selectionStroke);
        canvas.setColor(Color.BLACK);
        canvas.draw(selectionRect);
    }

    // set left
    public void setRotateLeft(boolean rotateLeft) {
        this.rotateLeft = rotateLeft;
        repaint();
    }

    // Сбрасываем изменения
    public void reset() {
        showGraphics(this.originalData);
    }

    //zoom
    protected int findSelectedPoint(int x, int y) {
        if (graphicsData == null) return -1;
        int pos = 0;
        for (Double[] point : graphicsData) {
            Point2D.Double screenPoint = xyToPoint(point[0], point[1]);
            double distance = (screenPoint.getX() - x) * (screenPoint.getX() - x) + (screenPoint.getY() - y) * (screenPoint.getY() - y);
            if (distance < 100) return pos;
            pos++;
        }
        return -1;
    }

    public void saveToFile(File selectedFile) {
        try {
            PrintStream out = new PrintStream(selectedFile);
            out.println("Результаты скорректированых значений");
            for (Double[] point : graphicsData) {
                out.println(point[0] + " " + point[1]);
            }

            out.close();

        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

    }


    public class MouseHandler extends MouseAdapter {
        public MouseHandler() {
        }

        public void mouseClicked(MouseEvent ev) {
            if (ev.getButton() == 3) {
                if (undoHistory.size() > 0) {
                    viewport = undoHistory.get(undoHistory.size() - 1);

                    undoHistory.remove(undoHistory.size() - 1);
                } else {
                    zoomToRegion(minX, maxY, maxX, minY);
                }
                repaint();
            }
        }

        public void mousePressed(MouseEvent ev) {
            if (ev.getButton() != 1) return;
            selectedMarker = findSelectedPoint(ev.getX(), ev.getY());
            originalPoint = translatePointToXY(ev.getX(), ev.getY());
            if (selectedMarker >= 0) {
                changeMode = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            } else {
                scaleMode = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                selectionRect.setFrame(ev.getX(), ev.getY(), 1.0D, 1.0D);
            }
        }

        public void mouseReleased(MouseEvent ev) {
            if (ev.getButton() != 1) return;

            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            if (changeMode) {
                changeMode = false;
            } else {
                scaleMode = false;
                double[] finalPoint = translatePointToXY(ev.getX(), ev.getY());
                undoHistory.add(viewport);
                viewport = new double[2][2];
                zoomToRegion(originalPoint[0], originalPoint[1], finalPoint[0], finalPoint[1]);
                repaint();
            }
        }
    }

    public class MouseMotionHandler implements MouseMotionListener {

        public void mouseDragged(MouseEvent ev) {
            if (changeMode) {
                //rotate
                double[] currentPoint = translatePointToXY(ev.getX(), ev.getY());
                double newY = ((Double[]) graphicsData.get(selectedMarker))[1] +
                        (currentPoint[1] - ((Double[]) graphicsData.get(selectedMarker))[1]);
                if (newY > viewport[0][1]) {
                    newY = viewport[0][1];
                }
                if (newY < viewport[1][1]) {
                    newY = viewport[1][1];
                }
                graphicsData.get(selectedMarker)[1] = newY;
                repaint();
            } else {
                double width = ev.getX() - selectionRect.getX();
                if (width < 5.0D) {
                    width = 5.0D;
                }
                double height = ev.getY() - selectionRect.getY();
                if (height < 5.0D) {
                    height = 5.0D;
                }
                selectionRect.setFrame(selectionRect.getX(), selectionRect.getY(), width, height);
                repaint();
            }
        }

        public void mouseMoved(MouseEvent ev) {
            selectedMarker = findSelectedPoint(ev.getX(), ev.getY());
            if (selectedMarker >= 0)
                setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            else {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
            repaint();
        }

    }

}