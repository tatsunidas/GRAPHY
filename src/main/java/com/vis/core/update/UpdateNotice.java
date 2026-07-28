package com.vis.core.update;

import java.awt.Desktop;
import java.awt.Frame;
import java.net.URI;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;

/**
 * 更新確認の結果をユーザーに知らせる（通知のみ。ダウンロード・置き換えは行わない）。
 *
 * <p>2通りの入り口がある:
 * <ul>
 *   <li>Help&amp;Contact &gt; Check for Updates … 明示的な確認。結果が何であれ必ず返事を返す。</li>
 *   <li>起動時の自動確認 … 新しい版があるときだけ黙って知らせる。取得失敗や最新の場合は何も出さない
 *       （起動のたびにダイアログが出るのは邪魔なだけなので）。</li>
 * </ul>
 *
 * <p>ネットワークアクセスは {@link SwingWorker} の background 側で行い、EDTを止めない。
 */
public final class UpdateNotice {

	private UpdateNotice() {
	}

	/** Help メニューからの明示的な確認。結果に関わらずダイアログで返事をする。 */
	public static void checkManually() {
		check(false);
	}

	/**
	 * 起動時の自動確認。新しい版があり、かつ「スキップ」されていない場合だけ通知する。
	 * 呼び出しはメイン画面表示後に行うこと（ダイアログの親が必要なため）。
	 */
	public static void checkOnStartup() {
		check(true);
	}

	private static void check(final boolean silent) {
		new SwingWorker<UpdateChecker.Result, Void>() {
			@Override
			protected UpdateChecker.Result doInBackground() {
				return UpdateChecker.check();
			}

			@Override
			protected void done() {
				UpdateChecker.Result result;
				try {
					result = get();
				} catch (Exception e) {
					Log.logger.warning("UpdateNotice: update check failed. " + e.getMessage());
					if (!silent) {
						showError();
					}
					return;
				}
				show(result, silent);
			}
		}.execute();
	}

	private static void show(UpdateChecker.Result result, boolean silent) {
		switch (result.kind) {
			case UPDATE:
				// 起動時は「スキップ」済みの版なら黙る。手動確認では常に見せる。
				if (silent && UpdateChecker.isSkipped(result.latest)) {
					return;
				}
				showUpdateAvailable(result, silent);
				break;
			case LATEST:
				if (!silent) {
					JOptionPane.showMessageDialog(parent(),
							"お使いの GRAPHY は最新です（v" + result.current + "）。",
							"Check for Updates", JOptionPane.INFORMATION_MESSAGE);
				}
				break;
			case ERROR:
			default:
				if (!silent) {
					showError();
				}
				break;
		}
	}

	private static void showUpdateAvailable(UpdateChecker.Result result, boolean silent) {
		// 「スキップ」は起動時の自動通知を止めるための選択肢。手動確認のときに出しても
		// 意味が通らない（自分で開いておいて今後見ない、は選びにくい）ので出し分ける。
		Object[] options = silent
				? new Object[] { "ダウンロードページを開く", "このバージョンをスキップ", "後で" }
				: new Object[] { "ダウンロードページを開く", "閉じる" };

		int choice = JOptionPane.showOptionDialog(parent(),
				"新しいバージョン v" + result.latest + " が公開されています。\n"
						+ "お使いのバージョン: v" + result.current,
				"Check for Updates",
				JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
				null, options, options[0]);

		if (choice == 0) {
			browse(result.releaseUrl);
		} else if (silent && choice == 1) {
			UpdateChecker.skipVersion(result.latest);
		}
	}

	private static void showError() {
		JOptionPane.showMessageDialog(parent(),
				"更新を確認できませんでした。しばらくしてからお試しください。\n"
						+ UpdateChecker.RELEASES_PAGE,
				"Check for Updates", JOptionPane.WARNING_MESSAGE);
	}

	private static void browse(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(url));
				return;
			}
			Log.logger.warning("UpdateNotice: Desktop browse is not supported on this platform.");
		} catch (Exception e) {
			Log.logger.warning("UpdateNotice: failed to open the browser. " + e.getMessage());
		}
		// ブラウザを開けない環境では、URLをコピーできる形で見せる（行き止まりにしない）。
		JOptionPane.showInputDialog(parent(),
				"ブラウザを開けませんでした。以下のURLをコピーしてご利用ください。",
				"Check for Updates", JOptionPane.INFORMATION_MESSAGE, null, null, url);
	}

	private static Frame parent() {
		Object main = WindowManager.getMainScreen();
		return main instanceof Frame ? (Frame) main : null;
	}
}
