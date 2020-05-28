package com.wayfair.panellayout

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.color_selector.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addShowHideClickListener()
        addPanelElevationAndRadiusAnimations()
        addColorSelectorClickListener()
    }

    private fun addShowHideClickListener() {
        showHideButton.setOnClickListener {
            panelLayout.panelVisible = !panelLayout.panelVisible
            showHideButton.isSelected = !showHideButton.isSelected
        }
    }

    private fun addColorSelectorClickListener() {
        colorSelector.children.forEach {
            it.setOnClickListener {
                it.backgroundTintList?.defaultColor?.let { color ->
                    content.setBackgroundColor(color)
                }
            }
        }
    }

    private fun addPanelElevationAndRadiusAnimations() {
        panelLayout.panelLayoutCallbacks = object : PanelLayout.Callbacks {
            override fun beforeSnap(position: PanelPosition) {
                panel.animateRadius(
                    from = resources.getDimension(R.dimen.panel_corner_radius),
                    to = resources.getDimension(R.dimen.zero)
                )

                panel.animateElevation(
                    from = resources.getDimension(R.dimen.panel_elevation),
                    to = resources.getDimension(R.dimen.zero)
                )
            }

            override fun afterSnap(position: PanelPosition) {}

            override fun beforePop(popToX: Int, popToY: Int) {
                panel.animateRadius(
                    from = resources.getDimension(R.dimen.zero),
                    to = resources.getDimension(R.dimen.panel_corner_radius)
                )

                panel.animateElevation(
                    from = resources.getDimension(R.dimen.zero),
                    to = resources.getDimension(R.dimen.panel_elevation)
                )
            }

            override fun afterPop(popToX: Int, popToY: Int) {}

            override fun afterClose() {}
        }
    }
}
