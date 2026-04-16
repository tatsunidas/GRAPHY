/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.plugin;

import javax.swing.Icon;

/**
 * ツールバーへのアイコン表示に対応したプラグインインターフェース。
 * 既存の PlugIn インターフェースを継承します。
 */
public interface ToolbarPlugIn extends PlugIn {
	
    /** ツールバーに表示するアイコンを返します */
    public Icon getIcon();
    
    /** マウスホバー時に表示するテキスト（プラグイン名や説明）を返します */
    public String getToolTipText();
}