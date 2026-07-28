package com.vis.core.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.log.Log;
import com.vis.core.util.PropertiesUtil;

/**
 * GRAPHY の更新確認（通知のみ。自動ダウンロード・自動更新は行わない）。
 *
 * <p>GitHub Releases の最新タグを取得し、実行中のバージョン（{@code application.properties} の
 * {@code app.version} ＝ pom.xml の version）と比較する。GRAPHY-Next 側の同等機能
 * （{@code frontend/src/help/update.ts}）とバージョン比較・スキップの考え方を揃えてある。
 *
 * <p>UI は {@link UpdateNotice}。ネットワークアクセスを含むため、EDT からは直接呼ばないこと。
 */
public final class UpdateChecker {

	/** 更新確認先のリポジトリ。GRAPHY-Next は別リポジトリなので混同しないこと。 */
	public static final String REPO = "tatsunidas/GRAPHY";

	private static final String LATEST_RELEASE_API =
			"https://api.github.com/repos/" + REPO + "/releases/latest";

	public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";

	/**
	 * 起動時チェックは「アプリが立ち上がらない」事態を招かないよう短めに切る。
	 * 取得できなければ黙って諦める（更新確認はあくまで付随機能）。
	 */
	private static final Duration TIMEOUT = Duration.ofSeconds(8);

	// JSONライブラリを依存に追加せず、必要な2項目だけを取り出す。GitHub の
	// releases/latest は tag_name / html_url を必ず含む。
	private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern HTML_URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");

	private UpdateChecker() {
	}

	/** 確認結果。 */
	public enum Kind {
		/** 新しいバージョンがある。 */
		UPDATE,
		/** 実行中のものが最新。 */
		LATEST,
		/** 取得に失敗した（ネットワーク等）。 */
		ERROR
	}

	/** 確認結果と、表示に必要な情報。 */
	public static final class Result {
		public final Kind kind;
		public final String current;
		public final String latest;
		public final String releaseUrl;

		Result(Kind kind, String current, String latest, String releaseUrl) {
			this.kind = kind;
			this.current = current;
			this.latest = latest;
			this.releaseUrl = releaseUrl;
		}
	}

	/**
	 * 最新リリースを取得して、実行中のバージョンと比較する。
	 * ネットワークI/Oを行うのでバックグラウンドスレッドから呼ぶこと。
	 */
	public static Result check() {
		String current = normalize(ApplicationFacade.version);
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(TIMEOUT)
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
					.header("Accept", "application/vnd.github+json")
					.header("User-Agent", "GRAPHY-update-check")
					.timeout(TIMEOUT)
					.GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				Log.logger.warning("UpdateChecker: unexpected status " + response.statusCode());
				return new Result(Kind.ERROR, current, null, RELEASES_PAGE);
			}

			String body = response.body();
			Matcher tag = TAG_NAME.matcher(body);
			if (!tag.find()) {
				Log.logger.warning("UpdateChecker: tag_name not found in the response");
				return new Result(Kind.ERROR, current, null, RELEASES_PAGE);
			}
			String latest = normalize(tag.group(1));

			// html_url はリリース本体のものが先に出てくる（assets より前）。
			Matcher url = HTML_URL.matcher(body);
			String releaseUrl = url.find() ? url.group(1) : RELEASES_PAGE;

			Kind kind = compare(latest, current) > 0 ? Kind.UPDATE : Kind.LATEST;
			return new Result(kind, current, latest, releaseUrl);
		} catch (IOException e) {
			Log.logger.warning("UpdateChecker: failed to check for updates. " + e.getMessage());
			return new Result(Kind.ERROR, current, null, RELEASES_PAGE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Result(Kind.ERROR, current, null, RELEASES_PAGE);
		}
	}

	/** 先頭の "v" を除いた素のバージョン文字列。 */
	public static String normalize(String version) {
		if (version == null) {
			return "";
		}
		String v = version.trim();
		if (v.startsWith("v") || v.startsWith("V")) {
			v = v.substring(1);
		}
		return v;
	}

	/**
	 * ドット区切りの数値比較（プレリリース接尾辞は簡易に無視）。
	 * a&gt;b なら 1、a&lt;b なら -1、等しければ 0。
	 */
	public static int compare(String a, String b) {
		String[] pa = normalize(a).split("[.+-]");
		String[] pb = normalize(b).split("[.+-]");
		int n = Math.max(pa.length, pb.length);
		for (int i = 0; i < n; i++) {
			int x = numberAt(pa, i);
			int y = numberAt(pb, i);
			if (x != y) {
				return x > y ? 1 : -1;
			}
		}
		return 0;
	}

	private static int numberAt(String[] parts, int index) {
		if (index >= parts.length) {
			return 0;
		}
		try {
			return Integer.parseInt(parts[index]);
		} catch (NumberFormatException e) {
			// "0.1.0-rc1" の "rc1" のような接尾辞は 0 として扱う。
			return 0;
		}
	}

	/**
	 * そのバージョンを「スキップ」済みか。起動時の自動通知を抑止するためのもので、
	 * ユーザーが自分でメニューから確認した場合は無視する（見たいから開いているため）。
	 */
	public static boolean isSkipped(String version) {
		String skipped = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props,
				GraphyProp.SkipUpdateVersion);
		return skipped != null && !skipped.isEmpty() && skipped.equals(normalize(version));
	}

	/** そのバージョンを今後は起動時に通知しないよう記録する。 */
	public static void skipVersion(String version) {
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.SkipUpdateVersion,
				normalize(version));
	}
}
