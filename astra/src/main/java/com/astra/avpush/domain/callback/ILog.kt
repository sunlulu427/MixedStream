package com.astra.avpush.domain.callback

interface ILog {
    fun i(tag: String = javaClass.simpleName, info: String?);
    fun e(tag: String = javaClass.simpleName, info: String?);
    fun w(tag: String = javaClass.simpleName, info: String?);
    fun d(tag: String = javaClass.simpleName, info: String?);
}
