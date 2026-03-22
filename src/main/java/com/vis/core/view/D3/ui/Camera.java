/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 * (既存のライセンスヘッダはそのまま残してください)
 * ***** END LICENSE BLOCK ***** */
package com.vis.core.view.D3.ui;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Camera {
    // 注視点（ボリュームの中心）
    private Vector3f target = new Vector3f(0.0f, 0.0f, 0.0f);
    
    // カメラの距離（ズーム用）
    private float distance = 1.5f; 
    
    // ★ 追加: オイラー角ではなく、クォータニオンで回転状態を保持する
    private Quaternionf rotation = new Quaternionf(); 

    public Camera() {
        // 初期状態
        reset();
    }

    /**
     * 画面(Screen)を基準とした直感的なアークボール回転
     */
    public void rotate(float dx, float dy) {
        // 回転の感度 (必要に応じて数値を調整してください)
        float sensitivity = 0.005f; 
        
        // マウスの移動量
        float moveX = dx * sensitivity;
        float moveY = dy * sensitivity;
        
        // 総移動距離（これが回転角度になります）
        float angle = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        
        if (angle > 0.0001f) {
            // ★ポイント: マウスの移動方向に対して「垂直な軸」を計算する
            // 縦ドラッグ(dy) -> 画面の水平(X)軸で回転
            // 横ドラッグ(dx) -> 画面の垂直(Y)軸で回転
            Vector3f axis = new Vector3f(moveY, moveX, 0.0f).normalize();
            
            // 画面空間での差分回転クォータニオンを作成
            Quaternionf delta = new Quaternionf().rotateAxis(angle, axis);
            
            // 現在の回転に対して「前掛け(pre-multiply)」することで、
            // 「いま見えている画面の縦横」を基準にした回転が適用される
            delta.mul(rotation, rotation);
            
            // 誤差蓄積を防ぐため正規化
            rotation.normalize();
        }
    }

    public void zoom(float amount) {
        // ホイールの回転量に応じて距離を増減
        distance += amount * 0.1f;
        if (distance < 0.1f) {
            distance = 0.1f; // 近づきすぎ（裏返り）防止
        }
    }

    public Matrix4f getViewMatrix() {
        Matrix4f view = new Matrix4f();
        // 1. カメラを距離の分だけ手前（Zのプラス方向）に引く
        view.translate(0, 0, -distance);
        // 2. クォータニオンによる回転を適用
        view.rotate(rotation);
        // 3. ターゲットを中心にするため平行移動
        view.translate(-target.x, -target.y, -target.z);
        
        return view;
    }
    
    /**
     * カメラを初期状態に戻す
     * (Reset Camera ボタンから呼ばれる想定)
     */
    public void reset() {
        rotation.identity(); // 回転をリセット (0,0,0)
        distance = 1.5f;     // 初期距離に戻す
    }
}