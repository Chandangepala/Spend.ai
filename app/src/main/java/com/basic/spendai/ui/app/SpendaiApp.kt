package com.basic.spendai.ui.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.activity.ActivityScreen
import com.basic.spendai.ui.activity.ActivitySection
import com.basic.spendai.ui.ask.AskScreen
import com.basic.spendai.ui.home.HomeScreen
import com.basic.spendai.ui.insights.InsightsScreen
import com.basic.spendai.ui.motion.LocalChromeVisible
import com.basic.spendai.ui.settings.GemmaActions
import com.basic.spendai.ui.settings.SettingsScreen
import com.basic.spendai.ai.GemmaModel
import com.basic.spendai.reminders.ReminderTarget
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey
@Serializable data object ActivityRoute : NavKey
@Serializable data object InsightsRoute : NavKey
@Serializable data object AskRoute : NavKey
@Serializable data object SettingsRoute : NavKey

enum class Tab(val root: NavKey, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(HomeRoute, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Activity(ActivityRoute, "Activity", Icons.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    Insights(InsightsRoute, "Insights", Icons.Outlined.Insights, Icons.Rounded.Insights),
    Ask(AskRoute, "Ask", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Rounded.Chat),
}

/**
 * The app shell: a bottom bar on phones (a side rail on foldables/tablets) with four tabs. Each tab
 * keeps its own back stack, so switching tabs keeps its scroll position and state; back from any
 * tab returns Home. Settings opens on top of the current tab with the bar hidden.
 */
@Composable
fun SpendaiApp(
    viewModel: ExpenseViewModel,
    /** Set when a tapped reminder opened the app; [onOpened] clears it once handled. */
    openTarget: ReminderTarget? = null,
    onOpened: () -> Unit = {},
) {
    val actions = rememberExpenseActions(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var activitySection by rememberSaveable { mutableStateOf(ActivitySection.Expenses) }
    // Bumped to make Insights scroll to its budgets (from Home's chips or a nudge's "View").
    var focusBudgets by rememberSaveable { mutableIntStateOf(0) }

    val stacks: Map<Tab, NavBackStack<NavKey>> = Tab.entries.associateWith { rememberNavBackStack(it.root) }
    val current = stacks.getValue(tab)
    fun openSettings() {
        if (current.lastOrNull() != SettingsRoute) current.add(SettingsRoute)
    }
    fun select(target: Tab) {
        tab = target
    }

    val provider = entryProvider<NavKey> {
        entry<HomeRoute> {
            HomeScreen(
                viewModel = viewModel,
                actions = actions,
                onOpenSettings = ::openSettings,
                onSeeAll = {
                    activitySection = ActivitySection.Expenses
                    select(Tab.Activity)
                },
                onReview = {
                    activitySection = ActivitySection.Review
                    select(Tab.Activity)
                },
                onOpenInsights = { select(Tab.Insights) },
                onOpenBudgets = {
                    focusBudgets++
                    select(Tab.Insights)
                },
            )
        }
        entry<ActivityRoute> {
            ActivityScreen(viewModel, actions, activitySection, { activitySection = it }, ::openSettings)
        }
        entry<InsightsRoute> { InsightsScreen(viewModel, ::openSettings, focusBudgets) }
        entry<AskRoute> {
            val history by viewModel.askHistory.collectAsStateWithLifecycle()
            val asking by viewModel.asking.collectAsStateWithLifecycle()
            AskScreen(history, asking, onAsk = viewModel::ask)
        }
        entry<SettingsRoute> { SettingsRoute(viewModel, onBack = { current.removeLastOrNull() }) }
    }
    val entries = Tab.entries.associateWith { t ->
        rememberDecoratedNavEntries(stacks.getValue(t), listOf(rememberSaveableStateHolderNavEntryDecorator()), provider)
    }
    // Home sits under every other tab, so back (and predictive back) from a tab's root goes Home.
    val shown = if (tab == Tab.Home) entries.getValue(Tab.Home) else entries.getValue(Tab.Home) + entries.getValue(tab)
    val onSettings = current.lastOrNull() == SettingsRoute

    // Budget nudges ("You're at 85% of your Food budget…") as a snackbar, with a shortcut to the budgets.
    LaunchedEffect(viewModel) {
        viewModel.nudges.collect { message ->
            val result = actions.snackbar.showSnackbar(message, actionLabel = "View", withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                focusBudgets++
                select(Tab.Insights)
            }
        }
    }

    // A tapped reminder: straight to the review list, or to the add sheet.
    LaunchedEffect(openTarget) {
        when (openTarget) {
            ReminderTarget.REVIEW -> {
                activitySection = ActivitySection.Review
                select(Tab.Activity)
            }
            ReminderTarget.ADD -> {
                select(Tab.Home)
                actions.add()
            }
            null -> return@LaunchedEffect
        }
        if (current.lastOrNull() == SettingsRoute) current.removeLastOrNull()
        onOpened()
    }

    // Hide the bar while scrolling down a list, bring it back on scroll up (and on any tab change).
    val suiteState = rememberNavigationSuiteScaffoldState()
    val scope = rememberCoroutineScope()
    var chromeVisible by remember { mutableStateOf(true) }
    fun setChrome(visible: Boolean) {
        if (chromeVisible == visible) return
        chromeVisible = visible
        scope.launch { if (visible) suiteState.show() else suiteState.hide() }
    }
    LaunchedEffect(tab, onSettings) { setChrome(true) }
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -SCROLL_SLOP) setChrome(false) else if (available.y > SCROLL_SLOP) setChrome(true)
                return Offset.Zero
            }
        }
    }

    // Selected tab: a pale-green pill with a dark-green icon (Material's default pill is teal).
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
        ),
    )

    NavigationSuiteScaffold(
        state = suiteState,
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = t == tab,
                    onClick = { select(t) },
                    icon = {
                        val pending = state.pendingDetections.size
                        BadgedBox(badge = { if (t == Tab.Activity && pending > 0) Badge { Text("$pending") } }) {
                            Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null)
                        }
                    },
                    label = { Text(t.label) },
                    colors = itemColors,
                )
            }
        },
        layoutType = if (onSettings) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(hideOnScroll),
        ) {
            CompositionLocalProvider(LocalChromeVisible provides (chromeVisible || onSettings)) {
                NavDisplay(
                    entries = shown,
                    onBack = {
                        if (current.size > 1) current.removeLastOrNull() else if (tab != Tab.Home) select(Tab.Home)
                    },
                    // Material motion: tabs "fade through"; Settings slides in along the x axis and back out.
                    transitionSpec = { if (isSettings(targetState)) sharedAxisForward() else fadeThrough() },
                    popTransitionSpec = { if (isSettings(initialState)) sharedAxisBack() else fadeThrough() },
                )
            }
            SnackbarHost(
                actions.snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
    ExpenseActionsHost(actions, viewModel)
}

private const val SCROLL_SLOP = 6f

private fun isSettings(scene: Scene<NavKey>) = scene.entries.lastOrNull()?.contentKey == SettingsRoute.toString()

/** Outgoing fades quickly, incoming fades and scales up slightly after it: unrelated destinations. */
private fun fadeThrough(): ContentTransform =
    (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.94f)) togetherWith
        fadeOut(tween(90))

/** Forward along the x axis: a destination that belongs to where you came from. */
private fun sharedAxisForward(): ContentTransform =
    (slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(210, delayMillis = 90))) togetherWith
        (slideOutHorizontally(tween(300)) { -it / 6 } + fadeOut(tween(90)))

private fun sharedAxisBack(): ContentTransform =
    (slideInHorizontally(tween(300)) { -it / 6 } + fadeIn(tween(210, delayMillis = 90))) togetherWith
        (slideOutHorizontally(tween(300)) { it / 6 } + fadeOut(tween(90)))

@Composable
private fun SettingsRoute(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val insightsNotify by viewModel.insightsNotify.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshAiStatus() }
    SettingsScreen(
        selectedCurrency = state.currency,
        onCurrencySelected = viewModel::setCurrency,
        onBack = onBack,
        smsImportEnabled = state.smsImportEnabled,
        onSmsImportChange = viewModel::setSmsImportEnabled,
        ai = aiState,
        onAiEnabledChange = viewModel::setAiEnabled,
        onDownloadAi = viewModel::downloadAiModel,
        gemmaActions = GemmaActions(
            canDownload = GemmaModel.canDownload,
            onDownload = viewModel::downloadGemma,
            onImport = viewModel::importGemma,
            onCancel = viewModel::cancelGemma,
            onDelete = viewModel::deleteGemma,
        ),
        insightsNotify = insightsNotify,
        onInsightsNotifyChange = viewModel::setInsightsNotify,
        reminder = reminder,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onReminderTimeChange = viewModel::setReminderTime,
        handleSystemBack = false,
    )
}
