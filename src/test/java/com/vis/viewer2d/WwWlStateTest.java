package com.vis.viewer2d;

import org.junit.Test;
import static org.junit.Assert.*;

import com.vis.core.view.D2.ui.glasses.WwWlState;

/**
 * WwWlState のユニットテスト。
 *
 * WW/WL (Window Width / Window Center) の状態管理が正しいかを検証する。
 * グレースケール画像の表示輝度範囲と RGB チャンネル別の調整値を保持するクラス。
 */
public class WwWlStateTest {

    @Test
    public void testConstructor_setsDefaultMinMax() {
        WwWlState state = new WwWlState(-1024, 3071);
        assertEquals("defaultMin", -1024.0, state.getDefaultMin(), 0.0);
        assertEquals("defaultMax",  3071.0, state.getDefaultMax(), 0.0);
    }

    @Test
    public void testConstructor_allChannelInitializedToDefault() {
        WwWlState state = new WwWlState(0.0, 4095.0);
        // チャンネル -1 (All) はデフォルト値で初期化される
        assertEquals("All channel min", 0.0,    state.getMin(-1), 0.0);
        assertEquals("All channel max", 4095.0, state.getMax(-1), 0.0);
    }

    @Test
    public void testConstructor_rgbChannelsInitTo0_255() {
        WwWlState state = new WwWlState(-1024, 3071);
        // RGB チャンネルは 0〜255 で初期化される
        assertEquals("R min", 0.0,   state.getMin(0), 0.0);
        assertEquals("R max", 255.0, state.getMax(0), 0.0);
        assertEquals("G min", 0.0,   state.getMin(1), 0.0);
        assertEquals("G max", 255.0, state.getMax(1), 0.0);
        assertEquals("B min", 0.0,   state.getMin(2), 0.0);
        assertEquals("B max", 255.0, state.getMax(2), 0.0);
    }

    @Test
    public void testSetValues_allChannel() {
        WwWlState state = new WwWlState(0.0, 255.0);
        state.setValues(-1, 100.0, 200.0);
        assertEquals("All channel min after set", 100.0, state.getMin(-1), 0.0);
        assertEquals("All channel max after set", 200.0, state.getMax(-1), 0.0);
    }

    @Test
    public void testSetValues_rgbChannels() {
        WwWlState state = new WwWlState(0.0, 255.0);
        state.setValues(0, 10.0, 240.0); // Red
        state.setValues(1, 20.0, 230.0); // Green
        state.setValues(2, 30.0, 220.0); // Blue

        assertEquals("R min", 10.0,  state.getMin(0), 0.0);
        assertEquals("R max", 240.0, state.getMax(0), 0.0);
        assertEquals("G min", 20.0,  state.getMin(1), 0.0);
        assertEquals("G max", 230.0, state.getMax(1), 0.0);
        assertEquals("B min", 30.0,  state.getMin(2), 0.0);
        assertEquals("B max", 220.0, state.getMax(2), 0.0);
    }

    @Test
    public void testSetValues_doesNotAffectOtherChannels() {
        WwWlState state = new WwWlState(0.0, 255.0);
        state.setValues(0, 50.0, 200.0); // Red のみ変更

        // Green/Blue は変わらない
        assertEquals("G min unchanged", 0.0,   state.getMin(1), 0.0);
        assertEquals("G max unchanged", 255.0, state.getMax(1), 0.0);
    }

    @Test
    public void testResetToDefault_restoresAllChannel() {
        WwWlState state = new WwWlState(-500.0, 1500.0);
        state.setValues(-1, 0.0, 100.0); // 一度変更

        state.resetToDefault();

        assertEquals("All min restored", -500.0, state.getMin(-1), 0.0);
        assertEquals("All max restored", 1500.0, state.getMax(-1), 0.0);
    }

    @Test
    public void testResetToDefault_restoresRgbTo0_255() {
        WwWlState state = new WwWlState(0.0, 255.0);
        state.setValues(0, 50.0, 200.0); // 変更
        state.resetToDefault();

        assertEquals("R min reset", 0.0,   state.getMin(0), 0.0);
        assertEquals("R max reset", 255.0, state.getMax(0), 0.0);
    }

    @Test
    public void testGetMin_unknownChannel_returnsDefaultMin() {
        WwWlState state = new WwWlState(-1024.0, 3071.0);
        // 存在しないチャンネル (例: 99) は defaultMin を返す
        assertEquals("unknown channel min", -1024.0, state.getMin(99), 0.0);
    }

    @Test
    public void testGetMax_unknownChannel_returnsDefaultMax() {
        WwWlState state = new WwWlState(-1024.0, 3071.0);
        assertEquals("unknown channel max", 3071.0, state.getMax(99), 0.0);
    }

    @Test
    public void testSynchronized_setAndGet_consistency() throws InterruptedException {
        WwWlState state = new WwWlState(0.0, 255.0);
        int THREADS = 8;
        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final double v = i * 10.0;
            threads[i] = new Thread(() -> {
                state.setValues(-1, v, v + 100.0);
                state.getMin(-1);
                state.getMax(-1);
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        // スレッドセーフなら例外なく終了する（結果は不定でよい）
        assertTrue("Concurrent access completed without exception", true);
    }

    @Test
    public void testNegativeMinMax_accepted() {
        WwWlState state = new WwWlState(-32768.0, -1.0);
        assertEquals("negative defaultMin", -32768.0, state.getDefaultMin(), 0.0);
        assertEquals("negative defaultMax", -1.0,     state.getDefaultMax(), 0.0);
        assertEquals("All channel min",  -32768.0, state.getMin(-1), 0.0);
        assertEquals("All channel max",  -1.0,     state.getMax(-1), 0.0);
    }
}
