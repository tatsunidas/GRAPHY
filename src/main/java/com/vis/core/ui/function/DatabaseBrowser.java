package com.vis.core.ui.function;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;

import org.apache.derby.jdbc.EmbeddedDataSource;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.util.PropertiesUtil;
import com.vis.db.DatabaseHandler;

public class DatabaseBrowser extends JDialog implements WindowListener {

	private static final long serialVersionUID = 1453185558822644934L;
	protected Connection conn;
	protected JComboBox<String> catalogBox;
	protected JComboBox<String> schemaBox;
	protected JComboBox<String> tableBox;
	protected JTable table;
	private DatabaseHandler db = DatabaseHandler.getInstance();

	String username = db.getUserName();
	String password = db.getPassword();
	String driver = db.getDriverName();
	String protocol = db.getProtocolName();
	String databasename = db.getDatabaseName();
	
	String[] tableType = new String[] {"IMAGE","SERIES","STUDY","PATIENT","SERVERS","ROI"};
	// URL
	// protocol + databasename + ";create=false"

	public DatabaseBrowser() throws Exception {
		super(WindowManager.getMainScreen(), true);
//	    ConnectionDialog cd = new ConnectionDialog(this);
//	    conn = cd.getConnection();
		if (!db.checkDBExists(PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.LocalDBLocation))) {
			dispose();
			return;
		}
		openConnection(protocol + databasename + ";create=false", username, password);
		buildFrameLayout();
		setSize(600, 450);
		pack();
		setVisible(true);
		addWindowListener(this);// dissconnect when window closing
	}

	private void openConnection(String url, String username, String password) {
		boolean dbExists = db.checkDBExists(PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.LocalDBLocation));
//		System.setProperty("derby.system.home", ApplicationContext.getLocalDBLocation());
		try {
			Class.forName(driver);
		} catch (ClassNotFoundException e) {
			StringWriter str = new StringWriter();
			e.printStackTrace(new PrintWriter(str));
		}
		try {
			EmbeddedDataSource ds = db.getEmbeddedDataSource();
			ds = new org.apache.derby.jdbc.EmbeddedDataSource();
			ds.setDatabaseName(databasename);
		} catch (NoClassDefFoundError e) {
			Log.logger.severe(e.getMessage());
			try {
				ApplicationFacade
						.exitApp(Level.SEVERE,"ERROR: ClassNotFoundException:" + e.getMessage() + ": GRAPHY stop and close...");
			} catch (Throwable e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		try {
			if (!dbExists) {
				JOptionPane.showConfirmDialog(this, "GRAPHYDB Dir is not exist.");
				return;
			} else {
				conn = DriverManager.getConnection(protocol + databasename + ";create=false", username, password);
			}
		} catch (Exception e) {
			if (dbExists && conn == null) {
				JOptionPane.showConfirmDialog(this, "An instance of GRAPHY is running: Exit the browser");
				this.setVisible(false);
				this.dispose();
//				return;
			}
		}
	}

//	private boolean checkDBexists(String tem) {
//		File[] listFiles = new File(tem).listFiles();
//		for (int l = 0; l < listFiles.length; l++) {
//			if (listFiles[l].getName().equalsIgnoreCase(databasename)) {
//				return true;
//			}
//		}
//		return false;
//	}

	protected void buildFrameLayout() {
		Container pane = getContentPane();
		pane.add(getSelectionPanel(), BorderLayout.NORTH);
		table = new JTable();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		refreshTable();
		pane.add(new JScrollPane(table), BorderLayout.CENTER);
		pane.add(getFrameButtonPanel(), BorderLayout.SOUTH);
	}

	protected JPanel getSelectionPanel() {
		JLabel label;
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.insets = new Insets(5, 10, 5, 10);
		label = new JLabel("Catalog", JLabel.RIGHT);
		panel.add(label, gbc);
		label = new JLabel("Schema", JLabel.RIGHT);
		panel.add(label, gbc);
		label = new JLabel("Table", JLabel.RIGHT);
		panel.add(label, gbc);

		gbc.gridy = 1;
		catalogBox = new JComboBox<>();
		populateCatalogBox();
		panel.add(catalogBox, gbc);
		schemaBox = new JComboBox<>();
		populateSchemaBox();
		panel.add(schemaBox, gbc);
		tableBox = new JComboBox<>();
		populateTableBox();
		panel.add(tableBox, gbc);

		catalogBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent event) {
				String newCatalog = (String) (catalogBox.getSelectedItem());
				try {
					conn.setCatalog(newCatalog);
				} catch (Exception e) {
				}
				;
				populateSchemaBox();
				populateTableBox();
				refreshTable();
			}
		});

		schemaBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent event) {
				populateTableBox();
				refreshTable();
			}
		});

		tableBox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent event) {
				refreshTable();
			}
		});
		return panel;
	}

			
	protected void populateCatalogBox() {
		try {
			DatabaseMetaData dmd = conn.getMetaData();
			ResultSet rset = dmd.getCatalogs();
			Vector<String> values = new Vector<>();
			while (rset.next()) {
				values.addElement(rset.getString(1));
			}
			rset.close();
			catalogBox.setModel(new DefaultComboBoxModel<String>(values));
			catalogBox.setSelectedItem(conn.getCatalog());
			catalogBox.setEnabled(values.size() > 0);
		} catch (Exception e) {
			catalogBox.setEnabled(false);
		}
	}

	protected void populateSchemaBox() {
		try {
			DatabaseMetaData dmd = conn.getMetaData();
			ResultSet rset = dmd.getSchemas();
			Vector<String> values = new Vector<>();
			while (rset.next()) {
				values.addElement(rset.getString(1));
			}
			rset.close();
			schemaBox.setModel(new DefaultComboBoxModel<String>(values));
			schemaBox.setEnabled(values.size() > 0);
			schemaBox.setSelectedItem("GRAPHY");
		} catch (Exception e) {
			schemaBox.setEnabled(false);
		}
	}

	protected void populateTableBox() {
		List<String> allowList = Arrays.asList(tableType);
		try {
			String[] types = { "TABLE" };
			String catalog = conn.getCatalog();
			String schema = (String) (schemaBox.getSelectedItem());
			DatabaseMetaData dmd = conn.getMetaData();
			ResultSet rset = dmd.getTables(catalog, schema, null, types);
			Vector<String> values = new Vector<>();
			while (rset.next()) {
				String val = rset.getString(3);
				if(allowList.contains(val)) {
					values.addElement(rset.getString(3));
				}
			}
			rset.close();
			tableBox.setModel(new DefaultComboBoxModel<String>(values));
			tableBox.setEnabled(values.size() > 0);
		} catch (Exception e) {
			tableBox.setEnabled(false);
		}
	}

	protected JPanel getFrameButtonPanel() {
		JPanel panel = new JPanel();
		JButton button = new JButton("Exit");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				setVisible(false);
				dispose();
			}
		});
		panel.add(button);
		return panel;
	}

	protected void refreshTable() {
//	    String catalog = (catalogBox.isEnabled() ?
//	        catalogBox.getSelectedItem().toString() :
//	        null);
		String schema = (schemaBox.isEnabled() ? schemaBox.getSelectedItem().toString() : null);
		String tableName = (String) tableBox.getSelectedItem();
		if (tableName == null) {
			table.setModel(new DefaultTableModel());
			return;
		}
		String selectTable = (schema == null ? "" : schema + ".") + tableName;
		if (selectTable.indexOf(' ') > 0) {
			selectTable = "\"" + selectTable + "\"";
		}
		try {
			Statement stmt = conn.createStatement();
			ResultSet rset = stmt.executeQuery("SELECT * FROM " + selectTable);
			table.setModel(new ResultSetTableModel(rset));
		} catch (Exception e) {
		}
		;
	}

	class ConnectionDialog extends JDialog {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected JTextField useridField;
		protected JTextField passwordField;
		protected JTextField urlField;

		protected boolean canceled;
		protected Connection connect;

		public ConnectionDialog(JFrame f) {
			super(f, "Connect To Database", true);
			buildDialogLayout();
			setSize(300, 200);
		}

		public Connection getConnection() {
			setVisible(true);
			return connect;
		}

		protected void buildDialogLayout() {
			JLabel label;

			Container pane = getContentPane();
			pane.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(5, 10, 5, 10);

			gbc.gridx = 0;
			gbc.gridy = 0;
			label = new JLabel("Userid:", JLabel.LEFT);
			pane.add(label, gbc);

			gbc.gridy++;
			label = new JLabel("Password:", JLabel.LEFT);
			pane.add(label, gbc);

			gbc.gridy++;
			label = new JLabel("URL:", JLabel.LEFT);
			pane.add(label, gbc);

			gbc.gridx = 1;
			gbc.gridy = 0;

			useridField = new JTextField(10);
			pane.add(useridField, gbc);

			gbc.gridy++;
			passwordField = new JTextField(10);
			pane.add(passwordField, gbc);

			gbc.gridy++;
			urlField = new JTextField(15);
			pane.add(urlField, gbc);

			gbc.gridx = 0;
			gbc.gridy = 3;
			gbc.gridwidth = GridBagConstraints.REMAINDER;
			gbc.anchor = GridBagConstraints.CENTER;
			pane.add(getButtonPanel(), gbc);
		}

		protected JPanel getButtonPanel() {
			JPanel panel = new JPanel();
			JButton btn = new JButton("Ok");
			btn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event) {
					onDialogOk();
				}
			});
			panel.add(btn);
			btn = new JButton("Cancel");
			btn.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event) {
					onDialogCancel();
				}
			});
			panel.add(btn);
			return panel;
		}

		protected void onDialogOk() {
			if (attemptConnection()) {
				setVisible(false);
			}
		}

		protected void onDialogCancel() {
			System.exit(0);
		}

		protected boolean attemptConnection() {
			try {
				connect = DriverManager.getConnection(urlField.getText(), useridField.getText(),
						passwordField.getText());
				return true;
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Error connecting to " + "database: " + e.getMessage());
			}
			return false;
		}

	}

	class ResultSetTableModel extends AbstractTableModel {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		protected Vector<String> columnHeaders;
		protected Vector<Object> tableData;

		public ResultSetTableModel(ResultSet rset) throws SQLException {
			Vector<Object> rowData;
			ResultSetMetaData rsmd = rset.getMetaData();
			int count = rsmd.getColumnCount();
			columnHeaders = new Vector<>(count);
			tableData = new Vector<>();
			for (int i = 1; i <= count; i++) {
				columnHeaders.addElement(rsmd.getColumnName(i));
			}
			while (rset.next()) {
				rowData = new Vector<>(count);
				for (int i = 1; i <= count; i++) {
					rowData.addElement(rset.getObject(i));
				}
				tableData.addElement(rowData);
			}
		}

		public int getColumnCount() {
			return columnHeaders.size();
		}

		public int getRowCount() {
			return tableData.size();
		}

		@SuppressWarnings("unchecked")
		public Object getValueAt(int row, int column) {
			Vector<Object> rowData = (Vector<Object>) (tableData.elementAt(row));
			return rowData.elementAt(column);
		}

		public boolean isCellEditable(int row, int column) {
			return false;
		}

		public String getColumnName(int column) {
			return (String) (columnHeaders.elementAt(column));
		}

	}

	@Override
	public void windowActivated(WindowEvent arg0) {
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		System.out.println("unconnect");
		try {
			if (!conn.isClosed()) {
				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
	}// DO NOT USE for disconnection

	@Override
	public void windowDeactivated(WindowEvent arg0) {
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
	}
}
