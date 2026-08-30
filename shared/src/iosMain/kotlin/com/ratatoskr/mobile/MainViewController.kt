package com.ratatoskr.mobile

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("ktlint:standard:function-naming")
fun MainViewController(controller: IosApplicationController): UIViewController =
    ComposeUIViewController {
        val shareStore by controller.shareStore.collectAsState()
        val libraryRoute by controller.libraryRoute.collectAsState()
        RatatoskrApp(
            sessionManager = controller.sessions,
            shareStore = shareStore,
            operationListStore = controller.operationListStore,
            operationDetailStore = controller::createOperationDetailStore,
            detailPollingVisible = controller.sceneActive,
            onDetailStoreActive = { controller.activeDetailStore = it },
            library = controller.library,
            github = controller.github,
            initialContentRoute = libraryRoute,
            localStorageStore = controller.localStorageStore,
        )
    }
