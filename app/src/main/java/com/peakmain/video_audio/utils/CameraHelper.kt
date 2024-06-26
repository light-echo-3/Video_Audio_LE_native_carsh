package com.peakmain.video_audio.utils

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.peakmain.ui.utils.LogUtils
import com.peakmain.ui.utils.ToastUtils
import com.peakmain.video_audio.simple.BasicSurfaceHolderCallback
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * author ：Peakmain
 * createTime：2021/9/1
 * mail:2726449200@qq.com
 * describe：Camera工具类
 */
class CameraHelper(surfaceView: SurfaceView) : Camera.PreviewCallback {
    private lateinit var mCamera: Camera
    private lateinit var size: Camera.Size
    private lateinit var mBuffer: ByteArray
    private lateinit var nv21_rotated: ByteArray
    private lateinit var nv12: ByteArray

    private var mCameraListener: ((ByteArray?, size: Camera.Size) -> Unit)? = null
    private lateinit var mediaCodec: MediaCodec

    init {
        surfaceView.holder.addCallback(object : BasicSurfaceHolderCallback() {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startPreview(holder)
            }
        })
    }

    private fun initMediaCodec() {
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                size.height, size.width
            )
            LogUtils.e(
                "initCodec:   width: " + size.height + "  width:  " + size.width
            )
            //设置帧率
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) //2s一个I帧
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startPreview(holder: SurfaceHolder?) {
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        val parameters = mCamera.parameters
        size = parameters.previewSize
        initMediaCodec()
        mCamera.setPreviewDisplay(holder)
        mCamera.setDisplayOrientation(90)
        mBuffer = ByteArray(size.width * size.height * 3 / 2)
        nv21_rotated = ByteArray(size.width * size.height * 3 / 2)
        nv12 = ByteArray(size.width * size.height * 3 / 2)
        mCamera.addCallbackBuffer(mBuffer)
        mCamera.setPreviewCallbackWithBuffer(this)
        mCamera.startPreview()

    }

    private var isCaptrue = false
    fun start() {
        isCaptrue = !isCaptrue
        ToastUtils.showShort(if (isCaptrue) "开始录屏" else "停止录屏")
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (isCaptrue) {
            nv12 = FileUtils.nv21toNV12(data)
            //旋转九十度
            FileUtils.portraitData2Raw(
                nv12,
                nv21_rotated,
                size.width,
                size.height
            )

            if (mCameraListener != null) {
                mCameraListener?.invoke(nv21_rotated, size)
            }
        }
        mCamera.addCallbackBuffer(data)
    }


    private fun captrue(bytes: ByteArray, index: Int = 0) {
        val fileName = "Camera_$index.jpg"
        val sdRoot = Environment.getExternalStorageDirectory()
        val pictureFile = File(sdRoot, fileName)
        if (!pictureFile.exists()) {
            try {
                pictureFile.createNewFile()
                val fileOutputStream =
                    FileOutputStream(pictureFile)
                val image = YuvImage(
                    bytes,
                    ImageFormat.NV21,
                    size.height,
                    size.width,
                    null
                ) //将NV21 data保存成YuvImage
                image.compressToJpeg(
                    Rect(0, 0, image.width, image.height),
                    100, fileOutputStream
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun setCameraListener(cameraListener: ((ByteArray?, size: Camera.Size) -> Unit)) {
        this.mCameraListener = cameraListener
    }

    fun getMediaCodec(): MediaCodec {
        return mediaCodec
    }
}