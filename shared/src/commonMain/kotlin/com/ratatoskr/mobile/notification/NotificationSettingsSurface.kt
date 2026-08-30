package com.ratatoskr.mobile.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratatoskr.mobile.presentation.AccessibleAction
import com.ratatoskr.mobile.presentation.AccessibleHeading
import com.ratatoskr.mobile.presentation.AccessibleStatus
import com.ratatoskr.mobile.presentation.LocalMobileLocale
import com.ratatoskr.mobile.presentation.MobileStringKey
import com.ratatoskr.mobile.presentation.MobileStrings

@Composable
@Suppress("ktlint:standard:function-naming")
fun NotificationSettingsSurface(
    state: CompletionNotificationState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit,
) {
    val locale = LocalMobileLocale.current

    fun string(key: MobileStringKey) = MobileStrings.value(key, locale)
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccessibleHeading(string(MobileStringKey.NotificationsTitle))
        AccessibleStatus(
            when (state.effective) {
                CompletionNotificationEffectiveState.Unpaired -> string(MobileStringKey.NotificationsPairDevice)
                CompletionNotificationEffectiveState.IntegrationPending ->
                    string(MobileStringKey.NotificationsIntegrationPending)
                CompletionNotificationEffectiveState.Disabled -> string(MobileStringKey.NotificationsDisabled)
                CompletionNotificationEffectiveState.PermissionRequired ->
                    string(MobileStringKey.NotificationsPermissionRequired)
                CompletionNotificationEffectiveState.PermissionDenied ->
                    string(MobileStringKey.NotificationsDenied)
                CompletionNotificationEffectiveState.Enabling -> string(MobileStringKey.NotificationsEnabling)
                CompletionNotificationEffectiveState.Enabled -> string(MobileStringKey.NotificationsEnabled)
            },
        )
        if (state.availability == CompletionSubscriptionAvailability.Available && !state.enabledByUser) {
            NotificationAction(string(MobileStringKey.NotificationsEnableAction), onEnable)
        }
        if (state.enabledByUser) NotificationAction(string(MobileStringKey.NotificationsDisableAction), onDisable)
        NotificationAction(string(MobileStringKey.Back), onBack)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun NotificationAction(
    label: String,
    onClick: () -> Unit,
) {
    AccessibleAction(label, onClick = onClick)
}
