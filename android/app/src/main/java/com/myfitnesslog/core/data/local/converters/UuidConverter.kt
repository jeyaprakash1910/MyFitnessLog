package com.myfitnesslog.core.data.local.converters

import androidx.room.TypeConverter
import java.util.UUID

/**
 * Converts [UUID] primary/foreign keys to and from their canonical String form
 * for storage in SQLite.
 *
 * Rationale: the schema uses UUIDv4 identifiers generated in application code
 * (docs/DATABASE.md, UUID Strategy). String storage is chosen over BLOB for
 * readability and easy debugging at Version 1 scale.
 *
 * This converter compiles and is unit-testable independently of any entity; it
 * is registered on the Room database once entities are introduced (Phase 2).
 */
class UuidConverter {

    @TypeConverter
    fun fromUuid(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let(UUID::fromString)
}
