package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.table.TableCellEditor;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class DICOMTreeTableCellEditor extends AbstractCellEditor implements TableCellEditor {

	private JTree tree;
	private JTable table;

	public DICOMTreeTableCellEditor(JTree tree, JTable table) {
		this.tree = tree;
		this.table = table;
	}

	@Override
	public Object getCellEditorValue() {
		return null;
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int r, int c) {
		// TODO Auto-generated method stub
		return tree;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean isCellEditable(EventObject e) {
		// expand tree
		if (e instanceof MouseEvent) {
			int column1 = 0;
			MouseEvent me = (MouseEvent) e;
			int doubleClick = 2;
			MouseEvent newME = new MouseEvent(tree, me.getID(), me.getWhen(), me.getModifiers(),
					me.getX() - table.getCellRect(0, column1, true).x, me.getY(), doubleClick, me.isPopupTrigger());
			tree.dispatchEvent(newME);
		}
		return false;
	}

	public JTree getTree() {
		return tree;
	}

//	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int r, int c) {
//	Component component = super.getTableCellEditorComponent(table, value, isSelected, r, c);
//	JTree t = getTree();
//	boolean rv = t.isRootVisible();
//	int offsetRow = rv ? r : r - 1;
//	Rectangle bounds = t.getRowBounds(offsetRow);
//	int offset = bounds.x;
//	TreeCellRenderer tcr = t.getCellRenderer();
//	if (tcr instanceof DefaultTreeCellRenderer) {
//		Object node = t.getPathForRow(offsetRow).getLastPathComponent();
//		Icon icon;
//		if (t.getModel().isLeaf(node))
//			icon = ((DefaultTreeCellRenderer) tcr).getLeafIcon();
//		else if (tree.isExpanded(offsetRow))
//			icon = ((DefaultTreeCellRenderer) tcr).getOpenIcon();
//		else
//			icon = ((DefaultTreeCellRenderer) tcr).getClosedIcon();
//		if (icon != null) {
//			offset += ((DefaultTreeCellRenderer) tcr).getIconTextGap() + icon.getIconWidth();
//		}
//	}
//	((TreeTableTextField) getComponent()).offset = offset;
//	return component;
//}
//
///**
// * This is overriden to forward the event to the tree. This will return true if
// * the click count >= 3, or the event is null.
// */
//public boolean isCellEditable(EventObject e) {
//	if (e instanceof MouseEvent) {
//		MouseEvent me = (MouseEvent) e;
//		// If the modifiers are not 0 (or the left mouse button),
//		// tree may try and toggle the selection, and table
//		// will then try and toggle, resulting in the
//		// selection remaining the same. To avoid this, we
//		// only dispatch when the modifiers are 0 (or the left mouse
//		// button).
//		if (me.getModifiers() == 0 || me.getModifiers() == InputEvent.BUTTON1_MASK) {
//			for (int counter = getColumnCount() - 1; counter >= 0; counter--) {
//				if (getColumnClass(counter) == TreeTableModel.class) {
//					MouseEvent newME = new MouseEvent(DICOMTreeTable.this.tree, me.getID(), me.getWhen(),
//							me.getModifiers(), me.getX() - getCellRect(0, counter, true).x, me.getY(),
//							me.getClickCount(), me.isPopupTrigger());
//					DICOMTreeTable.this.tree.dispatchEvent(newME);
//					break;
//				}
//			}
//		}
//		if (me.getClickCount() >= 3) {
//			return treeEditable;
//		}
//		return false;
//	}
//	if (e == null) {
//		return treeEditable;
//	}
//	return false;
//}
//}

	/**
	 * Component used by TreeTableCellEditor. The only thing this does is to
	 * override the <code>reshape</code> method, and to ALWAYS make the x location
	 * be <code>offset</code>.
	 */
	static class TreeTableTextField extends JTextField {
		public int offset;

		public void setBounds(int x, int y, int w, int h) {
			int newX = Math.max(x, offset);
			super.setBounds(newX, y, w - (newX - x), h);
		}
	}
}
