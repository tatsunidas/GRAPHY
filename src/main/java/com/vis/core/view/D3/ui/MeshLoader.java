package com.vis.core.view.D3.ui;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;
import com.vis.core.log.Log;

public class MeshLoader {

	// ========================================================
	// 拡張子から自動判別して読み込む統合メソッド
	// ========================================================
	public static MeshData load(String filePath) {
		String lowerPath = filePath.toLowerCase();
		if (lowerPath.endsWith(".obj")) {
			return loadOBJ(filePath);
		} else if (lowerPath.endsWith(".stl")) {
			return loadSTL(filePath);
		} else {
			Log.logger.warning("Unsupported mesh format: " + filePath);
			return null;
		}
	}

	// ========================================================
	// OBJ ローダー
	// ========================================================
	public static MeshData loadOBJ(String filePath) {
		Log.logger.fine("Loading OBJ Mesh: " + filePath);

		List<Vector3f> rawVertices = new ArrayList<>();
		List<Vector3f> rawNormals = new ArrayList<>();
		List<Float> finalVertices = new ArrayList<>();
		List<Float> finalNormals = new ArrayList<>();
		List<Integer> finalIndices = new ArrayList<>();

		Map<String, Integer> vertexCache = new HashMap<>();
		int nextIndex = 0;

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String[] tokens = line.split("\\s+");
				String type = tokens[0];

				if (type.equals("v")) {
					rawVertices.add(new Vector3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]),
							Float.parseFloat(tokens[3])));
				} else if (type.equals("vn")) {
					rawNormals.add(new Vector3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]),
							Float.parseFloat(tokens[3])));
				} else if (type.equals("f")) {
					List<Integer> faceIndices = new ArrayList<>();
					for (int i = 1; i < tokens.length; i++) {
						String vertexDef = tokens[i];
						if (!vertexCache.containsKey(vertexDef)) {
							vertexCache.put(vertexDef, nextIndex++);
							String[] parts = vertexDef.split("/");
							int vIdx = Integer.parseInt(parts[0]) - 1;
							Vector3f v = rawVertices.get(vIdx);
							finalVertices.add(v.x);
							finalVertices.add(v.y);
							finalVertices.add(v.z);

							if (parts.length >= 3 && !parts[2].isEmpty()) {
								int nIdx = Integer.parseInt(parts[2]) - 1;
								Vector3f n = rawNormals.get(nIdx);
								finalNormals.add(n.x);
								finalNormals.add(n.y);
								finalNormals.add(n.z);
							} else {
								finalNormals.add(0.0f);
								finalNormals.add(1.0f);
								finalNormals.add(0.0f);
							}
						}
						faceIndices.add(vertexCache.get(vertexDef));
					}
					// 三角形分割
					for (int i = 1; i < faceIndices.size() - 1; i++) {
						finalIndices.add(faceIndices.get(0));
						finalIndices.add(faceIndices.get(i));
						finalIndices.add(faceIndices.get(i + 1));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return buildMeshData(finalVertices, finalNormals, finalIndices);
	}

	// ========================================================
	// STL ローダー (バイナリ・アスキー両対応)
	// ========================================================
	public static MeshData loadSTL(String filePath) {
		Log.logger.fine("Loading STL Mesh: " + filePath);
		try {
			byte[] bytes = Files.readAllBytes(Paths.get(filePath));

			// STLバイナリフォーマットのチェック
			if (bytes.length >= 84) {
				ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
				int numTriangles = buffer.getInt(80);

				if (bytes.length == 84 + 50 * numTriangles) {
					return parseBinarySTL(buffer, numTriangles);
				}
			}

			return parseAsciiSTL(filePath);

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static MeshData parseBinarySTL(ByteBuffer buffer, int numTriangles) {
		Log.logger.fine("Format: Binary STL (" + numTriangles + " triangles)");
		buffer.position(84);

		List<Float> finalVertices = new ArrayList<>(numTriangles * 9);
		List<Float> finalNormals = new ArrayList<>(numTriangles * 9);
		List<Integer> finalIndices = new ArrayList<>(numTriangles * 3);

		Map<String, Integer> vertexCache = new HashMap<>();
		int nextIndex = 0;

		for (int i = 0; i < numTriangles; i++) {
			float nx = buffer.getFloat();
			float ny = buffer.getFloat();
			float nz = buffer.getFloat();

			for (int v = 0; v < 3; v++) {
				float vx = buffer.getFloat();
				float vy = buffer.getFloat();
				float vz = buffer.getFloat();

				String key = vx + "," + vy + "," + vz;
				if (!vertexCache.containsKey(key)) {
					vertexCache.put(key, nextIndex++);
					finalVertices.add(vx);
					finalVertices.add(vy);
					finalVertices.add(vz);
					finalNormals.add(nx);
					finalNormals.add(ny);
					finalNormals.add(nz);
				}
				finalIndices.add(vertexCache.get(key));
			}
			buffer.getShort();
		}

		return buildMeshData(finalVertices, finalNormals, finalIndices);
	}

	private static MeshData parseAsciiSTL(String filePath) throws IOException {
		Log.logger.fine("Format: ASCII STL");
		List<Float> finalVertices = new ArrayList<>();
		List<Float> finalNormals = new ArrayList<>();
		List<Integer> finalIndices = new ArrayList<>();

		Map<String, Integer> vertexCache = new HashMap<>();
		int nextIndex = 0;
		float nx = 0, ny = 0, nz = 0;

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("facet normal")) {
					String[] tokens = line.split("\\s+");
					nx = Float.parseFloat(tokens[2]);
					ny = Float.parseFloat(tokens[3]);
					nz = Float.parseFloat(tokens[4]);
				} else if (line.startsWith("vertex")) {
					String[] tokens = line.split("\\s+");
					float vx = Float.parseFloat(tokens[1]);
					float vy = Float.parseFloat(tokens[2]);
					float vz = Float.parseFloat(tokens[3]);

					String key = vx + "," + vy + "," + vz;
					if (!vertexCache.containsKey(key)) {
						vertexCache.put(key, nextIndex++);
						finalVertices.add(vx);
						finalVertices.add(vy);
						finalVertices.add(vz);
						finalNormals.add(nx);
						finalNormals.add(ny);
						finalNormals.add(nz);
					}
					finalIndices.add(vertexCache.get(key));
				}
			}
		}
		return buildMeshData(finalVertices, finalNormals, finalIndices);
	}

	// ========================================================
	// プリミティブ配列への変換ユーティリティ & バリデーション実行
	// ========================================================
	private static MeshData buildMeshData(List<Float> vList, List<Float> nList, List<Integer> iList) {
		float[] vArr = new float[vList.size()];
		for (int i = 0; i < vList.size(); i++)
			vArr[i] = vList.get(i);

		float[] nArr = new float[nList.size()];
		for (int i = 0; i < nList.size(); i++)
			nArr[i] = nList.get(i);

		int[] iArr = new int[iList.size()];
		for (int i = 0; i < iList.size(); i++)
			iArr[i] = iList.get(i);

		Log.logger
				.fine(String.format("Mesh Arrays Built: %d vertices, %d triangles", vArr.length / 3, iArr.length / 3));

		MeshData mesh = new MeshData(vArr, nArr, iArr);

		// バリデーションを実行
		boolean isValid = MeshValidator.validate(mesh);

		// ★異常が検出された場合は自動修復を試みる
		if (!isValid) {
			Log.logger.warning("Attempting automatic repair of corrupted mesh data...");
			mesh = MeshRepairer.repair(mesh);

			// 修復後、もう一度バリデーションを掛けて本当に直ったか確認する（任意）
			isValid = MeshValidator.validate(mesh);
			if(!isValid) {
				Log.logger.warning("Cannot repair completely this corrupted mesh data..., loading failed.");
				return null;
			}
		}
		
		// ★ ここで正規化を実行して、画面に収まるようにする
		/*
		 * 引き延ばす処理なので、一旦コメントアウト
		 */
//        normalizeMesh(mesh);

		return mesh;
	}
	
	// ========================================================
    // メッシュ座標を画面サイズ(-0.5 ~ 0.5)に正規化する処理
    // ========================================================
    public static void normalizeMesh(MeshData mesh) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        // 1. バウンディングボックス（最小・最大座標）を求める
        for (int i = 0; i < mesh.vertices.length; i += 3) {
            float x = mesh.vertices[i];
            float y = mesh.vertices[i + 1];
            float z = mesh.vertices[i + 2];
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }

        // 2. 中心点と最大サイズを計算
        float cx = (minX + maxX) / 2.0f;
        float cy = (minY + maxY) / 2.0f;
        float cz = (minZ + maxZ) / 2.0f;
        
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        float maxDim = Math.max(dx, Math.max(dy, dz));

        // 3. 中心を原点(0,0,0)に合わせ、最大サイズが1.0になるように縮小
        if (maxDim > 0) {
            for (int i = 0; i < mesh.vertices.length; i += 3) {
                mesh.vertices[i]     = (mesh.vertices[i] - cx) / maxDim;
                mesh.vertices[i + 1] = (mesh.vertices[i + 1] - cy) / maxDim;
                mesh.vertices[i + 2] = (mesh.vertices[i + 2] - cz) / maxDim;
            }
        }
        Log.logger.fine("Mesh normalized. Max dimension was: " + maxDim);
    }
}