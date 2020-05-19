package com.wayfair.panellayout

import android.os.Parcelable
import com.wayfair.panellayout.PanelState.HorizontalEdge.LEFT
import com.wayfair.panellayout.PanelState.VerticalEdge.TOP
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PanelState(
    var isVisible: Boolean = true,
    var snap: Snap = Snap.FLOATING,
    var size: Pair<Int, Int> = -1 to -1,
    var position: PanelPosition = PanelPosition.NO_EDGE,
    var horizontalNearestEdgeDistance: HorizontalEdgeDistance = HorizontalEdgeDistance(edge = LEFT, distance = 0),
    var verticalNearestEdgeDistance: VerticalEdgeDistance = VerticalEdgeDistance(edge = TOP, distance = 0)
) : Parcelable {

    @Parcelize
    data class HorizontalEdgeDistance(val edge: HorizontalEdge, val distance: Int) : Parcelable

    @Parcelize
    data class VerticalEdgeDistance(val edge: VerticalEdge, val distance: Int) : Parcelable

    enum class HorizontalEdge {
        LEFT, RIGHT
    }

    enum class VerticalEdge {
        TOP, BOTTOM
    }

    enum class Snap {
        FLOATING, ANIMATING, SNAPPED
    }
}
