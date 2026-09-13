package de.zwegen.zpaint

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ContentLoadingProgressBar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.dialog.ZaintHelpDialog
import de.zwegen.zpaint.dialog.ZaintMergeActiveLayersDialog
import de.zwegen.zpaint.dialog.ZaintOptionsDialog
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.ZaintCommandTimeline.CommandListener
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.implementation.ZaintHistoryDispatcher
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.command.implementation.ZaintHistoryManager
import de.zwegen.zpaint.command.implementation.ZaintLayerOpacityChange
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.common.DEFAULT_CANVAS_HEIGHT
import de.zwegen.zpaint.common.DEFAULT_CANVAS_WIDTH
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView
import de.zwegen.zpaint.controller.ZaintToolCoordinator
import de.zwegen.zpaint.listener.DrawerLayoutListener
import de.zwegen.zpaint.listener.PresenterColorPickedListener
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.model.ZaintEditorSession
import de.zwegen.zpaint.presenter.ZaintLayerPresenter
import de.zwegen.zpaint.presenter.ZaintEditorPresenter
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintShapeToolBase
import de.zwegen.zpaint.tools.implementation.BorderTool
import de.zwegen.zpaint.tools.implementation.ZaintToolContextAdapter
import de.zwegen.zpaint.tools.implementation.ZaintToolFactory
import de.zwegen.zpaint.tools.implementation.ZaintActiveToolReference
import de.zwegen.zpaint.tools.implementation.ZaintDrawingPaint
import de.zwegen.zpaint.tools.implementation.ZaintWorkspace
import de.zwegen.zpaint.tools.implementation.FilterTool
import de.zwegen.zpaint.tools.implementation.ZaintLineTool
import de.zwegen.zpaint.tools.implementation.ZaintFillTool
import de.zwegen.zpaint.tools.implementation.PerspectiveTool
import de.zwegen.zpaint.tools.implementation.PlaceTool
import de.zwegen.zpaint.tools.implementation.PixelTool
import de.zwegen.zpaint.tools.implementation.RotateTool
import de.zwegen.zpaint.tools.implementation.AlignTool
import de.zwegen.zpaint.tools.implementation.ZaintTransformTool
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.iotasks.BitmapSize
import de.zwegen.zpaint.iotasks.ZaintProjectFormat
import de.zwegen.zpaint.ui.ZaintDrawingSurface
import de.zwegen.zpaint.ui.FiveColumnToolbarOrganizer
import de.zwegen.zpaint.ui.KeyboardListener
import de.zwegen.zpaint.ui.ZaintLayerListAdapter
import de.zwegen.zpaint.ui.LayerNavigator
import de.zwegen.zpaint.ui.ZaintDocumentIoInteractor
import de.zwegen.zpaint.ui.ZaintEditorNavigator
import de.zwegen.zpaint.ui.ZaintEditorNavigationHost
import de.zwegen.zpaint.ui.Perspective
import de.zwegen.zpaint.ui.dragndrop.ZaintLayerDragList
import de.zwegen.zpaint.ui.tools.ZaintToolOptionsControllerHost
import de.zwegen.zpaint.ui.viewholder.BottomBarViewHolder
import de.zwegen.zpaint.ui.viewholder.BottomNavigationViewHolder
import de.zwegen.zpaint.ui.viewholder.DrawerLayoutViewHolder
import de.zwegen.zpaint.ui.viewholder.LayerMenuViewHolder
import de.zwegen.zpaint.ui.viewholder.TopBarViewHolder
import java.io.File
import java.util.Locale

private const val TEMP_IMAGE_COROUTINE_DELAY_MILLI_SEC = 1000
private const val MILLI_SEC_TO_SEC = 1000
private const val TEMP_IMAGE_SAVE_INTERVAL = 60
private const val TEMP_IMAGE_IDLE_INTERVAL = 2 * TEMP_IMAGE_COROUTINE_DELAY_MILLI_SEC

/** Main Android entry point for the Zaint editor screen. */
class ZaintEditorActivity : AppCompatActivity(), MainView, CommandListener, ColorChangeTarget,
    ZaintEditorNavigationHost {
    @VisibleForTesting
    lateinit var perspective: Perspective

    @VisibleForTesting
    lateinit var workspace: Workspace

    @VisibleForTesting
    lateinit var layerModel: ZaintLayerContracts.Model

    @VisibleForTesting
    lateinit var toolReference: ToolReference

    @VisibleForTesting
    lateinit var toolOptionsViewController: ZaintToolOptionsController

    @VisibleForTesting
    lateinit var layerAdapter: ZaintLayerListAdapter

    override var idlingResource: CountingIdlingResource = CountingIdlingResource("MainIdleResource")

    override lateinit var commandManager: ZaintCommandTimeline
    override lateinit var toolPaint: ToolPaint
    lateinit var bottomNavigationViewHolder: BottomNavigationViewHolder
    override lateinit var model: ZaintEditorContracts.Model

    private lateinit var commandSerializer: ZaintProjectSerializer
    private lateinit var layerPresenter: ZaintLayerPresenter
    private lateinit var drawingSurface: ZaintDrawingSurface
    private lateinit var presenterMain: ZaintEditorContracts.Presenter
    private lateinit var drawerLayoutViewHolder: DrawerLayoutViewHolder
    private lateinit var topBarViewHolder: TopBarViewHolder
    private lateinit var keyboardListener: KeyboardListener
    private lateinit var appFragment: ZPaintApplicationFragment
    lateinit var toolCoordinator: ZaintToolCoordinator
    private lateinit var commandFactory: ZaintCommandFactoryApi
    private lateinit var progressBar: ContentLoadingProgressBar

    @Volatile
    private var lastInteractionTime = System.currentTimeMillis()

    override fun runColorChange(block: () -> Unit) {
        runOnUiThread(block)
    }

    override fun updateColorIndicator(color: Int) {
        bottomNavigationViewHolder.setColorButtonColor(color)
    }

    override val activity: AppCompatActivity
        get() = this

    override fun selectPipetteColor() {
        toolCoordinator.selectPipetteColor()
    }

    @Volatile
    private var minuteTemporaryCopiesCounter = 0

    @Volatile
    private var userInteraction = false
    private var isTemporaryFileSavingTest = false
    private var autoSaveJob: Job? = null

    private val isRunningEspressoTests: Boolean by lazy {
        try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Application is not in test mode.")
            false
        }
    }

    companion object {
        const val TAG = "ZaintEditorActivity"
        private const val IS_SAVED_KEY = "isSaved"
        private const val SAVED_PICTURE_URI_KEY = "savedPictureUri"
        private const val CAMERA_IMAGE_URI_KEY = "cameraImageUri"
        private const val APP_FRAGMENT_KEY = "customActivityState"
    }

    override val presenter: ZaintEditorContracts.Presenter
        get() = presenterMain

    override val displayMetrics: DisplayMetrics
        get() = resources.displayMetrics

    override val visibleDrawingSurfaceSize: BitmapSize?
        get() = drawingSurface.takeIf { it.width > 0 && it.height > 0 }
            ?.let { BitmapSize(it.width, it.height) }

    override val isKeyboardShown: Boolean
        get() = keyboardListener.isSoftKeyboardVisible

    override val myContentResolver: ContentResolver
        get() = contentResolver

    override val finishing: Boolean
        get() = isFinishing

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.isStateSaved) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            } else if (!supportFragmentManager.popBackStackImmediate()) {
                presenterMain.onBackPressed()
            }
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun handleIntent(receivedIntent: Intent): Boolean {
        var receivedUri = receivedIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        receivedUri = receivedUri ?: receivedIntent.data

        receivedUri ?: return true
        try {
            if (ZaintProjectFormat.isZaintProject(receivedUri, myContentResolver)) {
                val fileContent = commandSerializer.readFromFile(receivedUri)
                commandManager.loadProjectHistory(fileContent.commandModel)
                presenterMain.setColorHistoryAfterLoadImage(fileContent.colorHistory)
                return false
            } else {
                ZaintDocumentStorage.filename = "image"
                ZaintDocumentStorage.getBitmapFromUri(myContentResolver, receivedUri)
                    ?.let { receivedBitmap ->
                        commandManager.setInitialStateCommand(
                            commandFactory.createInitCommand(
                                receivedBitmap
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            Log.e("Can not read", "Unable to retrieve Bitmap from Uri")
        }
        return true
    }

    private fun validateIntent(receivedIntent: Intent): Boolean {
        val receivedAction = receivedIntent.action
        val receivedType = receivedIntent.type
        val receivedUri = receivedIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: receivedIntent.data

        if (receivedAction == Intent.ACTION_SEND || receivedAction == Intent.ACTION_EDIT || receivedAction == Intent.ACTION_VIEW) {
            if (receivedUri != null && ZaintProjectFormat.isZaintProject(receivedUri, myContentResolver)) {
                return true
            }
        }

        return receivedAction == Intent.ACTION_EDIT || receivedAction != null && receivedType != null && (receivedAction == Intent.ACTION_SEND || receivedAction == Intent.ACTION_VIEW) && (
            receivedType.startsWith(
                "image/"
            ) || receivedType.startsWith("application/")
            )
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ZPaintTheme)
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        getAppFragment()
        ZPaintApplication.cacheDir = cacheDir
        setContentView(R.layout.activity_zpaint_main)
        applySystemBarColors()
        onCreateGlobals()
        onCreateMainView()
        onCreateLayerMenu()
        onCreateDrawingSurface()
        presenterMain.onCreateTool()
        val receivedIntent = intent
        isTemporaryFileSavingTest = intent.getBooleanExtra("isTemporaryFileSavingTest", false)
        when {
            validateIntent(receivedIntent) && savedInstanceState == null -> {
                if (handleIntent(receivedIntent)) {
                    commandManager.reset()
                }
                model.savedPictureUri = null
                model.cameraImageUri = null
                workspace.resetPerspective()
                presenterMain.initializeFromCleanState()
            }
            savedInstanceState == null -> {
                presenterMain.initializeFromCleanState()

                if (presenterMain.checkForTemporaryFile() && (!isRunningEspressoTests || isTemporaryFileSavingTest)) {
                    val workspaceReturnValue = presenterMain.openTemporaryFile()
                    presenterMain.resetPerspectiveAfterNextCommand()
                    commandManager.loadProjectHistory(workspaceReturnValue?.commandManagerModel)
                    model.colorHistory = workspaceReturnValue?.colorHistory ?: ColorHistory()
                    model.colorHistory.colors.lastOrNull()?.let {
                        toolReference.tool?.changePaintColor(it)
                        presenterMain.setBottomNavigationColor(it)
                    }
                }
                workspace.perspective.setBitmapDimensions(layerModel.width, layerModel.height)
            }
            else -> {
                val isSaved = savedInstanceState.getBoolean(IS_SAVED_KEY, false)
                val savedPictureUri = savedInstanceState.getParcelable<Uri>(SAVED_PICTURE_URI_KEY)
                val cameraImageUri = savedInstanceState.getParcelable<Uri>(CAMERA_IMAGE_URI_KEY)
                presenterMain.restoreState(
                    isSaved, savedPictureUri, cameraImageUri
                )
            }
        }

        commandManager.addCommandListener(this)
        lastInteractionTime = System.currentTimeMillis()
        if (!isRunningEspressoTests || isTemporaryFileSavingTest) {
            startAutoSaveCoroutine()
        }
        presenterMain.finishInitialize()

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            presenterMain.saveNewTemporaryImage()
            minuteTemporaryCopiesCounter = 0
            userInteraction = false
        }
        updateSystemUiVisibility()
        applySystemBarColors()
    }

    private fun updateSystemUiVisibility() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.statusBars())
    }

    private fun applySystemBarColors() {
        val systemBarColor = ContextCompat.getColor(this, R.color.zpaint_colorPrimaryDark)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.zpaint_options_save_as -> presenterMain.saveAsClicked()
            R.id.zpaint_options_open_image -> presenterMain.replaceImageClicked()
            R.id.zpaint_options_new_image -> presenterMain.newImageClicked()
            R.id.zpaint_options_quit -> presenterMain.exitClicked()
            R.id.zpaint_share_image_button -> presenterMain.shareImageClicked()
            android.R.id.home -> presenterMain.exitClicked()
            else -> return false
        }
        return true
    }

    private fun getAppFragment() {
        supportFragmentManager.findFragmentByTag(APP_FRAGMENT_KEY)?.let { fragment ->
            appFragment = fragment as ZPaintApplicationFragment
        }
        if (!this::appFragment.isInitialized) {
            appFragment = ZPaintApplicationFragment()
            supportFragmentManager.beginTransaction().add(appFragment, APP_FRAGMENT_KEY).commit()
        }
    }

    private fun onCreateGlobals() {
        val currentLayerModel = appFragment.layerModel ?: ZaintLayerModel()
        appFragment.layerModel = currentLayerModel
        layerModel = currentLayerModel

        commandFactory = ZaintCommandFactory()

        val currentCommandManager = appFragment.commandManager
        if (currentCommandManager == null) {
            val synchronousCommandManager: ZaintCommandTimeline =
                ZaintHistoryManager(CommonFactory(), layerModel)
            commandManager = ZaintHistoryDispatcher(synchronousCommandManager, layerModel)
            val initCommand =
                commandFactory.createInitCommand(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)
            commandManager.setInitialStateCommand(initCommand)
            commandManager.reset()
            appFragment.commandManager = commandManager
        } else {
            commandManager = currentCommandManager
        }

        val currentToolPaint = appFragment.toolPaint ?: ZaintDrawingPaint(applicationContext)
        appFragment.toolPaint = currentToolPaint
        toolPaint = currentToolPaint

        val currentTool = appFragment.currentTool ?: ZaintActiveToolReference()
        appFragment.currentTool = currentTool
        toolReference = currentTool
    }

    private fun onCreateMainView() {
        val context: Context = this
        FiveColumnToolbarOrganizer.organize(findViewById(R.id.zpaint_tools_layout))
        FiveColumnToolbarOrganizer.organize(findViewById(R.id.zpaint_filters_layout))
        val drawerLayout = findViewById<DrawerLayout>(R.id.zpaint_drawer_layout)
        val topBarLayout = findViewById<ViewGroup>(R.id.zpaint_layout_top_bar)
        val bottomBarLayout = findViewById<View>(R.id.zpaint_main_bottom_bar)
        val filterBarLayout = findViewById<View>(R.id.zpaint_main_filter_bar)
        val bottomNavigationView = findViewById<View>(R.id.zpaint_main_bottom_navigation)
        toolOptionsViewController = ZaintToolOptionsControllerHost(this, idlingResource)
        drawerLayoutViewHolder = DrawerLayoutViewHolder(drawerLayout)
        topBarViewHolder = TopBarViewHolder(topBarLayout)
        val bottomBarViewHolder = BottomBarViewHolder(bottomBarLayout)
        val filterBarViewHolder = BottomBarViewHolder(filterBarLayout)
        bottomNavigationViewHolder = BottomNavigationViewHolder(bottomNavigationView)
        perspective = Perspective(layerModel.width, layerModel.height)
        workspace = ZaintWorkspace(
            layerModel,
            perspective,
            ::refreshDrawingSurfaceWhenReady,
        )
        model = ZaintEditorSession()
        commandSerializer = ZaintProjectSerializer(this, commandManager, model)
        toolCoordinator = ZaintToolCoordinator(
            toolReference,
            toolOptionsViewController,
            ZaintToolFactory(
                bottomNavigationViewHolder,
                onPipetteSelected = { toolCoordinator.selectPipetteColor() },
                onLineSelected = { presenterMain.toolClicked(ZaintToolKind.LINE) },
                onBrushPresetSelected = { preset -> presenterMain.brushPresetClicked(preset) },
                onFillColorPickerRequested = { presenterMain.showColorPickerClicked() },
                onFillImagePickerRequested = { presenterMain.selectFillImageClicked() },
                colorChangeTarget = this,
                addToColorHistory = { color -> model.colorHistory.addColor(color) }
            ),
            commandManager,
            workspace,
            idlingResource,
            toolPaint,
            ZaintToolContextAdapter(context)
        )
        val preferences = ZaintSettings(getPreferences(MODE_PRIVATE))
        val navigator = ZaintEditorNavigator(this, toolReference)
        presenterMain = ZaintEditorPresenter(
            this,
            this,
            model,
            workspace,
            navigator,
            ZaintDocumentIoInteractor(idlingResource),
            topBarViewHolder,
            bottomBarViewHolder,
            filterBarViewHolder,
            drawerLayoutViewHolder,
            bottomNavigationViewHolder,
            ZaintCommandFactory(),
            commandManager,
            toolCoordinator,
            preferences,
            idlingResource,
            context,
            filesDir,
            commandSerializer
        )
        ZaintDocumentStorage.navigator = navigator
        toolCoordinator.setOnColorPickedListener(PresenterColorPickedListener(presenterMain))
        keyboardListener = KeyboardListener(drawerLayout)
        setTopBarListeners(topBarViewHolder)
        setBottomBarListeners(bottomBarViewHolder)
        setFilterBarListeners(filterBarViewHolder)
        setBottomNavigationListeners(bottomNavigationViewHolder)
        setActionBarToolTips(topBarViewHolder, context)
        progressBar = findViewById(R.id.zpaint_content_loading_progress_bar)
    }

    private fun onCreateLayerMenu() {
        setLayoutDirection()
        val layerLayout = findViewById<NavigationView>(R.id.zpaint_nav_view_layer)
        val drawerLayout = findViewById<DrawerLayout>(R.id.zpaint_drawer_layout)
        val layerListView = findViewById<ZaintLayerDragList>(R.id.zpaint_layer_side_nav_list)
        val layerMenuViewHolder = LayerMenuViewHolder(layerLayout)
        val layerNavigator = LayerNavigator(applicationContext)
        layerPresenter = ZaintLayerPresenter(
            layerModel, layerListView, layerMenuViewHolder,
            commandManager, ZaintCommandFactory(), layerNavigator
        )
        val layoutManager = LinearLayoutManager(applicationContext, RecyclerView.VERTICAL, false)
        layerListView.layoutManager = layoutManager
        layerListView.manager = layoutManager
        layerAdapter = ZaintLayerListAdapter(layerPresenter, this)
        layerListView.setLayerAdapter(layerAdapter)
        presenterMain.setLayerAdapter(layerAdapter)
        layerPresenter.setAdapter(layerAdapter)
        layerListView.setCoordinator(layerPresenter)
        layerListView.adapter = layerAdapter
        layerPresenter.refreshLayerMenuViewHolder()
        layerPresenter.disableVisibilityAndOpacityButtons()
        setLayerMenuListeners(layerMenuViewHolder)
        val drawerLayoutListener = DrawerLayoutListener(this, layerPresenter)
        drawerLayout.addDrawerListener(drawerLayoutListener)
    }

    private fun setLayoutDirection() {
        var visibilityBtn = findViewById<ImageButton>(R.id.zpaint_layer_side_nav_button_visibility)
        var layerNavigationView = findViewById<NavigationView>(R.id.zpaint_nav_view_layer)
        if (ZaintLayoutDirection.usesRightToLeftLayout()) {
            visibilityBtn.setBackgroundResource(R.drawable.rounded_corner_top_rtl)
            layerNavigationView.setBackgroundResource(R.drawable.layer_nav_view_background_rtl)
        } else {
            visibilityBtn.setBackgroundResource(R.drawable.rounded_corner_top_ltr)
            layerNavigationView.setBackgroundResource(R.drawable.layer_nav_view_background_ltr)
        }
    }

    private fun onCreateDrawingSurface() {
        drawingSurface = findViewById(R.id.zpaint_drawing_surface_view)
        drawingSurface.setArguments(
            layerModel,
            perspective,
            toolReference,
            idlingResource,
            supportFragmentManager,
            toolOptionsViewController,
            drawerLayoutViewHolder
        )
        layerPresenter.setDrawingSurface(drawingSurface)
        appFragment.perspective = perspective
        layerPresenter.setToolCoordinator(toolCoordinator)
        layerPresenter.setBottomNavigationViewHolder(bottomNavigationViewHolder)
    }

    private fun refreshDrawingSurfaceWhenReady() {
        if (this::drawingSurface.isInitialized) {
            drawingSurface.refreshDrawingSurface()
        }
    }

    private fun setLayerMenuListeners(layerMenuViewHolder: LayerMenuViewHolder) {
        layerMenuViewHolder.layerAddButton.setOnClickListener { layerPresenter.addLayer() }
        layerMenuViewHolder.layerDuplicateButton.setOnClickListener { layerPresenter.duplicateLayer() }
        layerMenuViewHolder.layerDeleteButton.setOnClickListener { layerPresenter.removeLayer() }
        layerMenuViewHolder.layerMergeButton.setOnClickListener { showMergeActiveLayersDialog() }
    }

    fun mergeActiveLayers() {
        layerPresenter.mergeActiveLayers()
    }

    private fun showMergeActiveLayersDialog() {
        if (supportFragmentManager.findFragmentByTag(ZaintMergeActiveLayersDialog.TAG) == null) {
            ZaintMergeActiveLayersDialog().show(
                supportFragmentManager,
                ZaintMergeActiveLayersDialog.TAG
            )
        }
    }

    private fun setActionBarToolTips(topBar: TopBarViewHolder, context: Context) {
        TooltipCompat.setTooltipText(topBar.undoButton, context.getString(R.string.button_undo))
        TooltipCompat.setTooltipText(topBar.helpButton, context.getString(R.string.button_help))
        TooltipCompat.setTooltipText(topBar.redoButton, context.getString(R.string.button_redo))
    }

    private fun mirrorUndoAndRedoButtonsForRtlLanguage() {
        val undoButton: ImageButton = findViewById(R.id.zpaint_btn_top_undo)
        val undoDrawable = ContextCompat.getDrawable(this, R.drawable.ic_zpaint_undo)

        val redoButton: ImageButton = findViewById(R.id.zpaint_btn_top_redo)
        val redoDrawable = ContextCompat.getDrawable(this, R.drawable.ic_zpaint_redo)

        undoDrawable?.let {
            it.isAutoMirrored = true
            undoButton.setImageDrawable(it)
        }

        redoDrawable?.let {
            it.isAutoMirrored = true
            redoButton.setImageDrawable(it)
        }
    }

    private fun setTopBarListeners(topBar: TopBarViewHolder) {
        topBar.undoButton.setOnClickListener { presenterMain.undoClicked() }
        topBar.helpButton.setOnClickListener { showCurrentToolHelp() }
        topBar.redoButton.setOnClickListener { presenterMain.redoClicked() }
        topBar.menuButton.setOnClickListener { showMoreOptionsPopup(it) }

        if (ZaintLayoutDirection.usesRightToLeftLayout()) {
            mirrorUndoAndRedoButtonsForRtlLanguage()
        }

        topBar.checkmarkButton.setOnClickListener {
            if (topBar.checkmarkProgress.visibility == View.VISIBLE) {
                return@setOnClickListener
            }
            idlingResource.increment()
            if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.TRANSFORM.name)) {
                (toolReference.tool as ZaintTransformTool).checkMarkClicked = true
                val tool = toolReference.tool as ZaintShapeToolBase?
                tool?.onClickOnButton()
            } else if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.BORDER.name)) {
                val tool = toolReference.tool as BorderTool?
                tool?.commitPendingBorder()
            } else if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.PERSPECTIVE.name)) {
                val tool = toolReference.tool as PerspectiveTool?
                tool?.applyPerspective()
            } else if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.PLACE.name)) {
                val tool = toolReference.tool as PlaceTool?
                tool?.applyPlacement()
            } else if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.ROTATE.name)) {
                val tool = toolReference.tool as RotateTool?
                if (tool?.commitPendingRotation() == true) {
                    showCheckmarkProgress()
                }
            } else if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.ALIGN.name)) {
                val tool = toolReference.tool as AlignTool?
                if (tool?.applyAlignment() == true) {
                    showCheckmarkProgress()
                }
            } else if (toolReference.tool?.toolType?.name.equals(ZaintToolKind.PIXEL.name)) {
                val tool = toolReference.tool as PixelTool?
                tool?.applyPixel()
                if (tool != null) {
                    showCheckmarkProgress()
                }
            } else if (toolReference.tool is ZaintFillTool) {
                (toolReference.tool as ZaintFillTool).applyImageFill()
            } else if (toolReference.tool is FilterTool) {
                val tool = toolReference.tool as FilterTool?
                if (tool?.commitPendingFilter() == true) {
                    showCheckmarkProgress()
                }
            } else {
                val tool = toolReference.tool as ZaintShapeToolBase?
                tool?.onClickOnButton()
            }
            idlingResource.decrement()
        }
        topBar.layerPlusButton.setOnClickListener {
            (toolReference.tool as? ZaintShapeToolBase)?.onClickOnNewLayerButton()
        }
        topBar.plusButton.setOnClickListener {
            val tool = toolReference.tool as ZaintLineTool
            tool.onClickOnPlus()
        }
        ZaintLineTool.topBarViewHolder = topBar
    }

    private fun showCurrentToolHelp() {
        val toolType = toolReference.tool?.toolType ?: return
        ZaintHelpDialog.newInstance(toolType.nameResource, toolType.helpTextResource)
            .show(supportFragmentManager, ZaintHelpDialog.TAG)
    }

    private fun showCheckmarkProgress() {
        topBarViewHolder.checkmarkButton.visibility = View.GONE
        topBarViewHolder.checkmarkButton.isEnabled = false
        topBarViewHolder.checkmarkProgress.visibility = View.VISIBLE
    }

    private fun hideCheckmarkProgress() {
        if (!this::topBarViewHolder.isInitialized ||
            topBarViewHolder.checkmarkProgress.visibility != View.VISIBLE
        ) {
            return
        }
        topBarViewHolder.checkmarkProgress.visibility = View.GONE
        topBarViewHolder.checkmarkButton.isEnabled = toolUsesCheckmarkProgress()
        topBarViewHolder.checkmarkButton.visibility = View.VISIBLE
    }

    private fun toolUsesCheckmarkProgress(): Boolean =
        toolReference.tool is FilterTool ||
            toolReference.tool is PixelTool ||
            toolReference.tool is RotateTool ||
            toolReference.tool is AlignTool

    private fun showMoreOptionsPopup(anchor: View) {
        val popupParent = findViewById<ViewGroup>(android.R.id.content)
        val content = LayoutInflater.from(this).inflate(R.layout.zaint_more_menu_popup, popupParent, false)
        val popupWidth = resources.getDimensionPixelSize(R.dimen.zaint_more_menu_width)
        val popup = PopupWindow(content, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = resources.getDimensionPixelSize(R.dimen.zaint_more_menu_vertical_offset).toFloat()
            animationStyle = R.style.ZaintMoreMenuAnimation
        }

        content.findViewById<View>(R.id.zaint_more_menu_new).setOnClickListener {
            popup.dismiss()
            presenterMain.newImageClicked()
        }
        content.findViewById<View>(R.id.zaint_more_menu_open).setOnClickListener {
            popup.dismiss()
            presenterMain.replaceImageClicked()
        }
        content.findViewById<View>(R.id.zaint_more_menu_save_as).setOnClickListener {
            popup.dismiss()
            presenterMain.saveAsClicked()
        }
        content.findViewById<View>(R.id.zaint_more_menu_share).setOnClickListener {
            popup.dismiss()
            presenterMain.shareImageClicked()
        }
        content.findViewById<View>(R.id.zaint_more_menu_options).setOnClickListener {
            popup.dismiss()
            ZaintOptionsDialog().show(supportFragmentManager, ZaintOptionsDialog.TAG)
        }
        content.findViewById<View>(R.id.zaint_more_menu_info).setOnClickListener {
            popup.dismiss()
            ZaintHelpDialog.newInstance(
                R.string.info_title_zaint,
                R.string.info_content_zaint,
                showVersion = true
            )
                .show(supportFragmentManager, ZaintHelpDialog.TAG)
        }
        content.findViewById<View>(R.id.zaint_more_menu_quit).setOnClickListener {
            popup.dismiss()
            presenterMain.exitClicked()
        }

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.zaint_more_menu_horizontal_margin)
        val yOffset = resources.getDimensionPixelSize(R.dimen.zaint_more_menu_vertical_offset)
        val anchorPosition = IntArray(2)
        anchor.getLocationInWindow(anchorPosition)
        popup.showAtLocation(
            popupParent,
            Gravity.TOP or Gravity.START,
            horizontalMargin,
            anchorPosition[1] + anchor.height + yOffset
        )
    }

    private fun setBottomBarListeners(viewHolder: BottomBarViewHolder) {
        val toolTypes = ZaintToolKind.values()
        for (type in toolTypes) {
            val toolButton = viewHolder.findButton(type.toolButtonID) ?: continue
            toolButton.setOnClickListener { presenterMain.toolClicked(type) }
        }
    }

    private fun setFilterBarListeners(viewHolder: BottomBarViewHolder) {
        val toolTypes = ZaintToolKind.values()
        for (type in toolTypes) {
            val toolButton = viewHolder.findButton(type.toolButtonID) ?: continue
            toolButton.setOnClickListener { presenterMain.toolClicked(type) }
        }
    }

    private fun setBottomNavigationListeners(viewHolder: BottomNavigationViewHolder) {
        viewHolder.bottomNavigationView.apply {
            setOnItemSelectedListener(::handleBottomNavigationItem)
            setOnItemReselectedListener(::handleBottomNavigationItem)
        }
    }

    private fun handleBottomNavigationItem(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_tools -> presenterMain.actionToolsClicked()
            R.id.action_current_tool -> presenterMain.actionCurrentToolClicked()
            R.id.action_color_picker -> presenterMain.showColorPickerClicked()
            R.id.action_filters -> presenterMain.actionFiltersClicked()
            R.id.action_layers -> presenterMain.showLayerMenuClicked()
            else -> return false
        }
        return true
    }

    override fun initializeActionBar() {
        val toolbar = findViewById<Toolbar>(R.id.zpaint_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false)
            setDisplayShowHomeEnabled(false)
        }
    }

    override fun commandPostExecute() {
        if (!finishing) {
            hideCheckmarkProgress()
            if (commandManager.lastExecutedCommand !is ZaintLayerOpacityChange) {
                layerPresenter.invalidate()
            }
            presenterMain.onCommandPostExecute()
        }
    }

    override fun onDestroy() {
        stopAutoSaveCoroutine()
        commandManager.removeCommandListener(this)
        if (finishing) {
            presenterMain.deleteTemporaryImage()
            commandManager.shutdown()
            appFragment.currentTool = null
            appFragment.commandManager = null
            appFragment.layerModel = null
        } else {
            presenterMain.saveNewTemporaryImage()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        with(outState) {
            putBoolean(IS_SAVED_KEY, model.isSaved)
            putParcelable(SAVED_PICTURE_URI_KEY, model.savedPictureUri)
            putParcelable(CAMERA_IMAGE_URI_KEY, model.cameraImageUri)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        presenterMain.handleActivityResult(requestCode, resultCode, data)
    }

    override fun superHandleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        presenterMain.handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun superHandleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun refreshDrawingSurface() {
        drawingSurface.refreshDrawingSurface()
    }

    override fun getUriFromFile(file: File): Uri = Uri.fromFile(file)

    override fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        if (inputMethodManager != null) {
            val rootView = window.decorView.rootView
            inputMethodManager.hideSoftInputFromWindow(rootView.windowToken, 0)
        }
    }

    override fun showContentLoadingProgressBar() {
        progressBar.show()
    }

    override fun hideContentLoadingProgressBar() {
        progressBar.hide()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionTime = System.currentTimeMillis()
        userInteraction = true
    }

    @Synchronized
    private fun addToMinuteTemporaryCopiesCounter(seconds: Int) {
        this.minuteTemporaryCopiesCounter += seconds
    }

    private fun startAutoSaveCoroutine() {
        stopAutoSaveCoroutine()
        autoSaveJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(TEMP_IMAGE_COROUTINE_DELAY_MILLI_SEC.toLong())
                addToMinuteTemporaryCopiesCounter(TEMP_IMAGE_COROUTINE_DELAY_MILLI_SEC / MILLI_SEC_TO_SEC)
                if ((System.currentTimeMillis() - lastInteractionTime >= TEMP_IMAGE_IDLE_INTERVAL || minuteTemporaryCopiesCounter >= TEMP_IMAGE_SAVE_INTERVAL) && userInteraction) {
                    presenterMain.saveNewTemporaryImage()
                    minuteTemporaryCopiesCounter = 0
                    userInteraction = false
                }
            }
        }
    }

    private fun stopAutoSaveCoroutine() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun getVersionCode(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrDefault("")
}
