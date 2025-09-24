package com.devyk.av.rtmppush

import android.content.res.Configuration
import android.Manifest
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.devyk.av.rtmp.library.callback.OnConnectListener
import com.devyk.av.rtmp.library.camera.Watermark
import com.devyk.av.rtmp.library.config.AudioConfiguration
import com.devyk.av.rtmp.library.config.CameraConfiguration
import com.devyk.av.rtmp.library.config.VideoConfiguration
import com.devyk.av.rtmp.library.stream.packer.rtmp.RtmpPacker
import com.devyk.av.rtmp.library.stream.sender.rtmp.RtmpSender
import com.devyk.av.rtmp.library.utils.LogHelper
import com.devyk.av.rtmppush.base.BaseActivity
import kotlinx.android.synthetic.main.activity_live.live
import kotlinx.android.synthetic.main.activity_live.live_icon
import kotlinx.android.synthetic.main.activity_live.progressBar

class LiveActivity : BaseActivity<Int>(), OnConnectListener {
    /**
     * OpenGL 物体坐标，对应 Android 屏幕坐标
     *
     * -1.0，1.0                             1.0，1.0
     *  -------------------------------------
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                                   |
     *  |                   这里就是水印坐标   |
     *  |                          |-----    |
     *  |                          |    |    |
     *  |                          ——-—-|    |
     *  --------------------------------------
     * -1.0，1.0                             1.0，-1.0
     */
    private var mVertexData = floatArrayOf(
        0.55f, -0.9f, //第一个点 左下角
        0.9f, -0.9f, //第二个点 右下角
        0.55f, -0.7f, //第三个点 左上角
        0.9f, -0.7f //第四个点  右上角
    )

    private var mDataSource = "rtmp://www.devyk.cn:1992/devykLive/live1"
    private var isConncet = false
    private var mSender: RtmpSender? = null
    private lateinit var mPacker: RtmpPacker
    private var uploadDialog: AlertDialog? = null


    override fun initListener() {
        // Sender is created lazily; listener will be set when creating sender.
    }

    override fun initData() {
        //设置文字水印
        setWatemark()
    }

    override fun init() {
        //初始化包封装器
        mPacker = RtmpPacker()
        live.setPacker(mPacker)

        //初始化音频参数
        val audioConfiguration = AudioConfiguration()
        live.setAudioConfigure(audioConfiguration)

        //初始化视频编码参数
        val videoConfiguration = VideoConfiguration(
            fps=30,
            maxBps = 800,
            minBps = 400,
            codeType = VideoConfiguration.ICODEC.ENCODE,
            width = 960,
            height = 1920,
            ifi = 5,
            mediaCodec = true
        )
        live.setVideoConfigure(videoConfiguration)

        //初始化 camera 参数
        val cameraConfiguration = CameraConfiguration(
            width = 960,
            height = 1920
        )
        live.setCameraConfigure(cameraConfiguration)

        //设置预览（权限就绪后再启动，避免无权限时崩溃）
        tryStartPreview()

        // 推流地址在真正连接前再设置，避免无效初始化

        initRtmpAddressDialog()
    }

    override fun onContentViewBefore() {
        super.onContentViewBefore()
        Utils.init(application)
        // 打开调试日志，便于通过 logcat 排查问题
        com.devyk.av.rtmp.library.utils.LogHelper.isShowLog = BuildConfig.DEBUG
        checkPermission()
        setNotTitleBar()
    }

    override fun getLayoutId(): Int = R.layout.activity_live

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LogHelper.e(TAG, "方向改变:${newConfig.densityDpi}")
        live.previewAngle(this)
    }

    fun rtmp_live(view: View) {
        if (isConncet) {
            progressBar.visibility = View.VISIBLE;
            live.stopLive()
            mSender?.close()
            isConncet = false
            mPacker.stop()
            return
        }
        isConncet = true
        uploadDialog?.show()
    }

    fun camera_change(view: View) {
        live.switchCamera()
    }


    override fun onDestroy() {
        super.onDestroy()
        mSender?.close()
        live.stopLive()
        live.releaseCamera()
    }

    private var previewStarted = false

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun tryStartPreview() {
        if (previewStarted) return
        if (!hasCameraPermission()) {
            // 等待权限结果，在 onResume 或权限回调后再次尝试
            LogHelper.w(TAG, "camera permission not granted, skip startPreview")
            return
        }
        live.startPreview()
        previewStarted = true
    }

    override fun onResume() {
        super.onResume()
        // 权限可能在运行时被授予，回来后再次尝试
        tryStartPreview()
    }

    override fun onPermissionsUpdated(allGranted: Boolean) {
        if (allGranted) {
            // 权限到位后立即尝试预览
            tryStartPreview()
        }
    }


    override fun onFail(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE;
            live_icon.setImageDrawable(getDrawable(R.mipmap.live))
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onConnecting() {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE;
        }
    }

    override fun onConnected() {
        mPacker.start()
        live.startLive()
        //传输的过程中可以动态设置码率
        live.setVideoBps(300)
        runOnUiThread {
            live_icon.setImageDrawable(getDrawable(R.mipmap.stop))
            progressBar.visibility = View.GONE;
        }
    }

    override fun onClose() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            live_icon.setImageDrawable(getDrawable(R.mipmap.live))
        }
    }

    fun initRtmpAddressDialog() {
        val inflater = layoutInflater
        val playView = inflater.inflate(R.layout.address_dialog, findViewById(R.id.dialog))
        val address = playView.findViewById<EditText>(R.id.address)
        address.setText(mDataSource)
        val okBtn = playView.findViewById<Button>(R.id.ok)
        val cancelBtn = playView.findViewById<Button>(R.id.cancel)
        val uploadBuilder = AlertDialog.Builder(this)
        uploadBuilder.setTitle("输入推流地址")
        uploadBuilder.setView(playView)
        uploadDialog = uploadBuilder.create()
        okBtn.setOnClickListener {
            val uploadUrl = address.getText().toString()
            if (TextUtils.isEmpty(uploadUrl)) {
                Toast.makeText(applicationContext, "Upload address is empty!", Toast.LENGTH_SHORT)
                    .show()
            } else {
                // 创建 Sender（延迟加载 native 库，避免在 x86 模拟器上启动崩溃）
                if (ensureSenderSafely()) {
                    //设置 rtmp 地址
                    mSender?.setDataSource(uploadUrl)
                    //开始连接
                    mSender?.connect()
                } else {
                    // 设备架构不支持或库加载失败
                    isConncet = false
                }
            }
            uploadDialog?.dismiss()
        }
        cancelBtn.setOnClickListener { uploadDialog?.dismiss() }
    }


    private fun setWatemark() {
        //设置 Bitmap 水印 第二个参数如果传 null 那么默认在右下角
//        live.setWatermark(Watermark(BitmapFactory.decodeResource(resources, R.mipmap.live_logo), null))
        live.setWatermark(Watermark("Mato", Color.WHITE, 20, null))
    }

    private fun ensureSenderSafely(): Boolean {
        if (mSender != null) return true
        return try {
            val sender = RtmpSender()
            sender.setOnConnectListener(this)
            mSender = sender
            // 绑定到 Live 组件
            live.setSender(sender)
            true
        } catch (e: UnsatisfiedLinkError) {
            Toast.makeText(
                this,
                "当前设备架构不支持推流组件，请使用 ARM 真机",
                Toast.LENGTH_LONG
            ).show()
            com.devyk.av.rtmp.library.utils.LogHelper.e(TAG, "load native fail: ${e.message}")
            false
        } catch (t: Throwable) {
            com.devyk.av.rtmp.library.utils.LogHelper.e(TAG, "init sender error: ${t.message}")
            false
        }
    }
}
