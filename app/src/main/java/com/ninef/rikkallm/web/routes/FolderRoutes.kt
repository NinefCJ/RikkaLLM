package com.ninef.rikkallm.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.repository.FolderRepository
import com.ninef.rikkallm.service.ChatService
import com.ninef.rikkallm.web.BadRequestException
import com.ninef.rikkallm.web.ConflictException
import com.ninef.rikkallm.web.NotFoundException
import com.ninef.rikkallm.web.dto.CreateFolderRequest
import com.ninef.rikkallm.web.dto.RenameFolderRequest
import com.ninef.rikkallm.web.dto.toDto

fun Route.folderRoutes(
    chatService: ChatService,
    folderRepo: FolderRepository,
    settingsStore: SettingsStore,
) {
    route("/folders") {
        // GET /api/folders - List folders of current assistant
        get {
            val settings = settingsStore.settingsFlow.first()
            val folders = folderRepo.getFoldersOfAssistant(settings.assistantId).first()
            call.respond(folders.map { it.toDto() })
        }

        // POST /api/folders - Create a folder under current assistant
        post {
            val request = call.receive<CreateFolderRequest>()
            val name = request.name.trim()
            if (name.isEmpty()) {
                throw BadRequestException("Folder name must not be blank")
            }

            val settings = settingsStore.settingsFlow.first()
            val folder = folderRepo.createFolder(settings.assistantId, name)
            call.respond(HttpStatusCode.Created, folder.toDto())
        }

        // POST /api/folders/{id}/rename - Rename a folder
        post("/{id}/rename") {
            val uuid = call.parameters["id"].toUuid("folder id")
            val request = call.receive<RenameFolderRequest>()
            val name = request.name.trim()
            if (name.isEmpty()) {
                throw BadRequestException("Folder name must not be blank")
            }

            folderRepo.getFolderById(uuid) ?: throw NotFoundException("Folder not found")
            folderRepo.renameFolder(uuid, name)
            call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
        }

        // DELETE /api/folders/{id} - Delete a folder (conversations are kept, just unfiled)
        delete("/{id}") {
            val uuid = call.parameters["id"].toUuid("folder id")
            folderRepo.getFolderById(uuid) ?: throw NotFoundException("Folder not found")

            // Refuse to delete while a conversation inside is still generating
            if (chatService.hasGeneratingConversationInFolder(uuid)) {
                throw ConflictException("Folder has a generating conversation")
            }

            chatService.deleteFolder(uuid)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
