package com.vis.core.reporting.ui;

import javax.swing.text.JTextComponent;

/**
 * Pluggable input assistance for the report editor. A clean seam for the deferred
 * Phase-2 features — voice input (音声入力) and the Japanese medical autocomplete
 * dictionary (日本語医療用語辞書) — so they can be attached without changing the
 * editor. The default implementation is a no-op.
 *
 * @author tatsunidas
 */
public interface TextInputAssist {

	/** Attach this assistant to the given editor text component. */
	void install(JTextComponent target);

	/** Detach (release listeners/resources). */
	default void uninstall(JTextComponent target) {
	}

	/** No-op assistant used until voice / dictionary assistants are provided. */
	TextInputAssist NONE = new TextInputAssist() {
		@Override
		public void install(JTextComponent target) {
			// intentionally empty
		}
	};
}
