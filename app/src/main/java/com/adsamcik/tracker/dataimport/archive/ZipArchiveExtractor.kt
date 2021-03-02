package com.adsamcik.tracker.dataimport.archive

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.adsamcik.tracker.dataimport.FileImportStream
import com.anggrayudi.storage.file.openInputStream
import java.util.zip.ZipInputStream

/**
 * Extracts zip archives
 */
internal class ZipArchiveExtractor : ArchiveExtractor {
	override val supportedExtensions: Collection<String> = listOf("zip")

	override fun extract(context: Context, file: DocumentFile): Sequence<FileImportStream>? {
		require(file.isDirectory) { "Directory is not a zip file" }

		file.openInputStream(context)?.use {
			ZipInputStream(it).use { zipStream ->
				var entry = zipStream.nextEntry
				return sequence {
					while (entry != null) {
						if (entry.isDirectory) {
							continue
						}

						yield(FileImportStream(zipStream, entry.name))

						entry = zipStream.nextEntry
					}
				}
			}
		}
		return null
	}
}