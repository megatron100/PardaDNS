package com.example.pardadns.data.db

/** Projection returned by the lifetime-totals aggregate query. */
data class LifetimeTotalsRow(val totalQueries: Int, val blockedCount: Int)
