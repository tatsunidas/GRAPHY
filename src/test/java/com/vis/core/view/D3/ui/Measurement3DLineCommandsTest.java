package com.vis.core.view.D3.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.junit.Before;
import org.junit.Test;

/**
 * Measurement3DLineCommands のユニットテスト。
 *
 * 検証項目:
 *  - AddPointCommand: execute で点が追加される
 *  - AddPointCommand: undo で最後の点が除去される
 *  - AddPointCommand: execute → undo → execute のラウンドトリップ
 *  - ClearCommand: execute でリストが空になる
 *  - ClearCommand: undo で元のリストが復元される
 *  - ClearCommand: execute → undo → execute のラウンドトリップ
 *  - AddPoint に渡した Vector3f を後で書き換えても影響しない（防御コピー）
 *  - UndoManager 経由での一連操作と全 undo / 全 redo
 */
public class Measurement3DLineCommandsTest {

    private static final float EPS = 1e-5f;

    private List<Vector3f> renderPts;
    private List<Vector3f> mmPts;

    @Before
    public void setUp() {
        renderPts = new ArrayList<>();
        mmPts     = new ArrayList<>();
    }

    // ── ヘルパー ────────────────────────────────────────────────────────────

    private static void assertVec3(float ex, float ey, float ez, Vector3f actual) {
        assertEquals("x", ex, actual.x, EPS);
        assertEquals("y", ey, actual.y, EPS);
        assertEquals("z", ez, actual.z, EPS);
    }

    // ── AddPointCommand ──────────────────────────────────────────────────────

    @Test
    public void testAddPoint_execute_appendsToLists() {
        Measurement3DLineCommands.AddPointCommand cmd =
            new Measurement3DLineCommands.AddPointCommand(
                renderPts, mmPts,
                new Vector3f(0.1f, 0.2f, 0.3f),
                new Vector3f(10f, 20f, 30f));

        cmd.execute();

        assertEquals("renderPts size", 1, renderPts.size());
        assertEquals("mmPts size",     1, mmPts.size());
        assertVec3(0.1f, 0.2f, 0.3f, renderPts.get(0));
        assertVec3(10f,  20f,  30f,  mmPts.get(0));
    }

    @Test
    public void testAddPoint_undo_removesLastPoint() {
        renderPts.add(new Vector3f(0f, 0f, 0f));
        mmPts.add(new Vector3f(0f, 0f, 0f));

        Measurement3DLineCommands.AddPointCommand cmd =
            new Measurement3DLineCommands.AddPointCommand(
                renderPts, mmPts,
                new Vector3f(1f, 1f, 1f),
                new Vector3f(100f, 100f, 100f));

        cmd.execute();
        assertEquals("after execute: size 2", 2, renderPts.size());

        cmd.undo();
        assertEquals("after undo: size 1", 1, renderPts.size());
        assertVec3(0f, 0f, 0f, renderPts.get(0));
    }

    @Test
    public void testAddPoint_executeUndoRedo_roundTrip() {
        Measurement3DLineCommands.AddPointCommand cmd =
            new Measurement3DLineCommands.AddPointCommand(
                renderPts, mmPts,
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Vector3f(50f, 50f, 50f));

        cmd.execute();
        cmd.undo();
        cmd.execute(); // redo

        assertEquals("redo: size 1", 1, renderPts.size());
        assertVec3(0.5f, 0.5f, 0.5f, renderPts.get(0));
        assertVec3(50f,  50f,  50f,  mmPts.get(0));
    }

    @Test
    public void testAddPoint_multiplePoints_orderPreserved() {
        for (int i = 1; i <= 3; i++) {
            new Measurement3DLineCommands.AddPointCommand(
                renderPts, mmPts,
                new Vector3f(i * 0.1f, 0f, 0f),
                new Vector3f(i * 10f,  0f, 0f)).execute();
        }
        assertEquals("3 points added", 3, renderPts.size());
        assertVec3(0.1f, 0f, 0f, renderPts.get(0));
        assertVec3(0.2f, 0f, 0f, renderPts.get(1));
        assertVec3(0.3f, 0f, 0f, renderPts.get(2));
    }

    /** AddPointCommand はコンストラクタ引数の Vector3f を防御コピーする */
    @Test
    public void testAddPoint_defensiveCopy_mutationAfterConstructDoesNotAffect() {
        Vector3f r = new Vector3f(0.1f, 0.2f, 0.3f);
        Vector3f m = new Vector3f(10f, 20f, 30f);

        Measurement3DLineCommands.AddPointCommand cmd =
            new Measurement3DLineCommands.AddPointCommand(renderPts, mmPts, r, m);

        // 渡した後に書き換え
        r.set(999f, 999f, 999f);
        m.set(999f, 999f, 999f);

        cmd.execute();

        assertVec3(0.1f, 0.2f, 0.3f, renderPts.get(0));
        assertVec3(10f,  20f,  30f,  mmPts.get(0));
    }

    // ── ClearCommand ─────────────────────────────────────────────────────────

    @Test
    public void testClear_execute_clearsLists() {
        renderPts.add(new Vector3f(1f, 2f, 3f));
        renderPts.add(new Vector3f(4f, 5f, 6f));
        mmPts.add(new Vector3f(10f, 20f, 30f));
        mmPts.add(new Vector3f(40f, 50f, 60f));

        new Measurement3DLineCommands.ClearCommand(renderPts, mmPts).execute();

        assertTrue("renderPts cleared", renderPts.isEmpty());
        assertTrue("mmPts cleared",     mmPts.isEmpty());
    }

    @Test
    public void testClear_undo_restoresSnapshot() {
        renderPts.add(new Vector3f(1f, 2f, 3f));
        mmPts.add(new Vector3f(10f, 20f, 30f));

        Measurement3DLineCommands.ClearCommand cmd =
            new Measurement3DLineCommands.ClearCommand(renderPts, mmPts);
        cmd.execute();
        cmd.undo();

        assertEquals("restored size", 1, renderPts.size());
        assertVec3(1f, 2f, 3f, renderPts.get(0));
        assertVec3(10f, 20f, 30f, mmPts.get(0));
    }

    @Test
    public void testClear_executeUndoRedo_roundTrip() {
        renderPts.add(new Vector3f(0.5f, 0.5f, 0f));
        mmPts.add(new Vector3f(5f, 5f, 0f));

        Measurement3DLineCommands.ClearCommand cmd =
            new Measurement3DLineCommands.ClearCommand(renderPts, mmPts);
        cmd.execute();
        cmd.undo();
        cmd.execute(); // redo

        assertTrue("redo: lists cleared", renderPts.isEmpty());
    }

    @Test
    public void testClear_emptyList_executeSafe() {
        // 空リストに対して Clear しても例外が出ないこと
        Measurement3DLineCommands.ClearCommand cmd =
            new Measurement3DLineCommands.ClearCommand(renderPts, mmPts);
        cmd.execute();
        assertTrue(renderPts.isEmpty());
    }

    // ── UndoManager 統合 ────────────────────────────────────────────────────

    @Test
    public void testUndoManager_addThreePoints_undoAll_redoAll() {
        UndoManager um = new UndoManager();

        for (int i = 1; i <= 3; i++) {
            um.addCommand(new Measurement3DLineCommands.AddPointCommand(
                renderPts, mmPts,
                new Vector3f(i * 0.1f, 0f, 0f),
                new Vector3f(i * 10f,  0f, 0f)));
        }
        assertEquals("3 points", 3, renderPts.size());

        um.undo(); um.undo(); um.undo();
        assertTrue("all undone: empty", renderPts.isEmpty());

        um.redo(); um.redo(); um.redo();
        assertEquals("all redone: 3 points", 3, renderPts.size());
        assertVec3(0.1f, 0f, 0f, renderPts.get(0));
        assertVec3(0.3f, 0f, 0f, renderPts.get(2));
    }

    @Test
    public void testUndoManager_addThenClear_undoClearRestores() {
        UndoManager um = new UndoManager();

        um.addCommand(new Measurement3DLineCommands.AddPointCommand(
            renderPts, mmPts,
            new Vector3f(0.1f, 0f, 0f), new Vector3f(10f, 0f, 0f)));
        um.addCommand(new Measurement3DLineCommands.AddPointCommand(
            renderPts, mmPts,
            new Vector3f(0.2f, 0f, 0f), new Vector3f(20f, 0f, 0f)));
        um.addCommand(new Measurement3DLineCommands.ClearCommand(renderPts, mmPts));

        assertTrue("after clear: empty", renderPts.isEmpty());

        um.undo(); // undo clear
        assertEquals("after undo clear: 2 points", 2, renderPts.size());
        assertVec3(0.1f, 0f, 0f, renderPts.get(0));
        assertVec3(0.2f, 0f, 0f, renderPts.get(1));

        um.undo(); // undo 2nd add
        assertEquals("after undo add2: 1 point", 1, renderPts.size());

        um.redo(); // redo 2nd add
        um.redo(); // redo clear
        assertTrue("redo to clear: empty", renderPts.isEmpty());
    }

    @Test
    public void testUndoManager_undoBeyondHistory_doesNotThrow() {
        UndoManager um = new UndoManager();
        um.addCommand(new Measurement3DLineCommands.AddPointCommand(
            renderPts, mmPts,
            new Vector3f(1f, 0f, 0f), new Vector3f(10f, 0f, 0f)));
        um.undo();
        um.undo(); // nothing to undo — should not throw
        assertTrue("should still be empty", renderPts.isEmpty());
    }
}
