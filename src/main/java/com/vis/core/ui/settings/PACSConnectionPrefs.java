package com.vis.core.ui.settings;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import java.awt.GridBagLayout;
import java.awt.Image;

import javax.swing.JLabel;

import java.awt.GridBagConstraints;
import javax.swing.JButton;

import java.awt.Insets;
import java.awt.Color;
import java.awt.Component;
import javax.swing.Box;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.JScrollPane;

import com.vis.core.facade.WindowManager;
import com.vis.core.ui.dialog.AddDicomCommunicationNodeWin;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.dimse.EchoImpl;

public class PACSConnectionPrefs extends JPanel {

	private static final long serialVersionUID = -6369768191399187866L;
	private JTable table;

	private DefaultTableModel model;
	public String[] dicomNetworkNodeTableHeader = new String[] { "Nickname", "AE title", "Host(IP address)",
			"Port(TCP/IP)", "Ciphers", "Ready" };
	private PACSConnectionPrefs thisPanel = this;

	public PACSConnectionPrefs() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 0, 306, 0 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
		setLayout(gridBagLayout);

		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.gridwidth = 5;
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 1;
		gbc_verticalStrut.gridy = 0;
		add(verticalStrut, gbc_verticalStrut);

		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		add(horizontalStrut, gbc_horizontalStrut);

		JLabel lblNewLabel = new JLabel("DICOM Communication Nodes");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 1;
		gbc_lblNewLabel.gridy = 1;
		add(lblNewLabel, gbc_lblNewLabel);

		JButton addButton = new JButton("Add");
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				/* add new nodes */
				new AddDicomCommunicationNodeWin(thisPanel);
			}
		});
		GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
		gbc_btnNewButton.insets = new Insets(0, 0, 5, 5);
		gbc_btnNewButton.gridx = 2;
		gbc_btnNewButton.gridy = 1;
		add(addButton, gbc_btnNewButton);

		JButton deleteButton = new JButton("Delete");
		deleteButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int row = table.getSelectedRow();
				if (row == -1) {
					return;
				}
				String identical = (String) model.getValueAt(row, model.findColumn("Nickname"));
				DatabaseHandler.getInstance().deleteServer(identical);
				model.removeRow(row);
				constructTableModel(getTable());
				/* 表示していたドックが削除された場合、タブから削除 */
				TreeTableDockManager dttm = WindowManager.getMainScreen().getTreeTableDockManager();
				for (String nickname : dttm.getAllNicknamesFromDocks()) {
					if (nickname.equals(identical)) {
						dttm.removeDockAt(nickname);
						break;
					}
				}
				WindowManager.getMainScreen().updateQRTreeTables();
			}
		});
		GridBagConstraints gbc_btnNewButton_1 = new GridBagConstraints();
		gbc_btnNewButton_1.insets = new Insets(0, 0, 5, 5);
		gbc_btnNewButton_1.gridx = 3;
		gbc_btnNewButton_1.gridy = 1;
		add(deleteButton, gbc_btnNewButton_1);

		JScrollPane scrollPane = new JScrollPane();
		GridBagConstraints gbc_scrollPane = new GridBagConstraints();
		gbc_scrollPane.gridheight = 2;
		gbc_scrollPane.gridwidth = 5;
		gbc_scrollPane.insets = new Insets(0, 0, 5, 5);
		gbc_scrollPane.fill = GridBagConstraints.BOTH;
		gbc_scrollPane.gridx = 1;
		gbc_scrollPane.gridy = 2;
		add(scrollPane, gbc_scrollPane);

		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 0, 5);
		gbc_horizontalStrut_1.gridx = 0;
		gbc_horizontalStrut_1.gridy = 3;
		add(horizontalStrut_1, gbc_horizontalStrut_1);

		table = new JTable();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		constructTableModel(table);
		scrollPane.setViewportView(table);
		table.setRowHeight(24);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.gridx = 6;
		gbc_horizontalStrut_2.gridy = 3;
		add(horizontalStrut_2, gbc_horizontalStrut_2);

	}

	public void constructTableModel(JTable table) {
		// get server lists
		DatabaseHandler db = DatabaseHandler.getInstance();
		ArrayList<DicomCommunicationNode> tableData = db.loadServerList();
		Object[][] tblObj = new Object[tableData.size()][];
		for (int i = 0; i < tableData.size(); i++) {
			DicomCommunicationNode nodeInfo = tableData.get(i);
			Object[] row = new Object[] { 
					nodeInfo.getNickname(),
					nodeInfo.getAETitle(), // aet
					nodeInfo.getHostName(), // host
					String.valueOf(nodeInfo.getPort()), // port
					nodeInfo.cipherListToString(), // ciphers
					new EchoImpl(null).echo(
							db.defaultAET,
							db.defaultHost, 
							null, 
							nodeInfo.getAETitle(), 
							nodeInfo.getHostName(), 
							String.valueOf(nodeInfo.getPort())
							)// connection established
			};
			tblObj[i] = row;
		}

		model = new DefaultTableModel(tblObj, dicomNetworkNodeTableHeader);
		/* ChangeListener */
		model.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent tme) {
				int row = tme.getFirstRow();// current edited row
				int col = tme.getColumn();
				// "AE title","Host(IP address)","Port(TCP/IP)","Ciphers","Ready"
				String colType = model.getColumnName(col);
				if (colType.equals("Ready")) {
					return;
				}
				int name_col = model.findColumn("Nickname");
				if (colType.equals("Nickname")) {
					/* 編集されたserverName */
					String newNickname = (String) model.getValueAt(row, col);
					/* 既存のリストの中に無いかどうか */
					boolean inlist = db.isAlreadyRegisteredServer(newNickname);
					if (!inlist) {
						/* 変更されたサーバーを特定して、アップデートする */
						/* テーブルのニックネームと、DBのニックネームの差分を取り、DBのニックの内、残ったものが変更されたサーバー */
						String prevName = "";
						ArrayList<String> nameInDB = new ArrayList<String>();
						ArrayList<HashMap<String,Object>>  serverMaterials= db.getCommunicationServerList();
						for(HashMap<String,Object> nodeMaterials:serverMaterials) {
							String name = (String)nodeMaterials.get("logicalname");
							nameInDB.add(name);
						}
						/* テーブル上のニックネームをDB内のニックネームと差分 */
						for (int r = 0; r < model.getRowCount(); r++) {
							String name = (String) model.getValueAt(r, col);
							if (nameInDB.contains(name)) {
								nameInDB.remove(name);
							}
						}
						/* 残ったものが変更されたサーバー */
						prevName = nameInDB.get(0);
						/* update */
						if (!prevName.isEmpty()) {
							HashMap<String, Object> prevNodeMaterials = db.getServerInfo(prevName);
							DicomCommunicationNode prevToUpdate = new DicomCommunicationNode(prevNodeMaterials);
							prevToUpdate.setNickname(newNickname);
							db.updateServer(prevToUpdate.getNodeMaterials(), prevName);
						}
					}else {
						return;
					}
				} else if (colType.equals("AE title")) {
					String serverName = (String) model.getValueAt(row, name_col);
					String aet = (String) model.getValueAt(row, col);
					if (aet == null ) {
						aet = "";
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setAETitle(aet);
					db.updateServer(node.getNodeMaterials(), serverName);
				} else if (colType.equals("Host(IP address)")) {
					String serverName = (String) model.getValueAt(row, name_col);
					String host_new = (String) model.getValueAt(row, col);
					if (host_new.equals("") || host_new == null) {
						host_new = "localhost";// default value
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setHostName(host_new);
					db.updateServer(node.getNodeMaterials(), serverName);
				} else if (colType.equals("Port(TCP/IP)")) {
					String serverName = (String) model.getValueAt(row, name_col);
					Integer port_new = Integer.valueOf((String) model.getValueAt(row, col));
					if (port_new == null) {
						port_new = 11112;// default value
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setPort(port_new);
					db.updateServer(node.getNodeMaterials(), serverName);
				} else if (colType.equals("Ciphers")) {
					String serverName = (String) model.getValueAt(row, name_col);
					String cip_new = String.valueOf((String) model.getValueAt(row, col));
					if (cip_new.equals("") || cip_new == null) {
						cip_new = "";// default value
					}
					HashMap<String, Object> nodeMaterials = db.getServerInfo(serverName);
					DicomCommunicationNode node = new DicomCommunicationNode(nodeMaterials);
					node.setCipherFromStringSequence(cip_new);
					db.updateServer(node.getNodeMaterials(), serverName);
				}
				constructTableModel(table);
			}
		});
		/* continue constructions */
		table.setModel(model);
		TableColumnModel tcm = table.getColumnModel();
		TableColumn tc = tcm.getColumn(model.findColumn("Ready"));
		tc.setCellRenderer(new EchoButtonRenderer());
		tc.setCellEditor(new EchoButtonEditor());
		revalidate();
		repaint();
	}

	public JTable getTable() {
		return table;
	}

	class EchoButtonRenderer extends JButton implements TableCellRenderer {

		/**
		 * 
		 */
		private static final long serialVersionUID = 8906997032244269301L;
		String successIconPath = "/icon/ic_location_on_black_24dp.png";
		String failedIconPath = "/icon/ic_location_off_black_24dp.png";

		public EchoButtonRenderer() {
			super();
		}

		public void setIcon(boolean eho_success) {

			if (eho_success) {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(successIconPath));
				ImageIcon icon = new ImageIcon(img);
//				JLabel iconLabel = new JLabel();
//				iconLabel.setOpaque(false);
//				iconLabel.setIcon(icon);
				setIcon(icon);
//				setForeground(new Color(152, 251, 152));
				setBackground(new Color(152, 251, 152));
			} else {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(failedIconPath));
				ImageIcon icon = new ImageIcon(img);
//				JLabel iconLabel = new JLabel();
//				iconLabel.setOpaque(false);
//				iconLabel.setIcon(icon);
				setIcon(icon);
			}
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int col) {
			if (isSelected) {
				setForeground(table.getSelectionForeground());
				super.setBackground(table.getSelectionBackground());
			} else {
				setForeground(table.getForeground());
				setBackground(table.getBackground());
			}
			if(value != null) {
				boolean echo_success = ((Boolean) value).booleanValue();
				this.setIcon(echo_success);
			}
			return this;
		}
	}

	class EchoButtonEditor extends JButton implements TableCellEditor {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected EventListenerList listenerList = new EventListenerList();
		protected ChangeEvent changeEvent = new ChangeEvent(this);

		String successIconPath = "/icon/ic_location_on_black_24dp.png";
		String failedIconPath = "/icon/ic_location_off_black_24dp.png";

		boolean state;

		public EchoButtonEditor() {
			super();
			addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					constructTableModel(table);
					WindowManager.getMainScreen().updateQRTreeTables();
				}
			});
		}

		public void setIcon(boolean eho_success) {
			if (eho_success) {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(successIconPath));
				ImageIcon icon = new ImageIcon(img);
				setIcon(icon);
				setBackground(new Color(152, 251, 152));
			} else {
				Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(failedIconPath));
				ImageIcon icon = new ImageIcon(img);
				setIcon(icon);
			}
		}

		@Override
		public void addCellEditorListener(CellEditorListener listener) {
			listenerList.add(CellEditorListener.class, listener);
		}

		@Override
		public void cancelCellEditing() {
			fireEditingCanceled();
		}

		protected void fireEditingStopped() {
			CellEditorListener listener;
			Object[] listeners = listenerList.getListenerList();
			for (int i = 0; i < listeners.length; i++) {
				if (listeners[i] == CellEditorListener.class) {
					listener = (CellEditorListener) listeners[i + 1];
					listener.editingStopped(changeEvent);
				}
			}
		}

		private void fireEditingCanceled() {
			CellEditorListener listener;
			Object[] listeners = listenerList.getListenerList();
			for (int i = 0; i < listeners.length; i++) {
				if (listeners[i] == CellEditorListener.class) {
					listener = (CellEditorListener) listeners[i + 1];
					listener.editingCanceled(changeEvent);
				}
			}
		}

		@Override
		public Object getCellEditorValue() {
			return state;
		}

		@Override
		public boolean isCellEditable(EventObject arg0) {
			return true;// keep always true for can push button
		}

		@Override
		public void removeCellEditorListener(CellEditorListener listener) {
			listenerList.remove(CellEditorListener.class, listener);
		}

		@Override
		public boolean shouldSelectCell(EventObject arg0) {
			return true;
		}

		@Override
		public boolean stopCellEditing() {
			fireEditingStopped();
			return true;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int col) {
			state = ((Boolean) value).booleanValue();
			setIcon(state);
			return this;
		}
	}

}
