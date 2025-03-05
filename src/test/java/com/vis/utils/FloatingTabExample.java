/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
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
package com.vis.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

public class FloatingTabExample {
    private JTabbedPane tabbedPane;
    private Map<Component, Boolean> floatingStatusMap; // タブのフローティング状態を管理

    public FloatingTabExample() {
        JFrame frame = new JFrame("Floating Tab Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        tabbedPane = new JTabbedPane();
        floatingStatusMap = new HashMap<>();

        // タブを追加
        addTab("Tab 1");
        addTab("Tab 2");
        addTab("Tab 3");

        frame.add(tabbedPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void addTab(String title) {
        JPanel panel = new JPanel(new BorderLayout());

        // フローティング可能な JToolBar を作成
        JToolBar toolBar = new JToolBar(title, JToolBar.HORIZONTAL);
        toolBar.setFloatable(true);
        toolBar.add(new JButton("Button"));

        // フローティングウィンドウを監視
        trackFloatingWindow(toolBar, panel);

        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(new JLabel("Content of " + title), BorderLayout.CENTER);

        tabbedPane.addTab(title, panel);
        floatingStatusMap.put(panel, false); // 初期状態は非フローティング
    }

    private void trackFloatingWindow(JToolBar toolBar, Component tab) {
        SwingUtilities.invokeLater(() -> {
            Window floatingWindow = SwingUtilities.getWindowAncestor(toolBar);
            if (floatingWindow instanceof JFrame) {
                floatingWindow.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                        updateFloatingStatus(tab, true);
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        updateFloatingStatus(tab, false);
                    }
                });
            }
        });
    }

    private void updateFloatingStatus(Component tab, boolean isFloating) {
        floatingStatusMap.put(tab, isFloating);
        int index = tabbedPane.indexOfComponent(tab);
        if (index != -1) {
            System.out.println("Tab " + tabbedPane.getTitleAt(index) + " Floating: " + isFloating);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FloatingTabExample::new);
    }
}




