package com.astrastream.streamer.core.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.astrastream.streamer.R
import com.astrastream.streamer.core.util.SPUtils
import com.tbruyelle.rxpermissions2.RxPermissions

abstract class BaseActivity<T> : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onContentViewBefore()

        if (getLayoutId() is Int) {
            setContentView(getLayoutId() as Int)
        } else if (getLayoutId() is View) {
            setContentView(getLayoutId() as View)
        }
        checkPermission()
        init()
        initListener()
        initData()
    }

    /**
     * 在 setContentView 之前需要做的初始化
     */
    protected open fun onContentViewBefore() {

    }

    abstract fun initListener()

    abstract fun initData()

    abstract fun init()

    abstract fun getLayoutId(): T


    protected fun setNotTitleBar() {
        enableEdgeToEdge()
    }

    /**
     * 检查权限
     */
    @SuppressLint("CheckResult")
    protected fun checkPermission() {
        val key = getString(R.string.OPEN_PERMISSIONS)
        if (SPUtils.getInstance().getBoolean(key)) return
        val rxPermissions = RxPermissions(this)
        rxPermissions
            .request(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .subscribe { allGranted ->
                if (allGranted) {
                    SPUtils.getInstance().put(key, true)
                    Toast.makeText(this, getString(R.string.GET_PERMISSION_ERROR), Toast.LENGTH_SHORT).show()
                } else {
                    SPUtils.getInstance().put(key, false)
                }
                onPermissionsUpdated(allGranted)
            }
    }

    /**
     * 权限授予结果回调（全量）
     */
    protected open fun onPermissionsUpdated(allGranted: Boolean) {}

    fun startTime(timer: Chronometer) {
        val hour = ((SystemClock.elapsedRealtime() - timer.base) / 1000 / 60).toInt()
        timer.format = "0${hour}:%s"
        timer.start()
    }

    fun cleanTime(timer: Chronometer) {
        timer.base = SystemClock.elapsedRealtime()
        timer.stop()
    }
}
