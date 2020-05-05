package com.wayfair.waystation.panel

internal interface PanelLayoutCommands {
    var panelLayoutCallbacks: PanelLayout.Callbacks?
    var panelVisible: Boolean

    fun snapPanelTo(panelPosition: PanelPosition)
    fun popPanelTo(x: Int, y: Int)
}
