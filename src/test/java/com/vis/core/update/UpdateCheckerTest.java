package com.vis.core.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 更新確認のバージョン比較。ここを間違えると「新しい版が出ているのに通知されない」
 * （あるいは最新なのに毎回通知される）という、気づきにくい壊れ方をする。
 */
public class UpdateCheckerTest {

	@Test
	public void normalize_stripsLeadingV() {
		assertEquals("0.0.21", UpdateChecker.normalize("v0.0.21"));
		assertEquals("0.0.21", UpdateChecker.normalize("V0.0.21"));
		assertEquals("0.0.21", UpdateChecker.normalize("  0.0.21  "));
		assertEquals("", UpdateChecker.normalize(null));
	}

	@Test
	public void compare_ordersBySegmentNumerically() {
		// 文字列比較だと "0.0.9" > "0.0.10" になってしまう典型例。
		assertTrue(UpdateChecker.compare("0.0.10", "0.0.9") > 0);
		assertTrue(UpdateChecker.compare("0.1.0", "0.0.21") > 0);
		assertTrue(UpdateChecker.compare("1.0.0", "0.9.9") > 0);
		assertTrue(UpdateChecker.compare("0.0.21", "0.0.22") < 0);
	}

	@Test
	public void compare_treatsEqualVersionsAsEqualRegardlessOfPrefix() {
		assertEquals(0, UpdateChecker.compare("v0.0.21", "0.0.21"));
		// 桁数が違っても、欠けている分は 0 として扱う。
		assertEquals(0, UpdateChecker.compare("0.1", "0.1.0"));
	}

	@Test
	public void compare_ignoresPreReleaseSuffix() {
		// 接尾辞は 0 扱い。少なくとも「同じ数値部分なら新版と誤認しない」ことを保証する。
		assertEquals(0, UpdateChecker.compare("0.1.0-rc1", "0.1.0"));
		assertTrue(UpdateChecker.compare("0.1.1-rc1", "0.1.0") > 0);
	}
}
