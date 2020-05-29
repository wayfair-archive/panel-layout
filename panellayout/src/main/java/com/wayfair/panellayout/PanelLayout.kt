/*  These imports produce a detekt false-positive:
    import androidx.core.graphics.component1
    import androidx.core.graphics.component2
    import androidx.core.graphics.component3
    import androidx.core.graphics.component4

    So UnusedImports are suppressed. */
@file:Suppress("UnusedImports", "CommentOverPrivateFunction")

package com.wayfair.panellayout

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.*
import android.view.MotionEvent.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.getResourceIdOrThrow
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.wayfair.panellayout.PanelPosition.*
import com.wayfair.panellayout.PanelPosition.values
import com.wayfair.panellayout.PanelState.HorizontalEdge.LEFT
import com.wayfair.panellayout.PanelState.HorizontalEdge.RIGHT
import com.wayfair.panellayout.PanelState.Snap.ANIMATING
import com.wayfair.panellayout.PanelState.Snap.FLOATING
import com.wayfair.panellayout.PanelState.Snap.SNAPPED
import com.wayfair.panellayout.PanelState.VerticalEdge.BOTTOM
import com.wayfair.panellayout.PanelState.VerticalEdge.TOP
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Suppress("LargeClass", "LongMethod") // It's hard to split view classes
@SuppressLint("ClickableViewAccessibility") // ClickableViewAccessibility: We do not want to handle clicks on move and resize handles
class PanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), PanelLayoutCommands {

    // Public field
    override var panelLayoutCallbacks: Callbacks? = null
    override var panelVisible: Boolean
        get() = panelState.isVisible
        set(value) {
            if (value) showPanel() else hidePanel()
        }

    // Attrs (initial values are not used)
    @IdRes
    private var panelResId = 0

    @IdRes
    private var contentResId = 0

    @IdRes
    private var moveHandleResId = 0

    @IdRes
    private var resizeHandleResId = 0

    private var resizeEnabled = false

    private var panelMinWidth = 0
    private var panelMaxWidth = 0

    private var panelMinHeight = 0
    private var panelMaxHeight = 0

    private var panelStartWidth = 0
    private var panelStartHeight = 0

    private var panelSnapWidth = 0f
    private var panelSnapHeight = 0f

    private var panelSnapWidthPercent = 0f
    private var panelSnapHeightPercent = 0f

    @LayoutRes
    private var snapLeftLayout = 0

    @LayoutRes
    private var snapTopLayout = 0

    @LayoutRes
    private var snapRightLayout = 0

    @LayoutRes
    private var snapBottomLayout = 0

    private var snapAnimationDuration = 0L
    private var snapOverlayAnimationDuration = 0L

    @ColorRes
    private var snapOverlayColor = 0

    private var snapToEdges = 0

    // View references
    private lateinit var content: View
    private lateinit var panelView: View
    private lateinit var moveHandle: View
    private var resizeHandle: View? = null

    private val leftOverlay = View(context)
    private val topOverlay = View(context)
    private val rightOverlay = View(context)
    private val bottomOverlay = View(context)

    private var isPanelStateRestored = false

    lateinit var panelState: PanelState

    val initialPanelState: PanelState
        get() = PanelState(
            snap = FLOATING,
            position = NO_EDGE,
            size = panelStartWidth to panelStartHeight
        )

    private val preferredSnapWidth: Float
        get() = when (panelSnapWidthPercent) {
            NOT_SET -> panelSnapWidth
            else -> width * panelSnapWidthPercent
        }

    private val preferredSnapHeight: Float
        get() = when (panelSnapHeightPercent) {
            NOT_SET -> panelSnapHeight
            else -> height * panelSnapHeightPercent
        }

    // Touch / motion related
    private val moveSnapListener = PanelMoveSnapListener()
    private val popListener = PanelPopListener()
    private val resizeListener = PanelResizeListener()

    private var lastDownEvent: MotionEvent? = null
    private var relativeTouchPositionX = 0f
    private var relativeTouchPositionY = 0f

    private var touchSubject: View? = null

    init {
        isMotionEventSplittingEnabled = false
        isSaveEnabled = true

        readAttrs(attrs)
        setUpOverlays()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!isPanelStateRestored) {
            panelState = initialPanelState
        }

        ensureChildren()
        setTouchListeners()
        restorePanelFromState()
    }

    private fun ensureChildren() {
        fun Int.toResourceName() = resources.getResourceName(this)

        require(::content.isInitialized) {
            "Could not find child (panel_content) with id: ${contentResId.toResourceName()}"
        }
        require(::panelView.isInitialized) {
            "Could not find child (panel_view) with id: ${panelResId.toResourceName()}"
        }
        require(::moveHandle.isInitialized) {
            "Could not find child (panel_move_handle) with id: ${moveHandleResId.toResourceName()}"
        }
        if (resizeEnabled) {
            require(resizeHandle != null) {
                "Could not find child (panel_resize_handle) with id: ${resizeHandleResId.toResourceName()}"
            }
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)

        child.findViewById<View>(contentResId)?.let {
            content = it
        }
        child.findViewById<View>(panelResId)?.let {
            panelView = it
            moveHandle = it.findViewById(moveHandleResId)
        }
        if (resizeEnabled) {
            child.findViewById<View>(resizeHandleResId)?.let {
                resizeHandle = it
            }
        }
    }

    private fun restorePanelFromState() = post {
        if (!panelState.isVisible) {
            panelView.isVisible = false
        } else if (panelState.snap == FLOATING) {
            // Restore size
            val (withWidth, withHeight) = panelState.size

            // Restore position
            val toX = panelState.horizontalNearestEdgeDistance.toX(withWidth)
            val toY = panelState.verticalNearestEdgeDistance.toY(withHeight)

            applyFloatingPanelConstraints(toX, toY, withWidth, withHeight)
        } else {
            // Restore snap
            snapPanelTo(panelState.position)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()

        return bundleOf(
            PARCELABLE_KEY_SUPER_STATE to superState,
            PARCELABLE_KEY_PANEL_STATE to panelState
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = state as Bundle
        panelState = bundle.getParcelable(PARCELABLE_KEY_PANEL_STATE)!!
        val superState: AbsSavedState? = bundle.getParcelable(PARCELABLE_KEY_SUPER_STATE)

        isPanelStateRestored = true

        super.onRestoreInstanceState(superState)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (panelState.snap == FLOATING) {
            coercePanelSize()
        }
    }

    private fun readAttrs(attrs: AttributeSet?) = attrs?.let {
        val a = context.obtainStyledAttributes(it, R.styleable.PanelLayout, 0, 0)
        try {
            readResizeEnabledAttr(a)
            readViewReferenceAttrs(a)
            readMinMaxSizeAttrs(a)
            readStartSizeAttrs(a)
            readSnapSizeAttrs(a)
            readSnapToEdgesAttr(a)
            readSnapAttrs(a)
            readOverlayAndAnimationAttrs(a)
        } finally {
            a.recycle()
        }
    }

    private fun readResizeEnabledAttr(a: TypedArray) {
        resizeEnabled = a.getBoolean(R.styleable.PanelLayout_panel_resize_enabled, true)
    }

    private fun readViewReferenceAttrs(a: TypedArray) {
        panelResId = a.getResourceIdOrThrow(R.styleable.PanelLayout_panel_view)
        contentResId = a.getResourceIdOrThrow(R.styleable.PanelLayout_panel_content)
        moveHandleResId = a.getResourceIdOrThrow(R.styleable.PanelLayout_panel_move_handle)
        resizeHandleResId = a.getResourceId(R.styleable.PanelLayout_panel_resize_handle, -1)
    }

    private fun readMinMaxSizeAttrs(a: TypedArray) {
        panelMinWidth = a.getDimensionPixelSize(
            R.styleable.PanelLayout_panel_min_width,
            resources.getDimensionPixelSize(R.dimen.panel_default_min_width)
        )
        panelMaxWidth = a.getDimensionPixelSize(
            R.styleable.PanelLayout_panel_max_width,
            resources.getDimensionPixelSize(R.dimen.panel_default_max_width)
        )
        panelMinHeight = a.getDimensionPixelSize(
            R.styleable.PanelLayout_panel_min_height,
            resources.getDimensionPixelSize(R.dimen.panel_default_min_height)
        )
        panelMaxHeight = a.getDimensionPixelSize(
            R.styleable.PanelLayout_panel_max_height,
            resources.getDimensionPixelSize(R.dimen.panel_default_max_height)
        )
    }

    private fun readStartSizeAttrs(a: TypedArray) {
        panelStartWidth = a.getDimensionPixelSize(
            R.styleable.PanelLayout_panel_start_width,
            resources.getDimensionPixelSize(R.dimen.panel_default_start_width)
        )
        panelStartHeight = a.getDimensionPixelSize(
            R.styleable.PanelLayout_panel_start_height,
            resources.getDimensionPixelSize(R.dimen.panel_default_start_height)
        )
    }

    private fun readSnapSizeAttrs(a: TypedArray) {
        panelSnapWidthPercent = a.getFloat(R.styleable.PanelLayout_panel_snap_width_percent, NOT_SET)
        panelSnapHeightPercent = a.getFloat(R.styleable.PanelLayout_panel_snap_height_percent, NOT_SET)

        if (panelSnapWidthPercent == NOT_SET) {
            panelSnapWidth = a.getDimension(
                R.styleable.PanelLayout_panel_snap_width,
                resources.getDimension(R.dimen.panel_default_snap_width)
            )
        }

        if (panelSnapHeightPercent == NOT_SET) {
            panelSnapHeight = a.getDimension(
                R.styleable.PanelLayout_panel_snap_height,
                resources.getDimension(R.dimen.panel_default_snap_height)
            )
        }
    }

    private fun readSnapToEdgesAttr(a: TypedArray) {
        snapToEdges = a.getInt(
            R.styleable.PanelLayout_panel_snap_to_edges,
            R.integer.panel_default_snap_edges
        )
    }

    private fun readSnapAttrs(a: TypedArray) {
        snapLeftLayout = a.getResourceId(
            R.styleable.PanelLayout_panel_snap_left_layout,
            R.layout.panel_view_default_snap_left
        )
        snapTopLayout = a.getResourceId(
            R.styleable.PanelLayout_panel_snap_top_layout,
            R.layout.panel_view_default_snap_top
        )
        snapRightLayout = a.getResourceId(
            R.styleable.PanelLayout_panel_snap_right_layout,
            R.layout.panel_view_default_snap_right
        )
        snapBottomLayout = a.getResourceId(
            R.styleable.PanelLayout_panel_snap_bottom_layout,
            R.layout.panel_view_default_snap_bottom
        )
    }

    private fun readOverlayAndAnimationAttrs(a: TypedArray) {
        snapAnimationDuration = a.getInt(
            R.styleable.PanelLayout_panel_snap_animation_duration,
            resources.getInteger(R.integer.panel_default_snap_animation_duration)
        ).toLong()
        snapOverlayAnimationDuration = a.getInt(
            R.styleable.PanelLayout_panel_snap_overlay_animation_duration,
            resources.getInteger(R.integer.panel_default_snap_overlay_animation_duration)
        ).toLong()
        snapOverlayColor = a.getResourceId(
            R.styleable.PanelLayout_panel_snap_overlay_color,
            R.color.panel_default_snap_overlay_color
        )
    }

    private fun setUpOverlays() {
        for (overlay in arrayOf(leftOverlay, topOverlay, rightOverlay, bottomOverlay)) {
            overlay.background = ContextCompat.getColor(context, snapOverlayColor).toDrawable()
            overlay.alpha = 0f
            overlay.layoutParams = LayoutParams(1, 1) // create square 1px x 1px to scale later.
            overlay.elevation = 2f
            addView(overlay)
        }
    }

    private fun setTouchListeners() {
        setOnTouchListener { v: View, event: MotionEvent ->
            when (touchSubject) {
                moveHandle -> when (panelState.snap) {
                    FLOATING -> moveSnapListener.onTouch(v, event)
                    SNAPPED -> popListener.onTouch(v, event)
                    else -> false
                }
                resizeHandle -> when (panelState.snap) {
                    FLOATING -> resizeListener.onTouch(v, event)
                    else -> false
                }
                else -> false
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            ACTION_DOWN -> {
                relativeTouchPositionX = event.rawX - panelView.x
                relativeTouchPositionY = event.rawY - panelView.y

                lastDownEvent = obtain(event)

                if (moveHandle.containsInHitRect(event)) {
                    touchSubject = moveHandle
                } else if (resizeEnabled && resizeHandle != null && resizeHandle!!.containsInHitRect(event)) {
                    touchSubject = resizeHandle!!
                } else {
                    touchSubject = null
                }

                return false
            }
            ACTION_MOVE -> {
                return touchSubject != null && event.isSignificantlyDistantTo(lastDownEvent!!)
            }
            ACTION_UP -> {
                return touchSubject != null && event.isSignificantlyDistantTo(lastDownEvent!!)
            }
            ACTION_CANCEL -> {
                touchSubject = null
                return false
            }
            else -> return false
        }
    }

    /**
     * Imitates a panel resize with zero difference in size.
     * <p>
     * This will make sure the panel size is recalculated and limited by the new panelLayout size.
     * Whenever the keyboard is shown, we resize the panelView to fit into the limited space available.
     */
    private fun coercePanelSize() = post {
        val (updatedWidth, updatedHeight) = calculateNewSize(diffX = 0f, diffY = 0f)
        resizePanel(updatedWidth, updatedHeight)
    }

    private fun calculateNewSize(diffX: Float, diffY: Float): Pair<Int, Int> {
        var updatedWidth =
            (panelView.width + diffX).roundToInt().coerceIn(panelMinWidth, panelMaxWidth)
        var updatedHeight =
            (panelView.height + diffY).roundToInt().coerceIn(panelMinHeight, panelMaxHeight)

        // These negative adjustment values are to prevent resizing out of the panel layout
        val negativeAdjustmentX = (width - (panelView.x + updatedWidth)).coerceAtMost(0f).roundToInt()
        val negativeAdjustmentY = (height - (panelView.y + updatedHeight)).coerceAtMost(0f).roundToInt()

        updatedWidth += negativeAdjustmentX
        updatedHeight += negativeAdjustmentY

        return updatedWidth to updatedHeight
    }

    private fun resizePanel(updatedWidth: Int, updatedHeight: Int) {
        val params = panelView.layoutParams as LayoutParams
        params.width = updatedWidth
        params.height = updatedHeight
        panelView.layoutParams = params
    }

    private fun calculatePositionFor(x: Int, y: Int): PanelPosition {
        val (left, top, right, bottom) = panelView.moveBounds()

        if (x == left) return LEFT_EDGE
        if (x == right) return RIGHT_EDGE
        if (y == top) return TOP_EDGE
        if (y == bottom) return BOTTOM_EDGE
        return NO_EDGE
    }

    private fun showSnapOverlay(snapPosition: PanelPosition) {

        val overlay = snapPosition.overlay()
        val (touchPointX, touchPointY) = snapPosition.touchPoint()
        val (scaleX, scaleY) = snapPosition.overlayScale()

        overlay.translationX = touchPointX
        overlay.translationY = touchPointY
        overlay.pivotX = touchPointX / width
        overlay.pivotY = touchPointY / height

        overlay.clearAnimation()

        overlay.animate()
            .alpha(1f)
            .scaleX(scaleX)
            .scaleY(scaleY)
            .setDuration(snapOverlayAnimationDuration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun hideSnapOverlay(snapPosition: PanelPosition) {
        val overlay = snapPosition.overlay()

        overlay.clearAnimation()

        overlay.animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(snapOverlayAnimationDuration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun hidePanel() {
        if (panelState.snap == SNAPPED) {
            val transition = AutoTransition().apply {
                interpolator = AccelerateDecelerateInterpolator()
                duration = snapAnimationDuration

                addListener(object : Transition.TransitionListener {
                    override fun onTransitionEnd(transition: Transition) {
                        panelLayoutCallbacks?.afterClose()
                    }

                    override fun onTransitionStart(transition: Transition) {} // No-op
                    override fun onTransitionResume(transition: Transition) {} // No-op
                    override fun onTransitionPause(transition: Transition) {} // No-op
                    override fun onTransitionCancel(transition: Transition) {} // No-op
                })
            }

            TransitionManager.beginDelayedTransition(this, transition)
        }

        panelView.isVisible = false
        panelState.isVisible = false
    }

    private fun showPanel() {
        panelState.isVisible = true

        restorePanelFromState()
    }

    override fun popPanelTo(x: Int, y: Int) {
        panelState.snap = ANIMATING

        val (popWidth, popHeight) = panelState.sanitizedSize()

        val transition = ChangeBounds().apply {
            interpolator = AccelerateDecelerateInterpolator()
            duration = snapAnimationDuration

            addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {
                    panelLayoutCallbacks?.beforePop(x, y)
                    resizeHandle?.isVisible = true
                }

                override fun onTransitionEnd(transition: Transition) {
                    panelLayoutCallbacks?.afterPop(x, y)
                }

                override fun onTransitionResume(transition: Transition) {} // No-op
                override fun onTransitionPause(transition: Transition) {} // No-op
                override fun onTransitionCancel(transition: Transition) {} // No-op
            })
        }

        TransitionManager.beginDelayedTransition(this, transition)

        applyFloatingPanelConstraints(x, y, popWidth, popHeight)

        panelState.position = NO_EDGE
        panelState.snap = FLOATING
    }

    private fun applyFloatingPanelConstraints(x: Int, y: Int, width: Int, height: Int) = ConstraintSet().apply {
        clone(this)
        clear(panelResId)
        clear(contentResId)

        connect(contentResId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
        connect(contentResId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        connect(contentResId, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT)
        connect(contentResId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        setTranslationX(panelResId, x.toFloat())
        setTranslationY(panelResId, y.toFloat())

        constrainWidth(panelResId, width)
        constrainHeight(panelResId, height)

        applyTo(this@PanelLayout)
    }

    override fun snapPanelTo(panelPosition: PanelPosition) {
        if (panelState.snap == FLOATING) {
            switchToMarginPositioning()
        }

        panelState.snap = ANIMATING

        when (panelPosition) {
            LEFT_EDGE -> applyConstraintsWithAnimation(snapLeftLayout)
            RIGHT_EDGE -> applyConstraintsWithAnimation(snapRightLayout)
            TOP_EDGE -> applyConstraintsWithAnimation(snapTopLayout)
            BOTTOM_EDGE -> applyConstraintsWithAnimation(snapBottomLayout)
            NO_EDGE -> throw IllegalArgumentException("Cannot snap panel with position NO_EDGE")
        }
    }

    private fun switchToMarginPositioning() {
        val marginLeft = panelView.translationX.roundToInt()
        val marginTop = panelView.translationY.roundToInt()

        panelView.translationX = 0f
        panelView.translationY = 0f

        ConstraintSet().apply {
            clone(this)
            connect(panelResId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, marginLeft)
            connect(panelResId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTop)
            applyTo(this@PanelLayout)
        }
    }

    private fun applyConstraintsWithAnimation(layoutResId: Int) = post {
        val constraintSet = ConstraintSet()
        constraintSet.load(context, layoutResId)

        val transition = AutoTransition().apply {
            interpolator = AccelerateDecelerateInterpolator()
            duration = snapAnimationDuration

            addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {
                    panelLayoutCallbacks?.beforeSnap(panelState.position)
                    resizeHandle?.isVisible = false
                }

                override fun onTransitionEnd(transition: Transition) {
                    panelLayoutCallbacks?.afterSnap(panelState.position)
                }

                override fun onTransitionResume(transition: Transition) {} // No-op
                override fun onTransitionPause(transition: Transition) {} // No-op
                override fun onTransitionCancel(transition: Transition) {} // No-op
            })
        }

        TransitionManager.beginDelayedTransition(this, transition)

        constraintSet.applyTo(this)
        panelState.snap = SNAPPED
    }

    private fun PanelPosition.isSnapEnabled() = snapToEdges.hasFlag(this.snapFlag())

    private fun PanelPosition.snapFlag() = when (this) {
        LEFT_EDGE -> SNAP_TO_LEFT
        RIGHT_EDGE -> SNAP_TO_RIGHT
        TOP_EDGE -> SNAP_TO_TOP
        BOTTOM_EDGE -> SNAP_TO_BOTTOM
        NO_EDGE -> throw IllegalArgumentException("You cannot get a snapFlag for PanelState.Position.NO_EDGE")
    }

    private fun PanelPosition.overlay() = when (this) {
        LEFT_EDGE -> leftOverlay
        RIGHT_EDGE -> rightOverlay
        TOP_EDGE -> topOverlay
        BOTTOM_EDGE -> bottomOverlay
        NO_EDGE -> throw IllegalArgumentException("You cannot get an overlay for PanelState.Position.NO_EDGE")
    }

    private fun PanelPosition.overlayScale() = when (this) {
        LEFT_EDGE,
        RIGHT_EDGE -> preferredSnapWidth to height.toFloat() + 1
        TOP_EDGE,
        BOTTOM_EDGE -> width.toFloat() + 1 to preferredSnapHeight
        NO_EDGE -> throw IllegalArgumentException("You cannot get an overlayScale for PanelState.Position.NO_EDGE")
    }

    private fun PanelPosition.touchPoint() = when (this) {
        LEFT_EDGE -> 0f to panelView.centerY()
        RIGHT_EDGE -> width.toFloat() to panelView.centerY()
        TOP_EDGE -> panelView.centerX() to 0f
        BOTTOM_EDGE -> panelView.centerX() to height.toFloat()
        NO_EDGE -> throw IllegalArgumentException("You cannot get an touchPoint for PanelState.Position.NO_EDGE")
    }

    private fun View.centerX() = x + width / 2f

    private fun View.centerY() = y + height / 2f

    private fun PanelState.sanitizedSize(): Pair<Int, Int> {
        val (savedWidth, savedHeight) = size

        val w = when (savedWidth) {
            -1 -> panelStartWidth
            else -> savedWidth.coerceAtMost(width)
        }

        val h = when (savedHeight) {
            -1 -> panelStartHeight
            else -> savedHeight.coerceAtMost(height)
        }

        return w to h
    }

    private fun PanelState.HorizontalEdgeDistance.toX(panelWidth: Int) = when (this.edge) {
        LEFT -> distance
        RIGHT -> width - panelWidth - distance
    }

    private fun PanelState.VerticalEdgeDistance.toY(panelHeight: Int) = when (this.edge) {
        TOP -> distance
        BOTTOM -> height - panelHeight - distance
    }

    private fun View.calculateHorizontalNearestEdgeDistance(): PanelState.HorizontalEdgeDistance {
        val leftDistance = this.x.roundToInt()
        val rightDistance = this@PanelLayout.width - this.x.roundToInt() - this.width

        return if (leftDistance <= rightDistance) {
            PanelState.HorizontalEdgeDistance(LEFT, leftDistance)
        } else {
            PanelState.HorizontalEdgeDistance(RIGHT, rightDistance)
        }
    }

    private fun View.calculateVerticalNearestEdgeDistance(): PanelState.VerticalEdgeDistance {
        val topDistance = this.y.roundToInt()
        val bottomDistance = this@PanelLayout.height - this.y.roundToInt() - this.height

        return if (topDistance <= bottomDistance) {
            PanelState.VerticalEdgeDistance(TOP, topDistance)
        } else {
            PanelState.VerticalEdgeDistance(BOTTOM, bottomDistance)
        }
    }

    // Calculates move bounds given (width to height) pair in PanelLayout
    private fun Pair<Int, Int>.moveBounds(offset: Int = 0): Rect {
        val (childWidth, childHeight) = this

        val minX = 0 + offset
        val minY = 0 + offset
        val maxX = (width - childWidth - offset).coerceAtLeast(minX)
        val maxY = (height - childHeight - offset).coerceAtLeast(minY)

        return Rect(minX, minY, maxX, maxY)
    }

    private fun View.moveBounds(): Rect = (width to height).moveBounds()

    private fun MotionEvent.isSignificantlyDistantTo(other: MotionEvent): Boolean {
        val deltaX = other.rawX - this.rawX
        val deltaY = other.rawY - this.rawY

        return sqrt(deltaX * deltaX + deltaY * deltaY) > ViewConfiguration.get(context).scaledTouchSlop
    }

    private fun View.containsInHitRect(event: MotionEvent) = offsetHitRectToAscendant(this@PanelLayout)
        .contains(event.x.toInt(), event.y.toInt())

    private fun View.offsetHitRectToAscendant(ascendant: View): Rect {
        val hitRect = Rect()
        this.getHitRect(hitRect)

        var iterator = parent as View

        while (iterator != ascendant) {
            hitRect.offset(iterator.x.toInt(), iterator.y.toInt())
            iterator = iterator.parent as View
        }

        return hitRect
    }

    private inner class PanelMoveSnapListener : OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                ACTION_DOWN -> true
                ACTION_MOVE -> handleActionMove(event)
                ACTION_UP -> handleActionUp()
                else -> false
            }
        }

        private fun handleActionMove(event: MotionEvent): Boolean {
            val (left, top, right, bottom) = panelView.moveBounds()

            val nextX = (event.rawX - relativeTouchPositionX).roundToInt().coerceIn(left, right)
            val nextY = (event.rawY - relativeTouchPositionY).roundToInt().coerceIn(top, bottom)

            val currentPosition = panelState.position
            val nextPosition = calculatePositionFor(nextX, nextY)
            panelState.position = nextPosition

            handleSnapOverlayAnimation(currentPosition, nextPosition)
            movePanelTo(nextX, nextY)

            return true
        }

        private fun handleActionUp(): Boolean {
            if (panelState.position != NO_EDGE && panelState.position.isSnapEnabled()) {
                hideSnapOverlay(panelState.position)
                snapPanelTo(panelState.position)
            }

            touchSubject = null
            return true
        }

        // Checking current and next positions shows or hides snap overlays with animation
        private fun handleSnapOverlayAnimation(
            currentPosition: PanelPosition,
            nextPosition: PanelPosition
        ) {
            for (position in values()) {
                if (position == NO_EDGE) continue
                if (position.isSnapEnabled().not()) continue

                if (currentPosition != position && nextPosition == position) {
                    showSnapOverlay(position)
                }

                if (currentPosition == position && nextPosition != position) {
                    hideSnapOverlay(position)
                }
            }
        }

        private fun movePanelTo(destinationX: Int, destinationY: Int) {
            panelView.animate()
                .translationX(destinationX.toFloat())
                .translationY(destinationY.toFloat())
                .setDuration(0)
                .withEndAction {
                    panelState.horizontalNearestEdgeDistance = panelView.calculateHorizontalNearestEdgeDistance()
                    panelState.verticalNearestEdgeDistance = panelView.calculateVerticalNearestEdgeDistance()
                }
                .start()
        }
    }

    private inner class PanelPopListener : OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                ACTION_DOWN -> true
                ACTION_MOVE -> handleActionMove(event)
                ACTION_UP -> handleActionUp()
                else -> false
            }
        }

        private fun handleActionMove(event: MotionEvent): Boolean {
            relativeTouchPositionX = event.rawX - panelView.x
            relativeTouchPositionY = event.rawY - panelView.y

            val (popWidth, popHeight) = panelState.sanitizedSize()

            val nextRelativeTouchPositionX = relativeTouchPositionX / panelView.width * popWidth
            val nextRelativeTouchPositionY = relativeTouchPositionY / panelView.height * popHeight

            val (left, top, right, bottom) = (popWidth to popHeight).moveBounds(offset = PANEL_POP_OFFSET)

            val popToX = (event.rawX - nextRelativeTouchPositionX).roundToInt().coerceIn(left, right)
            val popToY = (event.rawY - nextRelativeTouchPositionY).roundToInt().coerceIn(top, bottom)

            popPanelTo(popToX, popToY)

            relativeTouchPositionX = event.rawX - popToX
            relativeTouchPositionY = event.rawY - popToY

            return true
        }

        private fun handleActionUp(): Boolean {
            touchSubject = null
            return true
        }
    }

    private inner class PanelResizeListener : OnTouchListener {
        private var previousX = NOT_SET
        private var previousY = NOT_SET

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                ACTION_DOWN -> true
                ACTION_MOVE -> handleActionMove(event)
                ACTION_UP -> handleActionUp()
                else -> false
            }
        }

        private fun handleActionMove(event: MotionEvent): Boolean {
            if (previousX == NOT_SET || previousY == NOT_SET) {
                previousX = lastDownEvent!!.rawX
                previousY = lastDownEvent!!.rawY
            }

            val diffX = event.rawX - previousX
            val diffY = event.rawY - previousY

            val (updatedWidth, updatedHeight) = calculateNewSize(diffX, diffY)

            resizePanel(updatedWidth, updatedHeight)

            panelState.size = updatedWidth to updatedHeight

            previousX = event.rawX
            previousY = event.rawY

            return true
        }

        private fun handleActionUp(): Boolean {
            previousX = NOT_SET
            previousY = NOT_SET
            touchSubject = null
            return true
        }
    }

    interface Callbacks {
        fun beforeSnap(position: PanelPosition)
        fun afterSnap(position: PanelPosition)
        fun beforePop(popToX: Int, popToY: Int)
        fun afterPop(popToX: Int, popToY: Int)
        fun afterClose()
    }

    companion object {
        private const val NOT_SET = -1f
        private const val PANEL_POP_OFFSET = 4 // distance from edges when panelState is popped. in pixels
        private const val PARCELABLE_KEY_SUPER_STATE = "superState"
        private const val PARCELABLE_KEY_PANEL_STATE = "panelState"

        // Possible flags for snapToEdges
        private const val SNAP_TO_LEFT = 1
        private const val SNAP_TO_TOP = 2
        private const val SNAP_TO_RIGHT = 4
        private const val SNAP_TO_BOTTOM = 8

        private fun Int.hasFlag(flag: Int) = (this and flag) == flag
    }
}
