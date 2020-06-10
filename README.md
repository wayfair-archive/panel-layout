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

### Panel Layout API
Define if the Panel Layout is visible
```kotlin
var panelVisible: Boolean
```

The command that put Panel Layout in one of the predefine Panel Positions.
Possible panel positions are: `LEFT_EDGE`, `RIGHT_EDGE`, `TOP_EDGE`, `BOTTOM_EDGE`, `NO_EDGE`.
```kotlin
fun snapPanelTo(panelPosition: PanelPosition)
```

The command that put the Panel Layout in absolute position with coordinates `x` and `y`.
```kotlin
fun popPanelTo(x: Int, y: Int)
```

Define a listener to define actions on different kinds of events.
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

### Panel Layout attributes

`panel_content` - Resource id of view where is placed the Panel Layout

`panel_view` - Resource id of view inside the Panel Layout
 
`panel_move_handle` - Resource id of view used for moving the Panel Layout inside of content view

`panel_resize_enabled` - Flag that defines if the Panel Layout is resizable

`panel_snap_to_edges` - Define edges where the Panel Layout could be snapped. Possible values: `all`, `none`, `left`, `top`, `right` and `bottom`

`panel_start_height` - Start height

`panel_start_width` - Start width

### How to Use Panel Layout

Add Panel Layout in your layout:
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

        <FrameLayout
            android:id="@+id/content"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="#f3e5f5">
        </FrameLayout>

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/panel"
            android:layout_width="300dp"
            android:layout_height="300dp"
            app:cardBackgroundColor="@color/white">

            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <View
                    android:id="@+id/moveHandle"
                    android:layout_width="match_parent"
                    android:layout_height="48dp"
                    android:background="#e1bee7" />

                <ImageView
                    android:id="@+id/resizeHandle"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:layout_gravity="bottom|end"
                    android:padding="4dp"
                    android:src="@drawable/ic_resize"
                    app:tint="@color/divider" />
            </FrameLayout>
        </com.google.android.material.card.MaterialCardView>
    </com.wayfair.panellayout.PanelLayout>
``` 

Controls and listeners in the code:
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
