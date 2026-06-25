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
package com.vis.core.view.D2.ui.glasses;

import java.util.ArrayList;

/**
 * Lets a container coordinate slice-synchronization across MULTIPLE Eyepieces.
 * <p>
 * Normally an {@link Eyepiece} synchronizes scrolling only among the selected
 * Praparats it owns. The Comparison View places one Eyepiece per study, so it
 * supplies a group that spans every column, enabling current/prior studies to
 * scroll together. When no group is set, an Eyepiece falls back to its own
 * selection, so the regular 2D viewer behaviour is unchanged.
 *
 * @author tatsunidas
 */
public interface PraparatSyncGroup {

	/**
	 * @param source the Praparat being scrolled/zoomed.
	 * @return every Praparat that should be synchronized together with {@code
	 *         source} (the Comparison View returns the source's series PAIR, i.e.
	 *         the same-row series across all study columns; includes the source).
	 *         May return null to defer to the Eyepiece's own selection.
	 */
	ArrayList<Praparat> getSyncTargets(Praparat source);
}
