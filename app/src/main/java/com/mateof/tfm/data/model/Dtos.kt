package com.mateof.tfm.data.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// System / auth
// ---------------------------------------------------------------------------

@Serializable
data class SystemInfoDto(
    val product: String? = null,
    val version: String? = null,
    val apiVersion: String? = null,
    val serverTimeUtc: String? = null,
    val mongoConnected: Boolean = false,
    val telegramConfigured: Boolean = false,
    val telegramAuthenticated: Boolean = false,
    val setupComplete: Boolean = false,
    val webDavRunning: Boolean = false,
    val transfersHubPath: String? = null,
    val requiresApiKey: Boolean = false
)

@Serializable
data class SystemMetricsDto(
    val cpuUsagePercent: Double? = null,
    val memoryUsedBytes: Long? = null,
    val memoryTotalBytes: Long? = null,
    val diskUsedBytes: Long? = null,
    val diskTotalBytes: Long? = null,
    val diskFreeBytes: Long? = null
)

@Serializable
data class TelegramUserDto(
    val id: Long = 0,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val isPremium: Boolean = false
)

@Serializable
data class AuthStatusDto(
    val step: String? = null,
    val isAuthenticated: Boolean = false,
    val isConfigured: Boolean = false,
    val user: TelegramUserDto? = null
)

@Serializable
data class LoginRequest(val value: String, val isPhone: Boolean = false)

@Serializable
data class QrSessionDto(
    val sessionId: String? = null,
    val loginUrl: String? = null,
    val qrImageBase64: String? = null,
    val status: String? = null,
    val error: String? = null
)

@Serializable
data class QrPasswordRequest(val password: String)

// ---------------------------------------------------------------------------
// Channels
// ---------------------------------------------------------------------------

@Serializable
data class ChannelDto(
    val id: Long = 0,
    val name: String? = null,
    val type: String? = null,
    val isOwner: Boolean = false,
    val isFavorite: Boolean = false,
    val imageUrl: String? = null,
    val hasDatabase: Boolean = false,
    // details-only fields
    val fileCount: Long? = null,
    val folderCount: Long? = null,
    val totalSize: Long? = null,
    val totalSizeText: String? = null,
    val audioCount: Long? = null,
    val videoCount: Long? = null,
    val photoCount: Long? = null,
    val documentCount: Long? = null,
    val isRefreshing: Boolean? = null,
    val canRefresh: Boolean? = null
)

@Serializable
data class ChatFolderDto(
    val id: Long = 0,
    val title: String? = null,
    val iconEmoji: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    val channelCount: Int = 0
)

@Serializable
data class ChannelFoldersDto(
    val folders: List<ChatFolderDto> = emptyList(),
    val ungrouped: List<ChannelDto> = emptyList(),
    val totalChannels: Int = 0
)

@Serializable
data class CreateChannelRequest(
    val title: String,
    val about: String? = null,
    val createDatabase: Boolean = true
)

@Serializable
data class LeaveChannelRequest(
    val deleteLocalDatabase: Boolean = false,
    val deleteOnTelegram: Boolean = false
)

@Serializable
data class RefreshChannelRequest(
    val includeDocuments: Boolean = true,
    val includeAudio: Boolean = true,
    val includeVideo: Boolean = true,
    val includePhotos: Boolean = true,
    val force: Boolean = false
)

@Serializable
data class ChannelMessageDto(
    val id: Long = 0,
    val date: String? = null,
    val text: String? = null,
    val hasMedia: Boolean = false,
    val mediaType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val from: String? = null
)

@Serializable
data class InvitationDto(
    val invitationHash: String? = null,
    val invitationLink: String? = null
)

// ---------------------------------------------------------------------------
// Files (channel + local share the same shapes)
// ---------------------------------------------------------------------------

@Serializable
data class ApiFileDto(
    val id: String = "",
    val name: String = "",
    val path: String? = null,
    val parentId: String? = null,
    val isFile: Boolean = false,
    val hasChildren: Boolean = false,
    val size: Long? = null,
    val sizeText: String? = null,
    val type: String? = null,
    val category: String? = null,
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val messageId: Long? = null,
    val isSplit: Boolean = false,
    val md5Hash: String? = null,
    val xxHash: String? = null,
    val streamUrl: String? = null,
    val downloadUrl: String? = null
)

@Serializable
data class FolderStatsDto(
    val folderCount: Long = 0,
    val fileCount: Long = 0,
    val audioCount: Long = 0,
    val videoCount: Long = 0,
    val photoCount: Long = 0,
    val documentCount: Long = 0,
    val totalSize: Long = 0,
    val totalSizeText: String? = null
)

@Serializable
data class BreadcrumbDto(
    val name: String = "",
    val path: String = "/",
    val folderId: String? = null
)

@Serializable
data class FolderContentsDto(
    val channelId: String? = null,
    val currentPath: String? = null,
    val currentFolderId: String? = null,
    val parentFolderId: String? = null,
    val parentPath: String? = null,
    val folderName: String? = null,
    val items: List<ApiFileDto> = emptyList(),
    val stats: FolderStatsDto? = null,
    val breadcrumbs: List<BreadcrumbDto> = emptyList()
)

@Serializable
data class CreateFolderRequest(val path: String, val name: String)

@Serializable
data class RenameFileRequest(val newName: String)

@Serializable
data class IdsRequest(val ids: List<String>)

@Serializable
data class CopyMoveRequest(
    val ids: List<String>,
    val targetPath: String? = null,
    val targetFolderId: String? = null
)

@Serializable
data class OperationResultDto(
    val accepted: Int? = null,
    val skipped: List<String> = emptyList(),
    val taskId: String? = null
)

// ---------------------------------------------------------------------------
// Local files
// ---------------------------------------------------------------------------

@Serializable
data class LocalRenameRequest(val path: String, val newName: String)

@Serializable
data class LocalDeleteRequest(val paths: List<String>)

@Serializable
data class FilesByTypeDto(
    val extension: String? = null,
    val category: String? = null,
    val icon: String? = null,
    val count: Long = 0,
    val sizeBytes: Long = 0,
    val sizeWithSuffix: String? = null
)

@Serializable
data class DirectorySizeDto(
    val sizeBytes: Long = 0,
    val sizeWithSuffix: String? = null,
    val totalElements: Long = 0,
    val filesByType: List<FilesByTypeDto> = emptyList()
)

// ---------------------------------------------------------------------------
// Transfers
// ---------------------------------------------------------------------------

@Serializable
data class TransferDto(
    val id: String? = null,
    val kind: String? = null,
    val action: String? = null,
    val state: String? = null,
    val isQueued: Boolean = false,
    val name: String? = null,
    val path: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val size: Long? = null,
    val transmitted: Long? = null,
    val sizeText: String? = null,
    val transmittedText: String? = null,
    val progress: Int? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val totalItems: Int? = null,
    val executedItems: Int? = null,
    val isUpload: Boolean? = null,
    val fromPath: String? = null,
    val toPath: String? = null
)

@Serializable
data class TransferSummaryDto(
    val activeDownloads: Int = 0,
    val queuedDownloads: Int = 0,
    val activeUploads: Int = 0,
    val queuedUploads: Int = 0,
    val activeTasks: Int = 0,
    val totalTasks: Int = 0,
    val downloadSpeed: String? = null,
    val uploadSpeed: String? = null,
    val downloadBytesPerSecond: Long = 0,
    val uploadBytesPerSecond: Long = 0,
    val downloadsPaused: Boolean = false,
    val isWorking: Boolean = false
)

@Serializable
data class TransfersSnapshotDto(
    val downloads: List<TransferDto> = emptyList(),
    val queuedDownloads: List<TransferDto> = emptyList(),
    val uploads: List<TransferDto> = emptyList(),
    val queuedUploads: List<TransferDto> = emptyList(),
    val tasks: List<TransferDto> = emptyList(),
    val summary: TransferSummaryDto? = null
)

@Serializable
data class SpeedPointDto(
    val time: String? = null,
    val bytesPerSecond: Long = 0,
    val speedText: String? = null,
    val activeFiles: List<String> = emptyList()
)

@Serializable
data class SpeedHistoryDto(
    val download: List<SpeedPointDto> = emptyList(),
    val upload: List<SpeedPointDto> = emptyList(),
    val intervalSeconds: Int = 3,
    val windowSeconds: Int = 600
)

@Serializable
data class StartDownloadsRequest(
    val channelId: String,
    val fileIds: List<String>,
    val targetPath: String? = null,
    val sharedCollectionId: String? = null
)

@Serializable
data class StartUploadsRequest(
    val channelId: String,
    val localPaths: List<String>,
    val targetPath: String? = null
)

@Serializable
data class DownloadMessagesRequest(
    val chatId: Long,
    val messageIds: List<Long>,
    val targetPath: String? = null
)

@Serializable
data class PersistedTransferDto(
    val id: String? = null,
    val internalId: String? = null,
    val type: String? = null,
    val state: String? = null,
    val name: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val totalSize: Long? = null,
    val transmittedBytes: Long? = null,
    val progress: Int? = null,
    val sourcePath: String? = null,
    val destinationPath: String? = null,
    val creationDate: String? = null,
    val lastUpdated: String? = null,
    val retryCount: Int? = null,
    val lastError: String? = null
)

// ---------------------------------------------------------------------------
// Playlists
// ---------------------------------------------------------------------------

@Serializable
data class PlaylistTrackDto(
    val fileId: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val fileName: String? = null,
    val filePath: String? = null,
    val fileType: String? = null,
    val fileSize: Long? = null,
    val order: Int = 0,
    val directUrl: String? = null,
    val isLocalFile: Boolean = false,
    val dateAdded: String? = null
)

@Serializable
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val tracks: List<PlaylistTrackDto> = emptyList(),
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val trackCount: Int = 0
)

@Serializable
data class CreatePlaylistRequest(val name: String, val description: String? = null)

@Serializable
data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val tracks: List<PlaylistTrackDto>? = null
)

@Serializable
data class AddTrackRequest(
    val fileId: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val fileName: String? = null,
    val filePath: String? = null,
    val fileType: String? = null,
    val fileSize: Long? = null,
    val directUrl: String? = null,
    val isLocalFile: Boolean = false
)

// ---------------------------------------------------------------------------
// Shares / STRM
// ---------------------------------------------------------------------------

@Serializable
data class SharedCollectionDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val channelId: String? = null,
    val collectionId: String? = null,
    val dateCreated: String? = null,
    val dateModified: String? = null
)

@Serializable
data class CreateStrmRequest(
    val path: String = "/",
    val host: String? = null,
    val destinationFolder: String? = null
)

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

@Serializable
data class AppConfigDto(
    val maxSimultaneousDownloads: Int? = null,
    val splitSize: Double? = null,
    val checkHash: Boolean? = null,
    val strmStreamingMode: String? = null,
    val enableTaskPersistence: Boolean? = null,
    val autoResumeOnStartup: Boolean? = null,
    val parallelTransfers: Int? = null,
    val enableMultiConnectionDownloads: Boolean? = null,
    val downloadConnections: Int? = null,
    val enableRefreshOwnChannels: Boolean? = null
)
