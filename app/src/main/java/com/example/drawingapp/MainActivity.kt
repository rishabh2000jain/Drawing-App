package com.example.drawingapp

import android.app.ActionBar.LayoutParams
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.drawingapp.databinding.ActivityMainBinding
import com.example.drawingapp.databinding.LayoutBrushSettingBinding
import com.google.android.material.snackbar.Snackbar
import com.skydoves.colorpickerview.listeners.ColorListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private val permissionResultLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions: Map<String, Boolean> ->
            permissions.forEach {
                if (it.value) {
                    if (it.key == android.Manifest.permission.READ_EXTERNAL_STORAGE) {
                        openImagePicker()
                    } else if (it.key == android.Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                        saveFileAsImage()
                    }
                } else {
                    Toast.makeText(this, "Permission Denied ", Toast.LENGTH_SHORT).show()
                }
            }

        }
    private val imageChooserResultLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let { setCanvasBackground(it) }
        }

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.undoImageBtn.setOnClickListener {
            binding.drawingView.undo()
        }
        binding.redoImageBtn.setOnClickListener {
            binding.drawingView.redo()
        }
        binding.clearBtn.setOnClickListener {
            binding.drawingBgImg.setImageURI(null)
            binding.drawingView.clear()
        }
        binding.saveBtn.setOnClickListener {
            saveFileAsImage()
        }
        binding.brushSettingBtn.setOnClickListener{
            showBrushSettingDialog()
        }
        binding.chooseImageBtn.setOnClickListener {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                showRationalDialog("Drawing App requires storage permission to access images from device")
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }

    }

    private fun showRationalDialog(message: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Require Permission")
        builder.setMessage(message)
        builder.setPositiveButton("Ok") { dialog, id ->
            print(id)
            dialog.dismiss()
        }
        builder.create().show()

    }

    private fun openImagePicker() {
        val intent: Intent = Intent(Intent.ACTION_PICK)
        imageChooserResultLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun setCanvasBackground(uri: Uri) {
        binding.drawingBgImg.setImageURI(uri)
    }

    private fun saveFileAsImage() {
        if (!isPermissionGranted(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        } else {
            val bitmap = binding.drawingView.bitmapFromView(binding.drawingViewFrameLayout)
            lifecycleScope.launch {
                saveBitmap(bitmap)
            }
        }
    }

    private suspend fun saveBitmap(bitmap: Bitmap?) {
        withContext(Dispatchers.IO) {
            try {
                if (bitmap != null) {
                    var filePath:String?=null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val imageValues = ContentValues().apply {
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            val date = System.currentTimeMillis() / 1000
                            put(MediaStore.Images.Media.DATE_ADDED, date)
                            put(MediaStore.Images.Media.DATE_MODIFIED, date)
                            val path = Environment.DIRECTORY_PICTURES
                            put(
                                MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis() / 1000
                            )
                            put(MediaStore.Images.Media.RELATIVE_PATH, path+File.separator+"DrawImages")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }

                        val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                        val imageUri = contentResolver.insert(collection, imageValues)

                        imageUri?.let { it ->
                            filePath = it.toString()
                            contentResolver.openOutputStream(it)?.use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
                                it.write(bitmap.rowBytes)
                                it.close()
                            }
                            imageValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(it, imageValues, null, null)
                        }
                    }
                    else {
                        val byteArray = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArray)
                        val file =
                            File(externalCacheDir?.absolutePath.toString() + File.separator + (System.currentTimeMillis() / 1000) + ".png")
                        filePath = file.absolutePath
                        val outputStream = FileOutputStream(file)
                        outputStream.write(byteArray.toByteArray())
                        outputStream.close()
                    }
                    runOnUiThread {
                        Snackbar.make(binding.root, "Image save ", Snackbar.LENGTH_SHORT).show()
                        shareSavedFile(absoluteFilePath = filePath!!)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Snackbar.make(binding.root, "Failed to save image", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestPermissions(permissionArray: Array<String>) {
        permissionResultLauncher.launch(permissionArray)
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showBrushSettingDialog(){
        var selectedBrushColor:Int? = null
        var selectedBrushSize:Int? = null
        val dialog = Dialog(this@MainActivity)
        val dialogBinding = LayoutBrushSettingBinding.inflate(layoutInflater,binding.root,false)
        dialog.setContentView(dialogBinding.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.window?.setLayout(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
        dialogBinding.brushSizeSeekbar.progress = binding.drawingView.getBrushSize().toInt()
        dialogBinding.brushSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let {
                    selectedBrushSize = it
                }

            }
        })
        dialogBinding.colorPickerView.setInitialColor(binding.drawingView.getBrushColor())
        dialogBinding.colorPickerView.attachBrightnessSlider(dialogBinding.brightnessSlideView)
        dialogBinding.colorPickerView.setColorListener(object : ColorListener{
            override fun onColorSelected(color: Int, fromUser: Boolean) {
                selectedBrushColor = color
            }
        })
        dialogBinding.saveBtn.setOnClickListener {
            binding.drawingView.setBrushSize(selectedBrushSize?.toFloat())
            binding.drawingView.setBrushColor(selectedBrushColor)
            dialog.dismiss()
        }
        dialogBinding.cancelBtn.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun shareSavedFile(absoluteFilePath:String){
            val intent:Intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(absoluteFilePath))
            intent.type = "image/png"
            startActivity(Intent.createChooser(intent,"Share Image"))
    }

}