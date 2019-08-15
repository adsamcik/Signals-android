package com.adsamcik.tracker.common.extension

import java.io.File
import java.util.*

val File.lowerCaseExtension: String get() = extension.toLowerCase(Locale.getDefault())

