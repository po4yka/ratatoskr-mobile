package com.ratatoskr.mobile.notification

interface AndroidNotificationPermissionBoundary {
    suspend fun requestPostNotifications(): Boolean

    fun openNotificationSettings()

    fun cancelNotifications()
}

class AndroidNotificationPermissionPort(
    private val boundary: AndroidNotificationPermissionBoundary,
) : NativeNotificationPermissionPort {
    override suspend fun requestPermission(): NativeNotificationPermissionState =
        if (boundary.requestPostNotifications()) {
            NativeNotificationPermissionState.Granted
        } else {
            NativeNotificationPermissionState.Denied
        }

    override suspend fun openSettings() {
        boundary.openNotificationSettings()
    }

    override suspend fun clearNotifications() {
        boundary.cancelNotifications()
    }
}
