package com.astra.avpush.domain.callback

interface OnConnectListener {
    /**
     * 开始链接
     */
    fun onConnecting()

    /**
     * 连接成功
     */
    fun onConnected()

    /**
     * 推送失败
     */
    fun onFail(message:String)

    /**
     * 关闭
     */
    fun onClose()
}
