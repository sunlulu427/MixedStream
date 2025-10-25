package com.astrastream.avpush.unified

/**
 * 测试常量
 * 统一管理测试中使用的URL、IP地址等常量
 */
object TestConstants {

    // RTMP 测试URL
    const val RTMP_TEST_URL = "rtmp://47.100.16.213:1935/live/123333"
    const val RTMP_BACKUP_URL = "rtmp://47.100.16.213:1935/live/backup"

    // WebRTC 测试URL
    const val WEBRTC_TEST_URL = "webrtc://signal.example.com/room123"
    const val WSS_TEST_URL = "wss://signal.example.com?room=test"

    // SRT 测试URL
    const val SRT_TEST_URL = "srt://example.com:9999"

    // 测试用的流名称
    const val TEST_STREAM_NAME = "123333"
    const val BACKUP_STREAM_NAME = "backup"

    // 测试用的房间ID
    const val TEST_ROOM_ID = "room123"
    const val TEST_ROOM_NAME = "test"
}