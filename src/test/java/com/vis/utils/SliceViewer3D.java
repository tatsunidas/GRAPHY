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

import org.scijava.java3d.Appearance;
import org.scijava.java3d.BoundingSphere;
import org.scijava.java3d.BranchGroup;
import org.scijava.java3d.Canvas3D;
import org.scijava.java3d.ColoringAttributes;
import org.scijava.java3d.Transform3D;
import org.scijava.java3d.TransformGroup;
import org.scijava.java3d.utils.behaviors.vp.OrbitBehavior;
import org.scijava.java3d.utils.universe.SimpleUniverse;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Vector3f;

import com.vis.core.util.Platform;

import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class SliceViewer3D extends JFrame {
    private SimpleUniverse universe;
    private TransformGroup volumeTG;
    private BranchGroup scene;

    public SliceViewer3D() {
        setTitle("3D Slice Viewer");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 3D Canvasの作成
        Canvas3D canvas3D = new Canvas3D(SimpleUniverse.getPreferredConfiguration());
        add("Center", canvas3D);

        // Universeとシーンのセットアップ
        universe = new SimpleUniverse(canvas3D);
        universe.getViewingPlatform().setNominalViewingTransform();

        // 軌道制御（マウスで視点操作）
        OrbitBehavior orbit = new OrbitBehavior(canvas3D);
        orbit.setSchedulingBounds(new BoundingSphere());
        universe.getViewingPlatform().setViewPlatformBehavior(orbit);

        // 直方体のボリュームデータを作成
        scene = createSceneGraph();
        universe.addBranchGraph(scene);

        // スライダの設定 (横断面スライス)
        JScrollBar xSlider = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, 0, 100);
        xSlider.addAdjustmentListener(new SliceAdjustmentListener());
        add("South", xSlider);
    }

    // 3Dシーンの作成
    private BranchGroup createSceneGraph() {
        BranchGroup objRoot = new BranchGroup();

        // 直方体（ボリュームデータ）の作成
        volumeTG = new TransformGroup();
        volumeTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        // 直方体のスライス面 (Boxの各辺がスライスされると仮定)
        org.scijava.java3d.utils.geometry.Box volume = new org.scijava.java3d.utils.geometry.Box(0.5f, 0.5f, 0.5f, createAppearance());
        volumeTG.addChild(volume);

        objRoot.addChild(volumeTG);
        return objRoot;
    }

    // 直方体の外観設定
    private Appearance createAppearance() {
        Appearance appearance = new Appearance();
        Color3f color = new Color3f(0.6f, 0.6f, 0.9f);
        ColoringAttributes ca = new ColoringAttributes(color, ColoringAttributes.NICEST);
        appearance.setColoringAttributes(ca);
        return appearance;
    }

    // スライス面の調整
    private class SliceAdjustmentListener implements AdjustmentListener {
        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
            int value = e.getValue();
            float zSlice = (value - 50) / 100.0f;

            // スライス位置に応じて直方体を移動させる
            Transform3D transform = new Transform3D();
            transform.setTranslation(new Vector3f(0.0f, 0.0f, zSlice));
            volumeTG.setTransform(transform);
        }
    }

    public static void main(String[] args) {
    	if (Platform.getOS() == Platform.LINUX) {
			System.setProperty("jogl.disable.openglcore", System.getProperty("jogl.disable.openglcore", "false"));
		}
        SliceViewer3D viewer = new SliceViewer3D();
        viewer.setVisible(true);
    }
}
