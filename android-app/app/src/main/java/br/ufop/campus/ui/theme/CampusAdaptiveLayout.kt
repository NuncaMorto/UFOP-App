package br.ufop.campus.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

enum class CampusWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class CampusWindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class CampusFoldPosture {
    FLAT,
    BOOK_VERTICAL_HINGE,
    TABLETOP_HORIZONTAL_HINGE
}

data class CampusAdaptiveLayoutInfo(
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    val isInMultiWindowMode: Boolean,
    val widthClass: CampusWindowWidthClass,
    val heightClass: CampusWindowHeightClass,
    val foldPosture: CampusFoldPosture = CampusFoldPosture.FLAT,
    val hingeWidthDp: Int = 0,
    val hingeLeftOffsetDp: Int? = null
) {
    val useNavigationRail: Boolean
        get() = widthClass != CampusWindowWidthClass.COMPACT

    val useExpandedRail: Boolean
        get() = widthClass == CampusWindowWidthClass.EXPANDED && widthDp >= 1000

    val useTwoPaneLayout: Boolean
        get() = widthClass == CampusWindowWidthClass.EXPANDED ||
            (foldPosture == CampusFoldPosture.BOOK_VERTICAL_HINGE && widthDp >= 600)

    val shouldForceScreenOrientation: Boolean
        get() = smallestWidthDp < 600 && !isInMultiWindowMode

    val bottomContentPaddingDp: Int
        get() = if (useNavigationRail) 28 else 136

    fun disciplineGridColumns(hasSideDetailOpen: Boolean = false): Int = when (widthClass) {
        CampusWindowWidthClass.COMPACT -> 2
        CampusWindowWidthClass.MEDIUM -> 3
        CampusWindowWidthClass.EXPANDED -> when {
            hasSideDetailOpen -> 2
            widthDp >= 1100 -> 4
            else -> 3
        }
    }

    val readableMaxWidthDp: Int
        get() = when (widthClass) {
            CampusWindowWidthClass.COMPACT -> Int.MAX_VALUE
            CampusWindowWidthClass.MEDIUM -> 760
            CampusWindowWidthClass.EXPANDED -> 1120
        }

    companion object {
        val CompactDefault: CampusAdaptiveLayoutInfo = computeCampusAdaptiveLayout(
            widthDp = 390,
            heightDp = 844,
            smallestWidthDp = 390
        )
    }
}

fun computeCampusAdaptiveLayout(
    widthDp: Int,
    heightDp: Int,
    smallestWidthDp: Int,
    isInMultiWindowMode: Boolean = false,
    foldPosture: CampusFoldPosture = CampusFoldPosture.FLAT,
    hingeWidthDp: Int = 0,
    hingeLeftOffsetDp: Int? = null
): CampusAdaptiveLayoutInfo {
    val safeWidth = widthDp.coerceAtLeast(1)
    val safeHeight = heightDp.coerceAtLeast(1)
    val safeSmallestWidth = smallestWidthDp.coerceAtLeast(1)

    val widthClass = when {
        safeWidth < 600 -> CampusWindowWidthClass.COMPACT
        safeWidth < 840 -> CampusWindowWidthClass.MEDIUM
        else -> CampusWindowWidthClass.EXPANDED
    }
    val heightClass = when {
        safeHeight < 480 -> CampusWindowHeightClass.COMPACT
        safeHeight < 900 -> CampusWindowHeightClass.MEDIUM
        else -> CampusWindowHeightClass.EXPANDED
    }

    return CampusAdaptiveLayoutInfo(
        widthDp = safeWidth,
        heightDp = safeHeight,
        smallestWidthDp = safeSmallestWidth,
        isInMultiWindowMode = isInMultiWindowMode,
        widthClass = widthClass,
        heightClass = heightClass,
        foldPosture = foldPosture,
        hingeWidthDp = hingeWidthDp.coerceAtLeast(0),
        hingeLeftOffsetDp = hingeLeftOffsetDp
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun rememberCampusAdaptiveLayout(): CampusAdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val widthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val heightDp = configuration.screenHeightDp.coerceAtLeast(1)
    val smallestWidthDp = configuration.smallestScreenWidthDp
        .takeIf { it > 0 }
        ?: minOf(widthDp, heightDp)
    val isInMultiWindowMode = runCatching {
        activity?.isInMultiWindowMode ?: false
    }.getOrDefault(false)

    var foldPosture by remember { mutableStateOf(CampusFoldPosture.FLAT) }
    var hingeWidthDp by remember { mutableIntStateOf(0) }
    var hingeLeftOffsetDp by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(activity, density.density) {
        if (activity == null) {
            foldPosture = CampusFoldPosture.FLAT
            hingeWidthDp = 0
            hingeLeftOffsetDp = null
            return@LaunchedEffect
        }
        runCatching {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .collect { layoutInfo ->
                    val foldingFeature = layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()
                    if (foldingFeature == null) {
                        foldPosture = CampusFoldPosture.FLAT
                        hingeWidthDp = 0
                        hingeLeftOffsetDp = null
                    } else {
                        val bounds = foldingFeature.bounds
                        val isHalfOpened = foldingFeature.state == FoldingFeature.State.HALF_OPENED
                        val isSeparating = foldingFeature.isSeparating
                        foldPosture = when {
                            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL &&
                                (isSeparating || isHalfOpened) ->
                                CampusFoldPosture.BOOK_VERTICAL_HINGE
                            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL &&
                                isHalfOpened ->
                                CampusFoldPosture.TABLETOP_HORIZONTAL_HINGE
                            else -> CampusFoldPosture.FLAT
                        }
                        val scale = density.density.coerceAtLeast(1f)
                        hingeWidthDp = (bounds.width() / scale).toInt().coerceAtLeast(0)
                        hingeLeftOffsetDp = if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
                            (bounds.left / scale).toInt().coerceAtLeast(0)
                        } else {
                            null
                        }
                    }
                }
        }.onFailure {
            foldPosture = CampusFoldPosture.FLAT
            hingeWidthDp = 0
            hingeLeftOffsetDp = null
        }
    }

    return remember(
        widthDp,
        heightDp,
        smallestWidthDp,
        isInMultiWindowMode,
        foldPosture,
        hingeWidthDp,
        hingeLeftOffsetDp
    ) {
        computeCampusAdaptiveLayout(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = smallestWidthDp,
            isInMultiWindowMode = isInMultiWindowMode,
            foldPosture = foldPosture,
            hingeWidthDp = hingeWidthDp,
            hingeLeftOffsetDp = hingeLeftOffsetDp
        )
    }
}

val LocalCampusAdaptiveLayout = staticCompositionLocalOf {
    CampusAdaptiveLayoutInfo.CompactDefault
}
