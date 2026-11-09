package com.example.model

enum class Role {
    ADMINISTRATOR,
    MANAGER,
    STAFF
}

enum class SyncStatus {
    SYNCED,
    PENDING,
    CONFLICT
}

enum class PaymentStatus {
    PAID,
    PARTIAL,
    UNPAID
}
