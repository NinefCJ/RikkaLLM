package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 新增 RAG 记忆表 memory_item 及其 FTS4 索引（含外部内容同步触发器）。 */
val Migration_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `memory_item` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `subject_tags` TEXT NOT NULL DEFAULT '[]',
                `importance` INTEGER NOT NULL DEFAULT 3,
                `status` INTEGER NOT NULL DEFAULT 0,
                `event_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `last_accessed_at` INTEGER NOT NULL,
                `embedding_blob` BLOB,
                `embedding_model_id` TEXT
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_item_assistant_id` ON `memory_item` (`assistant_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_item_status` ON `memory_item` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_item_event_at` ON `memory_item` (`event_at`)")

        db.execSQL(
            """CREATE VIRTUAL TABLE IF NOT EXISTS `memory_item_fts` USING Fts4(
                `content` TEXT, `subject_tags` TEXT, content=`memory_item`, contentless=0
            )"""
        )

        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_item_fts_BEFORE_UPDATE
                BEFORE UPDATE ON `memory_item` BEGIN
                    DELETE FROM `memory_item_fts` WHERE `docid`=OLD.`rowid`;
                END"""
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_item_fts_BEFORE_DELETE
                BEFORE DELETE ON `memory_item` BEGIN
                    DELETE FROM `memory_item_fts` WHERE `docid`=OLD.`rowid`;
                END"""
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_item_fts_AFTER_UPDATE
                AFTER UPDATE ON `memory_item` BEGIN
                    INSERT INTO `memory_item_fts`(`docid`, `content`, `subject_tags`)
                    VALUES (NEW.`rowid`, NEW.`content`, NEW.`subject_tags`);
                END"""
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_item_fts_AFTER_INSERT
                AFTER INSERT ON `memory_item` BEGIN
                    INSERT INTO `memory_item_fts`(`docid`, `content`, `subject_tags`)
                    VALUES (NEW.`rowid`, NEW.`content`, NEW.`subject_tags`);
                END"""
        )
    }
}
