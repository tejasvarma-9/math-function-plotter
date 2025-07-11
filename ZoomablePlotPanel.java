package ui;

import functions.Function;
import solver.ExtremaFinder;
import solver.InflectionFinder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

public class ZoomablePlotPanel extends JPanel {
    private List<Function> functions = new ArrayList<>();
    private List<Point.Double> intersectionPoints = new ArrayList<>();
    private List<Point2D.Double> extremaPoints = new ArrayList<>();
    private List<Point2D.Double> inflectionPoints = new ArrayList<>();
    private Map<Point, Point2D.Double> allPlottedPoints = new HashMap<>();
    
    // Enhanced click detection with point types - using String keys for better matching
    private Map<String, String> pointTypes = new HashMap<>(); // "x,y" -> "type"
    private static final int CLICK_TOLERANCE = 15; // Increased tolerance for easier clicking

    private double scale = 40;
    private double offsetX = 0;
    private double offsetY = 0;
    private Point lastMouse;

    private double areaX1 = Double.NaN, areaX2 = Double.NaN;
    private BiConsumer<Double, Double> clickListener;

    public ZoomablePlotPanel() {
        setBackground(Color.WHITE);
        enablePanAndZoom();
        enableClickDetection();
    }

    public void addFunction(Function f) {
        functions.add(f);
    }

    public void markIntersectionPoints(List<Point.Double> points) {
        intersectionPoints.clear();
        intersectionPoints.addAll(points);
        // Mark point types for click detection using string keys
        for (Point.Double p : points) {
            String key = String.format("%.6f,%.6f", p.x, p.y);
            pointTypes.put(key, "Intersection");
        }
    }

    public void markExtremaAndInflection(Function f, double xStart, double xEnd, boolean markExtrema, boolean markInflection) {
        if (markExtrema) {
            extremaPoints.clear();
            List<Double> extrema = ExtremaFinder.findExtrema(f, xStart, xEnd, 0.1);
            for (double x : extrema) {
                double y = f.evaluate(x);
                Point2D.Double point = new Point2D.Double(x, y);
                extremaPoints.add(point);
                
                // Determine if it's a maximum or minimum
                double h = 0.001;
                double leftVal = f.evaluate(x - h);
                double rightVal = f.evaluate(x + h);
                String type = (leftVal < y && rightVal < y) ? "Maximum" : "Minimum";
                
                String key = String.format("%.6f,%.6f", x, y);
                pointTypes.put(key, type);
                System.out.println("Added extrema point: " + key + " -> " + type);
            }
        }
        
        if (markInflection) {
            inflectionPoints.clear();
            List<Double> inflections = InflectionFinder.findInflectionPoints(f, xStart, xEnd, 0.1);
            for (double x : inflections) {
                double y = f.evaluate(x);
                Point2D.Double point = new Point2D.Double(x, y);
                inflectionPoints.add(point);
                
                String key = String.format("%.6f,%.6f", x, y);
                pointTypes.put(key, "Inflection");
                System.out.println("Added inflection point: " + key + " -> Inflection");
            }
        }
    }

    public void clearAll() {
        functions.clear();
        intersectionPoints.clear();
        extremaPoints.clear();
        inflectionPoints.clear();
        allPlottedPoints.clear();
        pointTypes.clear();
        areaX1 = Double.NaN;
        areaX2 = Double.NaN;
    }

    public void setClickListener(BiConsumer<Double, Double> listener) {
        this.clickListener = listener;
    }

    public void shadeAndCalculateArea(double x1, double x2) {
        areaX1 = x1;
        areaX2 = x2;
        repaint();
    }

    private void enablePanAndZoom() {
        addMouseWheelListener(e -> {
            double delta = e.getPreciseWheelRotation();
            scale *= (1 - delta * 0.1);
            if (scale < 5) scale = 5;
            if (scale > 300) scale = 300;
            repaint();
        });

        MouseAdapter panningAdapter = new MouseAdapter() {
            private boolean isDragging = false;
            private Point startPoint;
            
            public void mousePressed(MouseEvent e) {
                lastMouse = e.getPoint();
                startPoint = e.getPoint();
                isDragging = false;
            }
            
            public void mouseDragged(MouseEvent e) {
                if (!isDragging) {
                    // Only start dragging if we've moved more than a few pixels
                    if (startPoint.distance(e.getPoint()) > 3) {
                        isDragging = true;
                    }
                }
                
                if (isDragging) {
                    Point current = e.getPoint();
                    offsetX -= (current.x - lastMouse.x) / scale;
                    offsetY += (current.y - lastMouse.y) / scale;
                    lastMouse = current;
                    repaint();
                }
            }
            
            public void mouseReleased(MouseEvent e) {
                if (!isDragging && startPoint != null) {
                    // This was a click, not a drag
                    handleClick(e);
                }
                isDragging = false;
                startPoint = null;
            }
        };
        
        addMouseListener(panningAdapter);
        addMouseMotionListener(panningAdapter);
    }

    private void enableClickDetection() {
        // Only add mouse motion listener for hover effects
        addMouseMotionListener(new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                Point2D.Double hoveredPoint = findClickedPoint(e.getPoint());
                if (hoveredPoint != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    String pointType = getPointType(hoveredPoint);
                    setToolTipText(pointType + " point: (" + 
                                 String.format("%.4f", hoveredPoint.x) + ", " + 
                                 String.format("%.4f", hoveredPoint.y) + ")");
                } else {
                    setCursor(Cursor.getDefaultCursor());
                    setToolTipText(null);
                }
            }
        });
    }
    
    private void handleClick(MouseEvent e) {
        // Debug: Print click coordinates
        System.out.println("Mouse clicked at: " + e.getPoint());
        
        Point2D.Double clickedPoint = findClickedPoint(e.getPoint());
        if (clickedPoint != null) {
            String pointType = getPointType(clickedPoint);
            System.out.println("Clicked " + pointType + " point at (" + 
                             String.format("%.4f", clickedPoint.x) + ", " + 
                             String.format("%.4f", clickedPoint.y) + ")");
            if (clickListener != null) {
                clickListener.accept(clickedPoint.x, clickedPoint.y);
            }
        } else {
            System.out.println("No point found at click location");
            // Debug: Print all point locations
            System.out.println("Available points:");
            for (Point.Double p : intersectionPoints) {
                Point screenPoint = toScreen(p.x, p.y);
                System.out.println("  Intersection: " + p + " -> screen: " + screenPoint);
            }
            for (Point2D.Double p : extremaPoints) {
                Point screenPoint = toScreen(p.x, p.y);
                System.out.println("  Extrema: " + p + " -> screen: " + screenPoint);
            }
            for (Point2D.Double p : inflectionPoints) {
                Point screenPoint = toScreen(p.x, p.y);
                System.out.println("  Inflection: " + p + " -> screen: " + screenPoint);
            }
        }
    }

    private Point2D.Double findClickedPoint(Point mousePoint) {
        double tolerance = CLICK_TOLERANCE;
        
        // Debug: Print mouse point
        System.out.println("Looking for points near: " + mousePoint);
        
        // Check intersection points
        for (Point.Double p : intersectionPoints) {
            Point screenPoint = toScreen(p.x, p.y);
            if (screenPoint != null) {
                double distance = mousePoint.distance(screenPoint);
                System.out.println("Intersection point " + p + " at screen " + screenPoint + 
                                 ", distance: " + distance);
                if (distance <= tolerance) {
                    return new Point2D.Double(p.x, p.y);
                }
            }
        }
        
        // Check extrema points
        for (Point2D.Double p : extremaPoints) {
            Point screenPoint = toScreen(p.x, p.y);
            if (screenPoint != null) {
                double distance = mousePoint.distance(screenPoint);
                System.out.println("Extrema point " + p + " at screen " + screenPoint + 
                                 ", distance: " + distance);
                if (distance <= tolerance) {
                    return p;
                }
            }
        }
        
        // Check inflection points
        for (Point2D.Double p : inflectionPoints) {
            Point screenPoint = toScreen(p.x, p.y);
            if (screenPoint != null) {
                double distance = mousePoint.distance(screenPoint);
                System.out.println("Inflection point " + p + " at screen " + screenPoint + 
                                 ", distance: " + distance);
                if (distance <= tolerance) {
                    return p;
                }
            }
        }
        
        return null;
    }
    
    // Helper method to get point type using string key
    private String getPointType(Point2D.Double point) {
        String key = String.format("%.6f,%.6f", point.x, point.y);
        return pointTypes.get(key);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        allPlottedPoints.clear();

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int centerX = w / 2;
        int centerY = h / 2;

        double minX = -centerX / scale + offsetX;
        double maxX = centerX / scale + offsetX;
        double minY = -centerY / scale + offsetY;
        double maxY = centerY / scale + offsetY;

        // Grid
        g2.setColor(Color.LIGHT_GRAY);
        for (int i = (int) Math.floor(minX); i <= maxX; i++) {
            int x = (int) (centerX + (i - offsetX) * scale);
            g2.drawLine(x, 0, x, h);
        }
        for (int i = (int) Math.floor(minY); i <= maxY; i++) {
            int y = (int) (centerY - (i - offsetY) * scale);
            g2.drawLine(0, y, w, y);
        }

        // Axes
        g2.setColor(Color.BLACK);
        int axisX = (int) (centerX - offsetX * scale);
        int axisY = (int) (centerY + offsetY * scale);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(0, axisY, w, axisY);
        g2.drawLine(axisX, 0, axisX, h);

        // Axis labels
        g2.setFont(new Font("Arial", Font.PLAIN, 10));
        g2.setColor(Color.DARK_GRAY);
        for (int i = (int) Math.floor(minX); i <= maxX; i++) {
            int x = (int) (centerX + (i - offsetX) * scale);
            g2.drawString(Integer.toString(i), x + 2, axisY + 12);
        }
        for (int i = (int) Math.floor(minY); i <= maxY; i++) {
            int y = (int) (centerY - (i - offsetY) * scale);
            g2.drawString(Integer.toString(i), axisX + 5, y - 3);
        }

        // Function curves
        for (Function f : functions) {
            g2.setColor(f.getColor());
            g2.setStroke(new BasicStroke(2));
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (double x = minX; x <= maxX; x += 0.01) {
                double y = f.evaluate(x);
                if (Double.isNaN(y) || Double.isInfinite(y)) continue;
                Point screen = toScreen(x, y);
                if (screen == null) continue;

                allPlottedPoints.put(screen, new Point2D.Double(x, y));

                if (first) {
                    path.moveTo(screen.x, screen.y);
                    first = false;
                } else {
                    path.lineTo(screen.x, screen.y);
                }
            }
            g2.draw(path);
        }

        // Area shading
        if (!Double.isNaN(areaX1) && !Double.isNaN(areaX2) && functions.size() >= 1) {
            Function f = functions.get(0);
            g2.setColor(new Color(0, 0, 255, 50));

            double area = 0;
            double step = 0.01;
            for (double x = areaX1; x < areaX2; x += step) {
                double y1 = f.evaluate(x);
                double y2 = f.evaluate(x + step);
                double avg = (y1 + y2) / 2;
                area += avg * step;

                int sx1 = (int) (centerX + (x - offsetX) * scale);
                int sx2 = (int) (centerX + (x + step - offsetX) * scale);
                int sy1 = (int) (centerY - (y1 - offsetY) * scale);
                int sy2 = (int) (centerY - (y2 - offsetY) * scale);
                int baseY = (int) (centerY - (0 - offsetY) * scale);

                Polygon trapezoid = new Polygon();
                trapezoid.addPoint(sx1, baseY);
                trapezoid.addPoint(sx1, sy1);
                trapezoid.addPoint(sx2, sy2);
                trapezoid.addPoint(sx2, baseY);
                g2.fill(trapezoid);
            }

            g2.setColor(Color.BLUE);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            int textX = (int) (centerX + ((areaX1 + areaX2) / 2 - offsetX) * scale);
            int textY = (int) (centerY - (f.evaluate((areaX1 + areaX2) / 2) - offsetY) * scale) - 10;
            g2.drawString(String.format("Area ≈ %.4f", area), textX, textY);
        }

        // Draw points with enhanced visibility
        drawEnhancedPoints(g2, intersectionPoints, centerX, centerY, Color.RED, "Intersection");
        drawEnhancedPoints(g2, extremaPoints, centerX, centerY, Color.MAGENTA, "Extrema");
        drawEnhancedPoints(g2, inflectionPoints, centerX, centerY, Color.ORANGE, "Inflection");
    }

    private void drawEnhancedPoints(Graphics2D g2, List<? extends Point2D> points, int cx, int cy, Color color, String type) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2));
        
        for (Point2D p : points) {
            int sx = (int) (cx + (p.getX() - offsetX) * scale);
            int sy = (int) (cy - (p.getY() - offsetY) * scale);
            
            // Draw filled circle
            g2.fill(new Ellipse2D.Double(sx - 4, sy - 4, 8, 8));
            
            // Draw white border for better visibility
            g2.setColor(Color.WHITE);
            g2.draw(new Ellipse2D.Double(sx - 4, sy - 4, 8, 8));
            
            // Draw black outline
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(sx - 5, sy - 5, 10, 10));
            
            // Reset color for next point
            g2.setColor(color);
        }
    }

    private void drawPoints(Graphics2D g2, List<? extends Point2D> points, int cx, int cy, Color color) {
        g2.setColor(color);
        for (Point2D p : points) {
            int sx = (int) (cx + (p.getX() - offsetX) * scale);
            int sy = (int) (cy - (p.getY() - offsetY) * scale);
            g2.fill(new Ellipse2D.Double(sx - 3, sy - 3, 6, 6));
        }
    }

    private Point toScreen(double x, double y) {
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int sx = (int) (cx + (x - offsetX) * scale);
        int sy = (int) (cy - (y - offsetY) * scale);
        return new Point(sx, sy);
    }
}