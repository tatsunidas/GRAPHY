 /*
  *  Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.ui.main.dcmtreetable;

import javax.swing.tree.TreeModel;

/**
 * TreeTableModel is the model used by a JTreeTable. It extends TreeModel
 * to add methods for getting information about the set of columns each
 * node in the TreeTableModel may have. Each column, like a column in
 * a TableModel, has a name and a type associated with it. Each node in
 * the TreeTableModel can return a value for each of the columns and
 * set that value if isCellEditable() returns true.
 *
 * @author Philip Milne
 * @author Scott Violet
 */
public interface TreeTableModel extends TreeModel
{
    /**
     * Returns the number ofs availible column.
     */
    public int getColumnCount();

    /**
     * Returns the name for column number <code>column</code>.
     */
    public String getColumnName(int column);

    /**
     * Returns the type for column number <code>column</code>.
     */
    public Class<?> getColumnClass(int column);

    /**
     * Returns the value to be displayed for node <code>node</code>,
     * at column number <code>column</code>.
     */
    public Object getValueAt(Object node, int column);

    /**
     * Indicates whether the the value for node <code>node</code>,
     * at column number <code>column</code> is editable.
     */
    public boolean isCellEditable(Object node, int column);

    /**
     * Sets the value for node <code>node</code>,
     * at column number <code>column</code>.
     */
    public void setValueAt(Object aValue, Object node, int column);
    
    public void reload(Object node);
}
