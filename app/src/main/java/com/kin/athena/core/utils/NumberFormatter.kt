/*
 * Copyright (C) 2025-2026 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kin.athena.core.utils

/**
 * Utility object for formatting numbers into human-readable abbreviated formats. Used for
 * displaying large counts in a compact format (e.g., 1K, 1.2M).
 */
object NumberFormatter {
  /**
   * Formats a count into an abbreviated string representation.
   *
   * @param count The number to format
   * @return Formatted string representation:
   *     - Numbers < 1,000: displayed as-is (e.g., "999")
   *     - Numbers >= 1,000 and < 1,000,000: displayed with K suffix (e.g., "1K", "1.2K")
   *     - Numbers >= 1,000,000: displayed with M suffix (e.g., "1M", "1.2M")
   *     - Zero: returns "0"
   *     - Negative numbers: returns "0" (treated as no activity)
   */
  fun formatCount(count: Long): String =
    when {
      // Handle edge cases
      count < 0 -> {
        "0"
      }

      // Negative numbers treated as no activity
      count == 0L -> {
        "0"
      }

      // Handle millions (1,000,000+)
      count >= 1_000_000 -> {
        val millions = count / 1_000_000.0
        if (millions >= 10.0) {
          // For 10M+, show whole numbers to save space
          "${(millions).toInt()}M"
        } else {
          // For 1.0M - 9.9M, show one decimal place
          String.format(java.util.Locale.US, "%.1fM", millions).replace(".0M", "M")
        }
      }

      // Handle thousands (1,000 - 999,999)
      count >= 1_000 -> {
        val thousands = count / 1_000.0
        if (thousands >= 10.0) {
          // For 10K+, show whole numbers to save space
          "${(thousands).toInt()}K"
        } else {
          // For 1.0K - 9.9K, show one decimal place
          String.format(java.util.Locale.US, "%.1fK", thousands).replace(".0K", "K")
        }
      }

      // Handle numbers less than 1,000
      else -> {
        count.toString()
      }
    }

  /**
   * Formats bytes into human-readable units (B, KB, MB, GB).
   *
   * @param bytes The number of bytes to format
   * @return Formatted string representation with appropriate unit
   */
  fun formatBytes(bytes: Long): String =
    when {
      bytes < 0 -> "0 B"
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
      bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
      else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }.replace(".0 ", " ")
}
