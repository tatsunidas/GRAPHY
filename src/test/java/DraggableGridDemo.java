import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

@SuppressWarnings("serial")
public class DraggableGridDemo extends JFrame {

    public DraggableGridDemo() {
        setTitle("Grid Drag & Insert Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 600);
        setLocationRelativeTo(null);

        // GlassPane（ゴースト描画用）の準備
        GhostGlassPane glassPane = new GhostGlassPane();
        setGlassPane(glassPane);

        // ドラッグ可能なグリッドパネルの作成
        DraggableGridPanel gridPanel = new DraggableGridPanel(3, 10, 10); // 3列, 隙間10px
        gridPanel.setGhostGlassPane(glassPane);
        
        // テスト用アイテムを追加
		for (int i = 0; i < 9; i++) {
			JPanel item = createItemPanel(i);
			gridPanel.addDraggableItem(item);
		}

        add(new JScrollPane(gridPanel));
    }

    // グリッド内のアイテム（パネル）を作成するヘルパー
    private JPanel createItemPanel(int index) {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.setBackground(new Color(100 + index * 15, 200 - index * 10, 255));
        p.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        p.setPreferredSize(new Dimension(100, 100)); // 推奨サイズ
        
        JLabel label = new JLabel("Box " + (index + 1), SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 24));
        p.add(label, BorderLayout.CENTER);
        
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DraggableGridDemo().setVisible(true));
    }
}

/**
 * ドラッグ＆ドロップ機能を備えたグリッドパネル
 */
@SuppressWarnings("serial")
class DraggableGridPanel extends JPanel {
    private GhostGlassPane glassPane;
    private Component draggingComponent = null; // 現在ドラッグ中のコンポーネント
    private int insertionIndex = -1; // ドロップ予定のインデックス

    public DraggableGridPanel(int columns, int hGap, int vGap) {
        super(new GridLayout(0, columns, hGap, vGap)); // 行は可変(0)、列は指定
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    public void setGhostGlassPane(GhostGlassPane glassPane) {
        this.glassPane = glassPane;
    }

    // アイテムを追加し、ドラッグリスナーを設定するメソッド
    public void addDraggableItem(Component comp) {
        DragHandler handler = new DragHandler();
        comp.addMouseListener(handler);
        comp.addMouseMotionListener(handler);
        add(comp);
    }

    // 挿入位置のライン（キャレット）を描画
    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g); // 子コンポーネントを描画

        if (draggingComponent != null && insertionIndex >= 0) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(4f));

            // 挿入位置の座標計算
            Rectangle bounds;
            int x, y, h;

            int componentCount = getComponentCount();
            
            if (componentCount == 0) return;

            // 末尾への追加か、既存要素の前への挿入か
            if (insertionIndex < componentCount) {
                // 既存のコンポーネントの「左側」に描画
                Component target = getComponent(insertionIndex);
                bounds = target.getBounds();
                x = bounds.x;
                y = bounds.y;
                h = bounds.height;
            } else {
                // 最後のコンポーネントの「右側」に描画
                Component last = getComponent(componentCount - 1);
                bounds = last.getBounds();
                x = bounds.x + bounds.width;
                y = bounds.y;
                h = bounds.height;
            }

            // グリッドの隙間(gap)を考慮して少し調整
            int gapAdjustment = ((GridLayout)getLayout()).getHgap() / 2;
            g2.drawLine(x - gapAdjustment, y, x - gapAdjustment, y + h);
        }
    }

    /**
     * ドラッグ操作を処理する内部クラス
     * (各アイテムに割り当てられる)
     */
    private class DragHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            Component source = (Component) e.getSource();
            draggingComponent = source;

            // ゴースト画像の作成
            BufferedImage ghostImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = ghostImage.createGraphics();
            source.paint(g2); // コンポーネントの見た目を描画
            g2.dispose();

            // スクリーン座標へ変換してGlassPaneに通知
            Point p = e.getPoint();
            SwingUtilities.convertPointToScreen(p, source);
            glassPane.startDrag(ghostImage, p);
            glassPane.setVisible(true);
            
            // 視覚的に「掴んでいる」ことを示すために元のコンポーネントを少し薄くする等の処理も可能
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (draggingComponent == null) return;

            // 1. GlassPaneのゴースト移動
            Component source = (Component) e.getSource();
            Point screenP = e.getPoint();
            SwingUtilities.convertPointToScreen(screenP, source);
            glassPane.moveDrag(screenP);

            // 2. 挿入位置の計算
            // 親パネル(DraggableGridPanel)基準の座標に変換
            Point panelPoint = SwingUtilities.convertPoint(source, e.getPoint(), DraggableGridPanel.this);
            updateInsertionIndex(panelPoint);
            
            // パネル全体の再描画（赤い線を描くため）
            DraggableGridPanel.this.repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (draggingComponent != null && insertionIndex != -1) {
                performReorder();
            }

            // リセット
            draggingComponent = null;
            insertionIndex = -1;
            glassPane.setVisible(false);
            DraggableGridPanel.this.repaint();
        }
    }

    /**
     * マウス座標から挿入すべきインデックスを計算するロジック
     */
    private void updateInsertionIndex(Point p) {
        int count = getComponentCount();
        int closestIndex = -1;
        double minDistance = Double.MAX_VALUE;

        // 全コンポーネントを走査して、マウスに最も近いものを探す
        for (int i = 0; i < count; i++) {
            Component c = getComponent(i);
            Rectangle b = c.getBounds();
            
            // コンポーネントの中心点
            Point center = new Point(b.x + b.width / 2, b.y + b.height / 2);
            double dist = p.distance(center);
            
            if (dist < minDistance) {
                minDistance = dist;
                closestIndex = i;
            }
        }

        if (closestIndex != -1) {
            Component target = getComponent(closestIndex);
            Rectangle b = target.getBounds();
            
            // コンポーネントの左半分なら「その前」、右半分なら「その後ろ」とする
            // グリッドなのでX座標の相対位置で判断
            if (p.x < b.x + b.width / 2) {
                insertionIndex = closestIndex;
            } else {
                insertionIndex = closestIndex + 1;
            }
        } else {
            // 空の領域などの場合、末尾にする
            insertionIndex = count;
        }
    }

    /**
     * 実際の並べ替え処理（Insert）
     */
    private void performReorder() {
        // 現在のインデックスを取得
        int currentIndex = -1;
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) == draggingComponent) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) return;

        // 削除してから挿入するため、インデックスのズレを補正
        // (自分より後ろに挿入する場合、削除によってインデックスが1つ減るため)
        if (insertionIndex > currentIndex) {
            insertionIndex--;
        }

        // 同じ場所なら何もしない
        if (insertionIndex == currentIndex) return;

        // スワップではなく「挿入」: 
        // SwingのContainer.add(comp, index) は、既存の要素をシフトしてくれる
        remove(draggingComponent);
        add(draggingComponent, insertionIndex);

        revalidate(); // レイアウト計算しなおし
    }
}

/**
 * 半透明のゴーストを描画するGlassPane (前回のコードと共通)
 */
@SuppressWarnings("serial")
class GhostGlassPane extends JPanel {
    private BufferedImage ghostImage;
    private Point location;
    private Point offset = new Point(-15, -15); // カーソルからのズレ

    public GhostGlassPane() {
        setOpaque(false);
    }

    public void startDrag(BufferedImage image, Point screenLocation) {
        this.ghostImage = image;
        moveDrag(screenLocation);
    }

    public void moveDrag(Point screenLocation) {
        Point p = new Point(screenLocation);
        SwingUtilities.convertPointFromScreen(p, this);
        this.location = p;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (ghostImage != null && location != null) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g2.drawImage(ghostImage, location.x + offset.x, location.y + offset.y, null);
        }
    }
}