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
package com.vis.core.view.D2.ui.glasses;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author tatsunidas
 */
public class SlideGlassKeyListener implements KeyListener {
    final SlideGlass sg;
    final Praparat pp;
    
    // キーの状態を外部（マウスリスナー）からも参照可能にする
    private static final Set<Integer> pressedKeys = new HashSet<>();

    public static boolean isKeyPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public SlideGlassKeyListener(SlideGlass sg) {
        this.sg = sg;
        this.pp = sg.getPraparat();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        pressedKeys.add(k);

        // 1. 特殊操作 (Undo/Redo, Reset, Delete)
        if (handleSpecialKeys(e)) return;

        // 2. ROI移動操作
        CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
        if (cg.keyPressed(k, e.getModifiersEx())) {
            e.consume();
            return;
        }

        // 3. 多次元ページング操作 (矢印キー/上・下キー)
        handlePaging(k);
    }

    private boolean handleSpecialKeys(KeyEvent e) {
        boolean isCtrlOrCmd = (e.getModifiersEx() & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.META_DOWN_MASK)) != 0;
        int k = e.getKeyCode();

        if (isCtrlOrCmd && k == KeyEvent.VK_Z) {
            if (e.isShiftDown()) sg.redo(); else sg.undo();
            return true;
        }
        if (isCtrlOrCmd && e.isShiftDown() && k == KeyEvent.VK_R) {
            pp.resetView();
            return true;
        }
        if (k == KeyEvent.VK_DELETE || k == KeyEvent.VK_BACK_SPACE) {
            ((CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER)).deleteRoi(sg.mouseX, sg.mouseY);
            return true;
        }
        return false;
    }

    private void handlePaging(int k) {
        int step = 0;
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_UP) step = -1;
        else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_DOWN) step = 1;

        if (step != 0 && !pp.isShowGridViewOn()) {
            String targetDim = "Slice"; 
            if (isKeyPressed(KeyEvent.VK_C)) targetDim = "Channel";
            else if (isKeyPressed(KeyEvent.VK_T)) targetDim = "Time";

            ArrayList<Praparat> syncingPraps = (pp.getEyepiece() != null) ? pp.getEyepiece().getSelectingPraparats() : null;
            
            // ★ 修正：マウスリスナーと同様の判定ロジックに戻す
            if (syncingPraps != null && syncingPraps.size() > 1) {
                for (Praparat prap : syncingPraps) {
                    prap.stepDimension(targetDim, step);
                }
            } else {
                pp.stepDimension(targetDim, step);
            }
        }
    }

    @Override public void keyReleased(KeyEvent e) { pressedKeys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}
}
