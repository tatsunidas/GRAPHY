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
package com.vis.core.view.D3.endo;

import org.joml.Vector3f;

/**
 * 仮想内視鏡パスの制御点。
 *
 * 座標はレンダラーのローカルキューブ座標系（各軸 index/size - 0.5 ， {@link com.vis.core.view.D3.ui.GLCanvas}
 * の calculateModelMatrix() や volume.frag の texCoord 規約と同じ）で保持する。
 * SphereRoi3D 等のROIが使う絶対患者座標(IPP/IOP)とは異なる規約なので、変換する場合は注意すること。
 *
 * @author tatsunidas
 */
public class EndoPathPoint3D {

	private final Vector3f position;

	public EndoPathPoint3D(Vector3f position) {
		this.position = new Vector3f(position);
	}

	public EndoPathPoint3D(float x, float y, float z) {
		this.position = new Vector3f(x, y, z);
	}

	/** 防御的コピーを返す（内部の{@link Vector3f}参照は外に漏らさない） */
	public Vector3f getPosition() {
		return new Vector3f(position);
	}

	public void setPosition(Vector3f p) {
		this.position.set(p);
	}

	public void setPosition(float x, float y, float z) {
		this.position.set(x, y, z);
	}

	public EndoPathPoint3D copy() {
		return new EndoPathPoint3D(position);
	}
}
