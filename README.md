## Panel Layout

![CI](https://github.com/GradleUp/auto-manifest/workflows/CI/badge.svg)

Panel Layout is a UI library for Android that allows you to display a floating and resizable panel that can also snap to the edges.

Panel Layout makes use of [ConstraintLayout](https://developer.android.com/training/constraint-layout) to lay out panel with rest of the content.

This library is inspired by a good iOS UI framework: [PanelKit](https://github.com/louisdh/panelkit)

![](https://user-images.githubusercontent.com/4990386/83229577-e6564f00-a190-11ea-8998-97322e3d818d.gif)

### Importing Panel Layout

[ ![Bintray](https://img.shields.io/bintray/v/wayfair/PanelLayout/PanelLayout) ](https://bintray.com/wayfair/PanelLayout/PanelLayout/_latestVersion)


```kotlin
dependencies {
    implementation("com.wayfair.panellayout:panellayout:<latest-version>")
}
```

**Note:** Panel Layout is currently in alpha and the public API it offers is __subject to heavy changes__.

### How to Use Panel Layout

## Library API
Define if Panel Layout is visible
```kotlin
var panelVisible: Boolean
```

Command that put Panel Layout in on of predefine PanelPosition. 
Possible panel positions are: `LEFT_EDGE`, `RIGHT_EDGE`, `TOP_EDGE`, `BOTTOM_EDGE`, `NO_EDGE`.
```kotlin
fun snapPanelTo(panelPosition: PanelPosition)
```

Command that put Panel Layout in absolute position with coordinates `x` and `y`.
```kotlin
fun popPanelTo(x: Int, y: Int)
```

Define listener to define actions on different kind of events.
```kotlin
var panelLayoutCallbacks: PanelLayout.Callbacks?

interface Callbacks {
    fun beforeSnap(position: PanelPosition)
    fun afterSnap(position: PanelPosition)
    fun beforePop(popToX: Int, popToY: Int)
    fun afterPop(popToX: Int, popToY: Int)
    fun afterClose()
}
```

## Example

Add Panel Layout in layout:
```xml
<com.wayfair.panellayout.PanelLayout
        android:id="@+id/panelLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:panel_content="@id/content"
        app:panel_move_handle="@id/moveHandle"
        app:panel_resize_enabled="true"
        app:panel_resize_handle="@id/resizeHandle"
        app:panel_snap_to_edges="all"
        app:panel_start_height="300dp"
        app:panel_start_width="300dp"
        app:panel_view="@id/panel">
</com.wayfair.panellayout.PanelLayout>
``` 

Controls and listeners in your code:
```kotlin
panelLayout.panelVisible = !panelLayout.panelVisible
```
```kotlin
panelLayout.popPanelTo(100, 100)
```
```kotlin
panelLayout.snapPanelTo(PanelPosition.RIGHT_EDGE)
```
```kotlin
panelLayout.panelLayoutCallbacks = object : PanelLayout.Callbacks {
    override fun beforeSnap(position: PanelPosition) {
        TODO("Not yet implemented")
    }

    override fun afterSnap(position: PanelPosition) {
        TODO("Not yet implemented")
    }

    override fun beforePop(popToX: Int, popToY: Int) {
        TODO("Not yet implemented")
    }

    override fun afterPop(popToX: Int, popToY: Int) {
        TODO("Not yet implemented")
    }

    override fun afterClose() {
        TODO("Not yet implemented")
    }
}
```

### LICENSE

Panel Layout is licensed under 2-clause BSD License

See [LICENSE.md](LICENSE.md) for details.

### CONTRIBUTION

Panel Layout is open to contribution. See [CONTRIBUTING.md](CONTRIBUTING.md) for details
