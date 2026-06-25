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
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.D2.ui;

import java.awt.Window;
import java.util.ArrayList;

import com.vis.core.view.D2.ui.glasses.Praparat;

/**
 * Abstracts the host window that immediate-action tools (WW/WL, invert, flip,
 * sort, …) operate within, so the same tools can serve both the regular 2D
 * viewer and the Comparison View.
 * <p>
 * The regular {@link Viewer2DScreen} resolves the target from the active patient
 * tab's Eyepiece selection; {@link ComparisonScreen} resolves it as the selected
 * series PAIR(s) spanning its study columns. Routing tools through this interface
 * avoids the previous hard dependency on {@code Viewer2DScreen.getInstance()}.
 *
 * @author tatsunidas
 */
public interface ImageViewerContext {

	/** The currently active tool id (see {@code Viewer2DToolBar}). */
	int getCurrentToolType();

	/** The Praparats an immediate action should be applied to (may be empty). */
	ArrayList<Praparat> getActionTargetPraparats();

	/** Owner window for tool dialogs. */
	Window getOwnerWindow();
}
