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

import com.vis.core.view.D3.ui.UndoManager;

/**
 * {@link EndoPath3D}および{@link EndoCamera}に対するUndo/Redoコマンド群。
 *
 * {@code com.vis.core.view.D3.ui.VolumeEditor.CutCommand}と同じ規約に従う:
 * コンストラクタで「変更前」の値を防御的にバックアップし、{@link UndoManager.Command#execute()}は
 * 「変更後」の値を適用、{@link UndoManager.Command#undo()}はバックアップした値を再適用する。
 * コマンドはインスタンス化しただけでは何も変更しない
 * （{@link UndoManager#addCommand(UndoManager.Command)}が呼ばれた時点で初めてexecute()が走る）。
 *
 * 連続的な変更（パス点のライブドラッグ中の座標更新、再生によるuの毎フレーム更新など）は、
 * ジェスチャー完了時に1コマンドとしてpushすること。毎フレーム/毎イベントでpushしてはならない
 * ({@code GLCanvas}のカット機能がmouseDragged中はoutlineのみ更新し、mouseReleasedで一度だけ
 * addCommand()するのと同じ規約。今回はコマンド自体のみを提供し、デバウンスは将来の呼び出し側の責務とする)。
 *
 * @author tatsunidas
 */
public final class EndoCommands {

	private EndoCommands() {
		// インスタンス化不要のコンテナ
	}

	/** 制御点の挿入（末尾追加も index=path.size() を渡せばよい） */
	public static final class InsertPointCommand implements UndoManager.Command {
		private final EndoPath3D path;
		private final int index;
		private final Vector3f position;

		public InsertPointCommand(EndoPath3D path, int index, Vector3f position) {
			this.path = path;
			this.index = index;
			this.position = new Vector3f(position);
		}

		@Override
		public void execute() {
			path.insertPoint(index, position);
		}

		@Override
		public void undo() {
			path.removePoint(index);
		}
	}

	/** 既存制御点の移動 */
	public static final class MovePointCommand implements UndoManager.Command {
		private final EndoPath3D path;
		private final int index;
		private final Vector3f oldPosition;
		private final Vector3f newPosition;

		public MovePointCommand(EndoPath3D path, int index, Vector3f newPosition) {
			this.path = path;
			this.index = index;
			this.oldPosition = path.getPoint(index).getPosition();
			this.newPosition = new Vector3f(newPosition);
		}

		@Override
		public void execute() {
			path.setPointPosition(index, newPosition);
		}

		@Override
		public void undo() {
			path.setPointPosition(index, oldPosition);
		}
	}

	/** 既存制御点の削除。undoで同じindex・同じ位置に復元する */
	public static final class RemovePointCommand implements UndoManager.Command {
		private final EndoPath3D path;
		private final int index;
		private final Vector3f removedPosition;

		public RemovePointCommand(EndoPath3D path, int index) {
			this.path = path;
			this.index = index;
			this.removedPosition = path.getPoint(index).getPosition();
		}

		@Override
		public void execute() {
			path.removePoint(index);
		}

		@Override
		public void undo() {
			path.insertPoint(index, removedPosition);
		}
	}

	/** {@link EndoCamera}の正規化距離u([0,1])の変更 */
	public static final class SetCameraUCommand implements UndoManager.Command {
		private final EndoCamera camera;
		private final float oldU;
		private final float newU;

		public SetCameraUCommand(EndoCamera camera, float newU) {
			this.camera = camera;
			this.oldU = camera.getU();
			this.newU = newU;
		}

		@Override
		public void execute() {
			camera.setU(newU);
		}

		@Override
		public void undo() {
			camera.setU(oldU);
		}
	}
}
