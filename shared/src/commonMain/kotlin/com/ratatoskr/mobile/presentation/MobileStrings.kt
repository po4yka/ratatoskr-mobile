package com.ratatoskr.mobile.presentation

enum class MobileLocale {
    English,
    Russian,
}

enum class MobileStringKey {
    SearchTitle,
    SearchAction,
    SearchHint,
    SearchQueryLabel,
    SearchInvalid,
    SearchLoading,
    SearchEmpty,
    SearchUnavailable,
    SearchOffline,
    SearchRetry,
    SearchRepairPairing,
    SearchResults,
    SearchLoadMore,
    SearchLoadingMore,
    SearchRead,
    SearchUnread,
    SearchRelevance,
    NotificationsTitle,
    NotificationsIntegrationPending,
    NotificationsDisabled,
    NotificationsPermissionRequired,
    NotificationsDenied,
    NotificationsEnabling,
    NotificationsEnabled,
    NotificationsEnableAction,
    NotificationsDisableAction,
    NotificationsPairDevice,
    CollectionUnavailable,
    RepositoryOpening,
    Back,
}

object MobileStrings {
    fun value(
        key: MobileStringKey,
        locale: MobileLocale,
    ): String =
        when (locale) {
            MobileLocale.English -> english(key)
            MobileLocale.Russian -> russian(key)
        }

    private fun english(key: MobileStringKey): String =
        when (key) {
            MobileStringKey.SearchTitle -> "Search"
            MobileStringKey.SearchAction -> "Search"
            MobileStringKey.SearchHint -> "Search the Ratatoskr library"
            MobileStringKey.SearchQueryLabel -> "Search"
            MobileStringKey.SearchInvalid -> "Enter 1–512 characters"
            MobileStringKey.SearchLoading -> "Searching…"
            MobileStringKey.SearchEmpty -> "No matches for this search"
            MobileStringKey.SearchUnavailable -> "Search is unavailable on this Ratatoskr instance"
            MobileStringKey.SearchOffline -> "Search is offline"
            MobileStringKey.SearchRetry -> "Retry search"
            MobileStringKey.SearchRepairPairing -> "Pair this device again"
            MobileStringKey.SearchResults -> "Live Platform search results"
            MobileStringKey.SearchLoadMore -> "Load more"
            MobileStringKey.SearchLoadingMore -> "Loading more…"
            MobileStringKey.SearchRead -> "Read"
            MobileStringKey.SearchUnread -> "Unread"
            MobileStringKey.SearchRelevance -> "Relevance"
            MobileStringKey.NotificationsTitle -> "Operation notifications"
            MobileStringKey.NotificationsIntegrationPending ->
                "Server completion notifications are integration pending. No permission or push token is requested."
            MobileStringKey.NotificationsDisabled -> "Operation notifications are disabled."
            MobileStringKey.NotificationsPermissionRequired -> "Notification permission is required."
            MobileStringKey.NotificationsDenied -> "Notification permission is denied. Open system settings to change it."
            MobileStringKey.NotificationsEnabling -> "Enabling operation notifications…"
            MobileStringKey.NotificationsEnabled -> "Operation notifications are enabled."
            MobileStringKey.NotificationsEnableAction -> "Enable notifications"
            MobileStringKey.NotificationsDisableAction -> "Disable notifications"
            MobileStringKey.NotificationsPairDevice -> "Pair this device to configure notifications."
            MobileStringKey.CollectionUnavailable -> "Collection is integration pending or unavailable"
            MobileStringKey.RepositoryOpening -> "Opening repository…"
            MobileStringKey.Back -> "Back"
        }

    private fun russian(key: MobileStringKey): String =
        when (key) {
            MobileStringKey.SearchTitle -> "Поиск"
            MobileStringKey.SearchAction -> "Найти"
            MobileStringKey.SearchHint -> "Поиск по библиотеке Ratatoskr"
            MobileStringKey.SearchQueryLabel -> "Запрос"
            MobileStringKey.SearchInvalid -> "Введите от 1 до 512 символов"
            MobileStringKey.SearchLoading -> "Идёт поиск…"
            MobileStringKey.SearchEmpty -> "По этому запросу ничего не найдено"
            MobileStringKey.SearchUnavailable -> "Поиск недоступен на этом экземпляре Ratatoskr"
            MobileStringKey.SearchOffline -> "Поиск недоступен без сети"
            MobileStringKey.SearchRetry -> "Повторить поиск"
            MobileStringKey.SearchRepairPairing -> "Повторно подключить устройство"
            MobileStringKey.SearchResults -> "Результаты поиска Platform"
            MobileStringKey.SearchLoadMore -> "Загрузить ещё"
            MobileStringKey.SearchLoadingMore -> "Загружаются новые результаты…"
            MobileStringKey.SearchRead -> "Прочитано"
            MobileStringKey.SearchUnread -> "Непрочитано"
            MobileStringKey.SearchRelevance -> "Релевантность"
            MobileStringKey.NotificationsTitle -> "Уведомления об операциях"
            MobileStringKey.NotificationsIntegrationPending ->
                "Серверные уведомления ещё не интегрированы. Разрешение и push-токен не запрашиваются."
            MobileStringKey.NotificationsDisabled -> "Уведомления об операциях выключены."
            MobileStringKey.NotificationsPermissionRequired -> "Требуется разрешение на уведомления."
            MobileStringKey.NotificationsDenied ->
                "Уведомления запрещены. Изменить разрешение можно в системных настройках."
            MobileStringKey.NotificationsEnabling -> "Уведомления об операциях включаются…"
            MobileStringKey.NotificationsEnabled -> "Уведомления об операциях включены."
            MobileStringKey.NotificationsEnableAction -> "Включить уведомления"
            MobileStringKey.NotificationsDisableAction -> "Выключить уведомления"
            MobileStringKey.NotificationsPairDevice -> "Подключите устройство, чтобы настроить уведомления."
            MobileStringKey.CollectionUnavailable -> "Коллекция ещё не интегрирована или недоступна"
            MobileStringKey.RepositoryOpening -> "Репозиторий открывается…"
            MobileStringKey.Back -> "Назад"
        }
}
