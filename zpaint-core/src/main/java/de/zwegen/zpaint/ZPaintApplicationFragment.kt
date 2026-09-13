package de.zwegen.zpaint

import android.os.Bundle
import androidx.fragment.app.Fragment
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.ui.Perspective

class ZPaintApplicationFragment : Fragment() {
    var commandManager: ZaintCommandTimeline? = null
    var currentTool: ToolReference? = null
    var perspective: Perspective? = null
    var layerModel: ZaintLayerContracts.Model? = null
    var toolPaint: ToolPaint? = null
    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)
        retainInstance = true
    }
}
