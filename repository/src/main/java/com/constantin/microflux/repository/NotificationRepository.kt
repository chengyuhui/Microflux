package com.constantin.microflux.repository

import com.constantin.microflux.data.EntryStarred
import com.constantin.microflux.data.EntryStatus
import com.constantin.microflux.data.FeedNotificationCount
import com.constantin.microflux.data.Result
import com.constantin.microflux.database.Account
import com.constantin.microflux.database.ConstafluxDatabase
import com.constantin.microflux.database.FeedBackground
import com.constantin.microflux.repository.transformation.AccountFeedNotificationData
import com.constantin.microflux.repository.transformation.FeedNotificationInformation
import com.constantin.microflux.repository.transformation.NotificationInformationBundle
import com.constantin.microflux.util.forEachAsync
import com.constantin.microflux.util.mapAsync

class NotificationRepository(
    private val constafluxDatabase: ConstafluxDatabase,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val feedRepository: FeedRepository,
    private val entryRepository: EntryRepository
) {
    suspend fun refreshAllContent(): Result<NotificationInformationBundle> {

        val result = accountRepository.checkValidAccounts() as? Result.UserValidation
            ?: return Result.Error.NetworkError.ConnectivityError

        return Result.notificationInformation(
            notifications = NotificationInformationBundle(
                accountsFeedsInformation = result.validUsers.mapAsync { account ->
                    account.fetchCategory()
                    AccountFeedNotificationData(
                        feedsInformation = account.fetchFeeds().createFeedNotifications(account),
                        feedTotalCount = feedRepository.getFeedNotificationDisplayed(
                            serverId = account.serverId,
                            userId = account.userId
                        ).executeAsOne()
                    )

                },
                invalidAccounts = result.invalidUsers
            )
        )
    }

    private suspend fun Account.fetchCategory() {
        categoryRepository.fetch(this)
    }

    private suspend fun Account.fetchFeeds(): List<FeedBackground> {
        feedRepository.fetch(this)
        return constafluxDatabase.feedQueries.selectAllBackground(
            serverId = this.serverId,
            userId = this.userId
        ).executeAsList()
    }

    private suspend fun List<FeedBackground>.createFeedNotifications(account: Account): List<FeedNotificationInformation> {
        val notifications = mutableListOf<FeedNotificationInformation>()
        forEachAsync { feed ->
            val lastUpdateAtUnix = constafluxDatabase.feedQueries.selectLastUpdateAtUnix(
                serverId = feed.serverId,
                feedId = feed.feedId
            ).executeAsOne()

            entryRepository.fetchFeed(
                account = account,
                feedId = feed.feedId,
                entryStarred = EntryStarred.UN_STARRED,
                entryStatus = EntryStatus.ALL,
                clearPrevious = true,
                clearNotification = false
            )

            if (account.userFirstTimeRun.firstTimeRun.not()) {
                val newUnreadEntries = constafluxDatabase.entryQueries.selectLatestFeedEntries(
                    serverId = feed.serverId,
                    userId = feed.userId,
                    feedId = feed.feedId,
                    entryStatus = EntryStatus.UN_READ
                ).executeAsList()

                val newNotificationCount = FeedNotificationCount(
                    newUnreadEntries.count { entryPublishedAtUnix ->
                        entryPublishedAtUnix.publishedAt > lastUpdateAtUnix.lastUpdateAtUnix
                    }.toLong()
                )

                if (newNotificationCount != FeedNotificationCount.INVALID && feed.feedAllowNotification.notification) {
                    feedRepository.addFeedNotificationCount(
                        serverId = feed.serverId,
                        feedId = feed.feedId,
                        feedNotificationCount = newNotificationCount
                    )
                    val totalFeedNotificationCount = feedRepository.getFeedNotificationCount(
                        serverId = feed.serverId,
                        feedId = feed.feedId
                    ).executeAsOne()

                    if (totalFeedNotificationCount != FeedNotificationCount.INVALID){
                        notifications.add(
                            FeedNotificationInformation(
                                serverId = feed.serverId,
                                userId = feed.userId,
                                userName = account.userName,
                                feedId = feed.feedId,
                                feedTitle = feed.feedTitle,
                                feedIcon = feed.feedIcon,
                                notificationItemsCount = feedRepository.getFeedNotificationCount(
                                    serverId = feed.serverId,
                                    feedId = feed.feedId
                                ).executeAsOne(),
                                notify = newNotificationCount != FeedNotificationCount.INVALID
                            )
                        )
                    }
                }
            } else {
                constafluxDatabase.userQueries.setUserRanFirstTime(
                    serverId = account.serverId,
                    userId = account.userId
                )
            }
        }
        return notifications
    }
}