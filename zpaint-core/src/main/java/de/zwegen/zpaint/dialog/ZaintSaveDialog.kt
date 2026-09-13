package de.zwegen.zpaint.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.ZaintDocumentStorage.FileType
import de.zwegen.zpaint.ZaintDocumentStorage.FileType.JPG
import de.zwegen.zpaint.ZaintDocumentStorage.FileType.PNG
import de.zwegen.zpaint.ZaintDocumentStorage.FileType.ZAINT
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView

/**
 * Zaint's save configuration screen. It keeps the user's file target, export
 * format and metadata choice together before starting the existing save flow.
 */
class ZaintSaveDialog : AppCompatDialogFragment(), SeekBar.OnSeekBarChangeListener {
    private lateinit var layoutInflater: LayoutInflater
    private lateinit var formatChoices: RadioGroup
    private lateinit var formatOptions: ViewGroup
    private lateinit var jpegOptions: View
    private lateinit var qualityLabel: AppCompatTextView
    private lateinit var filenameInput: AppCompatEditText
    private lateinit var metadataChoice: CheckBox
    private lateinit var request: SaveRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = SaveRequest.from(requireArguments())
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        layoutInflater = requireActivity().layoutInflater
        val content = layoutInflater.inflate(R.layout.dialog_zpaint_save, null)
        bindContent(content)

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun bindContent(content: View) {
        formatOptions = content.findViewById(R.id.zpaint_save_format_specific_options)
        jpegOptions = layoutInflater.inflate(
            R.layout.dialog_zpaint_save_jpg_sub_dialog,
            formatOptions,
            false
        )
        qualityLabel = jpegOptions.findViewById(R.id.zpaint_percentage_save_info)
        jpegOptions.findViewById<SeekBar>(R.id.zpaint_jpg_seekbar_save_info).apply {
            progress = ZaintDocumentStorage.compressQuality
            setOnSeekBarChangeListener(this@ZaintSaveDialog)
        }
        qualityLabel.text = getQualityLabel(ZaintDocumentStorage.compressQuality)

        metadataChoice = content.findViewById(R.id.zpaint_remove_metadata_checkbox)
        metadataChoice.isChecked = ZaintDocumentStorage.removeMetadata
        configureFilename(content)
        configureFormatSelection(content)
        configureActions(content)
    }

    private fun configureFilename(content: View) {
        val displayName = SaveDialogFileName(
            sourceFolderName = ZaintDocumentStorage.sourceFolderName,
            editableFilename = request.initialFilename,
            hasConcreteTarget = request.saveLocationUri != null
        )
        content.findViewById<AppCompatTextView>(R.id.zpaint_save_folder_prefix).text = displayName.folderPrefix
        filenameInput = content.findViewById(R.id.zpaint_image_name_save_text)
        filenameInput.setText(displayName.editableFilename)
    }

    private fun configureFormatSelection(content: View) {
        formatChoices = content.findViewById(R.id.zpaint_save_format_group)
        formatChoices.setOnCheckedChangeListener { _, checkedId ->
            applyFormat(
                when (checkedId) {
                    R.id.zpaint_save_format_jpg -> JPG
                    R.id.zpaint_save_format_zaint -> ZAINT
                    else -> PNG
                }
            )
        }
        formatChoices.check(
            when (ZaintDocumentStorage.fileType) {
                JPG -> R.id.zpaint_save_format_jpg
                ZAINT -> R.id.zpaint_save_format_zaint
                PNG -> R.id.zpaint_save_format_png
            }
        )
    }

    private fun configureActions(content: View) {
        content.findViewById<AppCompatImageButton>(R.id.zpaint_btn_save_info).setOnClickListener {
            when (ZaintDocumentStorage.fileType) {
                JPG -> presenter.showJpgInformationDialog()
                PNG -> presenter.showPngInformationDialog()
                ZAINT -> Unit
            }
        }
        content.findViewById<Button>(R.id.zpaint_save_close_button).setOnClickListener { dismiss() }
        content.findViewById<Button>(R.id.zpaint_save_in_button).setOnClickListener {
            keepSaveChoices()
            presenter.selectSaveFolderClicked(request.permissionCode, request.isExport)
            dismiss()
        }
        content.findViewById<Button>(R.id.zpaint_save_confirm_button).setOnClickListener {
            saveInCurrentFolder()
        }
    }

    private fun applyFormat(fileType: FileType) {
        formatOptions.removeAllViews()
        if (fileType == JPG) {
            formatOptions.addView(jpegOptions)
        }
        metadataChoice.visibility = if (fileType == ZAINT) View.GONE else View.VISIBLE
        ZaintDocumentStorage.fileType = fileType
        ZaintDocumentStorage.compressFormat = if (fileType == JPG) {
            Bitmap.CompressFormat.JPEG
        } else {
            Bitmap.CompressFormat.PNG
        }
    }

    private fun keepSaveChoices() {
        SaveDialogFileName(null, filenameInput.text.toString(), false).applyToFileIo()
        ZaintDocumentStorage.removeMetadata = metadataChoice.isChecked
        ZaintDocumentStorage.storeImageUri = null
    }

    private fun saveInCurrentFolder() {
        keepSaveChoices()
        val existingUri = request.overwriteTargetFor(
            ZaintDocumentStorage.defaultFileName,
            requireContext().contentResolver
        )
        if (existingUri != null) {
            ZaintDocumentStorage.storeImageUri = existingUri
            presenter.showOverwriteDialog(request.permissionCode, request.isExport)
        } else {
            presenter.switchBetweenVersions(request.permissionCode, request.isExport)
            dismiss()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        qualityLabel.text = getQualityLabel(progress)
        ZaintDocumentStorage.compressQuality = progress
    }

    private fun getQualityLabel(quality: Int): String =
        getString(R.string.dialog_save_jpg_quality_percentage, quality)

    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit

    private val presenter
        get() = (requireActivity() as MainView).presenter

    private data class SaveRequest(
        val permissionCode: Int,
        val initialFilename: String,
        val isExport: Boolean,
        val saveLocationUri: Uri?,
        val sourceFilename: String?
    ) {
        fun toBundle(): Bundle = bundleOf(
            ARGUMENT_PERMISSION_CODE to permissionCode,
            ARGUMENT_INITIAL_FILENAME to initialFilename,
            ARGUMENT_IS_EXPORT to isExport,
            ARGUMENT_SAVE_LOCATION_URI to saveLocationUri,
            ARGUMENT_SOURCE_FILENAME to sourceFilename
        )

        fun overwriteTargetFor(filename: String, resolver: android.content.ContentResolver): Uri? =
            if (filename == sourceFilename) {
                saveLocationUri
            } else {
                ZaintDocumentStorage.getUriForFilenameInCurrentSaveFolder(filename, resolver)
            }

        companion object {
            fun from(arguments: Bundle): SaveRequest = SaveRequest(
                permissionCode = arguments.getInt(ARGUMENT_PERMISSION_CODE),
                initialFilename = arguments.getString(ARGUMENT_INITIAL_FILENAME).orEmpty(),
                isExport = arguments.getBoolean(ARGUMENT_IS_EXPORT),
                saveLocationUri = arguments.getParcelable(ARGUMENT_SAVE_LOCATION_URI),
                sourceFilename = arguments.getString(ARGUMENT_SOURCE_FILENAME)
            )
        }
    }

    companion object {
        private const val DEFAULT_FILENAME = "image"
        private const val ARGUMENT_PERMISSION_CODE = "zaint.save.permission_code"
        private const val ARGUMENT_INITIAL_FILENAME = "zaint.save.initial_filename"
        private const val ARGUMENT_IS_EXPORT = "zaint.save.is_export"
        private const val ARGUMENT_SAVE_LOCATION_URI = "zaint.save.location_uri"
        private const val ARGUMENT_SOURCE_FILENAME = "zaint.save.source_filename"

        fun forSave(
            permissionCode: Int,
            imageNumber: Int,
            useDefaultImageName: Boolean,
            isExport: Boolean,
            saveLocationUri: Uri?
        ): ZaintSaveDialog {
            if (useDefaultImageName) {
                ZaintDocumentStorage.filename = DEFAULT_FILENAME
                ZaintDocumentStorage.fileType = PNG
                ZaintDocumentStorage.compressFormat = Bitmap.CompressFormat.PNG
            }
            val initialFilename = if (ZaintDocumentStorage.filename == DEFAULT_FILENAME) {
                "$DEFAULT_FILENAME$imageNumber"
            } else {
                ZaintDocumentStorage.filename.orEmpty()
            }
            return ZaintSaveDialog().apply {
                arguments = SaveRequest(
                    permissionCode,
                    initialFilename,
                    isExport,
                    saveLocationUri,
                    saveLocationUri?.let { ZaintDocumentStorage.defaultFileName }
                ).toBundle()
            }
        }
    }
}
