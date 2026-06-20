package com.vis.core.view.D3.endo;

import static org.junit.Assert.assertEquals;

import org.joml.Vector3f;
import org.junit.Test;

import com.vis.core.view.D3.ui.UndoManager;

public class EndoCommandsTest {

	private static final float EPS = 1e-3f;

	private static void assertVectorEquals(Vector3f expected, Vector3f actual, float eps) {
		assertEquals(expected.x, actual.x, eps);
		assertEquals(expected.y, actual.y, eps);
		assertEquals(expected.z, actual.z, eps);
	}

	@Test
	public void testInsertPointCommand_executeUndoRedo_append() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));

		EndoCommands.InsertPointCommand cmd = new EndoCommands.InsertPointCommand(path, path.size(),
				new Vector3f(2f, 0f, 0f));

		cmd.execute();
		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(2).getPosition(), EPS);

		cmd.undo();
		assertEquals(2, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(1f, 0f, 0f), path.getPoint(1).getPosition(), EPS);

		cmd.execute();
		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(2).getPosition(), EPS);
	}

	@Test
	public void testInsertPointCommand_executeUndoRedo_middle() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(2f, 0f, 0f));

		EndoCommands.InsertPointCommand cmd = new EndoCommands.InsertPointCommand(path, 1,
				new Vector3f(1f, 0f, 0f));

		cmd.execute();
		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(1f, 0f, 0f), path.getPoint(1).getPosition(), EPS);
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(2).getPosition(), EPS);

		cmd.undo();
		assertEquals(2, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(1).getPosition(), EPS);

		cmd.execute();
		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(1f, 0f, 0f), path.getPoint(1).getPosition(), EPS);
	}

	@Test
	public void testMovePointCommand_executeUndoRedo() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));

		Vector3f target = new Vector3f(5f, 5f, 5f);
		EndoCommands.MovePointCommand cmd = new EndoCommands.MovePointCommand(path, 0, target);

		// コンストラクタ後に渡したVector3fを書き換えても、コマンドの動作に影響しないこと
		target.set(999f, 999f, 999f);

		cmd.execute();
		assertVectorEquals(new Vector3f(5f, 5f, 5f), path.getPoint(0).getPosition(), EPS);

		cmd.undo();
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);

		cmd.execute();
		assertVectorEquals(new Vector3f(5f, 5f, 5f), path.getPoint(0).getPosition(), EPS);
	}

	@Test
	public void testRemovePointCommand_executeUndoRedo() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(1f, 0f, 0f));
		path.addPoint(new Vector3f(2f, 0f, 0f));

		EndoCommands.RemovePointCommand cmd = new EndoCommands.RemovePointCommand(path, 1);

		cmd.execute();
		assertEquals(2, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(1).getPosition(), EPS);

		cmd.undo();
		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(1f, 0f, 0f), path.getPoint(1).getPosition(), EPS);
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(2).getPosition(), EPS);

		cmd.execute();
		assertEquals(2, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(2f, 0f, 0f), path.getPoint(1).getPosition(), EPS);
	}

	@Test
	public void testSetCameraUCommand_executeUndoRedo() {
		EndoCamera camera = new EndoCamera();
		camera.setU(0.25f);

		EndoCommands.SetCameraUCommand cmd = new EndoCommands.SetCameraUCommand(camera, 0.75f);

		cmd.execute();
		assertEquals(0.75f, camera.getU(), EPS);

		cmd.undo();
		assertEquals(0.25f, camera.getU(), EPS);

		cmd.execute();
		assertEquals(0.75f, camera.getU(), EPS);
	}

	@Test
	public void testSetCameraUCommand_clampingDelegatesToCamera() {
		EndoCamera camera = new EndoCamera();
		camera.setU(0.5f);

		EndoCommands.SetCameraUCommand cmd = new EndoCommands.SetCameraUCommand(camera, 5f);

		cmd.execute();
		assertEquals(1f, camera.getU(), EPS); // EndoCamera.setUのクランプにより1.0fになる

		cmd.undo();
		assertEquals(0.5f, camera.getU(), EPS);
	}

	@Test
	public void testUndoManagerIntegration_sequenceOfCommands_undoAllThenRedoAll() {
		EndoPath3D path = new EndoPath3D();
		path.addPoint(new Vector3f(0f, 0f, 0f));
		path.addPoint(new Vector3f(3f, 0f, 0f));
		EndoCamera camera = new EndoCamera(path);
		camera.setU(0.2f);

		UndoManager undoManager = new UndoManager();

		undoManager.addCommand(new EndoCommands.InsertPointCommand(path, path.size(), new Vector3f(6f, 0f, 0f)));
		undoManager.addCommand(new EndoCommands.MovePointCommand(path, 0, new Vector3f(1f, 1f, 1f)));
		undoManager.addCommand(new EndoCommands.SetCameraUCommand(camera, 0.9f));

		// 期待される最終状態（直接メソッド呼び出しで同じ操作をした場合）
		EndoPath3D directPath = new EndoPath3D();
		directPath.addPoint(new Vector3f(0f, 0f, 0f));
		directPath.addPoint(new Vector3f(3f, 0f, 0f));
		EndoCamera directCamera = new EndoCamera(directPath);
		directCamera.setU(0.2f);
		directPath.addPoint(new Vector3f(6f, 0f, 0f));
		directPath.setPointPosition(0, new Vector3f(1f, 1f, 1f));
		directCamera.setU(0.9f);

		assertEquals(3, path.size());
		assertVectorEquals(new Vector3f(1f, 1f, 1f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(6f, 0f, 0f), path.getPoint(2).getPosition(), EPS);
		assertEquals(0.9f, camera.getU(), EPS);

		// 3回undoして元の状態に戻ること
		undoManager.undo();
		undoManager.undo();
		undoManager.undo();

		assertEquals(2, path.size());
		assertVectorEquals(new Vector3f(0f, 0f, 0f), path.getPoint(0).getPosition(), EPS);
		assertVectorEquals(new Vector3f(3f, 0f, 0f), path.getPoint(1).getPosition(), EPS);
		assertEquals(0.2f, camera.getU(), EPS);

		// 3回redoして、直接メソッド呼び出しの最終状態と一致すること
		undoManager.redo();
		undoManager.redo();
		undoManager.redo();

		assertEquals(directPath.size(), path.size());
		for (int i = 0; i < directPath.size(); i++) {
			assertVectorEquals(directPath.getPoint(i).getPosition(), path.getPoint(i).getPosition(), EPS);
		}
		assertEquals(directCamera.getU(), camera.getU(), EPS);
	}
}
