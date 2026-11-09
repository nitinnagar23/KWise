package com.example.model

import java.math.BigDecimal

/**
 * Single source of truth for all role-based permissions in KhataWise.
 */
enum class Permission {
    CREATE_BILL,
    OVERRIDE_BILL_RATE,
    EDIT_DELETE_BILL,
    VIEW_TODAY_SALES,
    EDIT_TODAY_SALES,
    VIEW_DAILY_RATES,
    EDIT_DAILY_RATES,
    VIEW_CUSTOMERS,
    ADD_CUSTOMER,
    EDIT_OWN_CUSTOMER_BASIC,
    VIEW_CUSTOMER_BALANCE,
    VIEW_CUSTOMER_HISTORY,
    VIEW_CUSTOMER_FULL,
    EDIT_ANY_CUSTOMER,
    EDIT_CUSTOMER_FULL,
    EDIT_CREDIT_LIMIT,
    VIEW_COLLECTIONS,
    ACCESS_COLLECTIONS,
    VIEW_CASHBOOK,
    ACCESS_CASHBOOK,
    VIEW_REPORTS,
    ACCESS_REPORTS,
    VIEW_INVENTORY,
    ACCESS_INVENTORY,
    EDIT_INVENTORY,
    VIEW_SUPPLIERS,
    ACCESS_SUPPLIERS,
    EDIT_SUPPLIERS,
    MANAGE_SHOP_PROFILE,
    MANAGE_STAFF
}

/**
 * Evaluates whether the given [role] is granted [permission].
 *
 * Role matrix:
 * - Billing (new bill): all roles
 * - Rate/discount override at billing: Admin, Manager only (Staff always uses listed rate)
 * - Edit or delete a bill: Admin, Manager only
 * - View today's sales list: all; Edit today's sales: Admin, Manager only
 * - Today's Rates: view for all, edit for Admin and Manager only
 * - Customers: all can view list and add customer; Staff can edit only name, phone, village for customers they created
 * - Customer balance, full history, full edit: Admin and Manager
 * - Udhari (credit) limit: Admin only
 * - Collections, Cashbook, Reports, Inventory, Suppliers: Admin and Manager
 * - Shop Profile and Staff Management: Admin only
 */
fun can(role: Role?, permission: Permission): Boolean {
    if (role == null) return false
    return when (permission) {
        Permission.CREATE_BILL,
        Permission.VIEW_TODAY_SALES,
        Permission.VIEW_DAILY_RATES,
        Permission.VIEW_CUSTOMERS,
        Permission.ADD_CUSTOMER,
        Permission.EDIT_OWN_CUSTOMER_BASIC -> true

        Permission.OVERRIDE_BILL_RATE,
        Permission.EDIT_DELETE_BILL,
        Permission.EDIT_TODAY_SALES,
        Permission.EDIT_DAILY_RATES,
        Permission.VIEW_CUSTOMER_BALANCE,
        Permission.VIEW_CUSTOMER_HISTORY,
        Permission.VIEW_CUSTOMER_FULL,
        Permission.EDIT_ANY_CUSTOMER,
        Permission.EDIT_CUSTOMER_FULL,
        Permission.VIEW_COLLECTIONS,
        Permission.ACCESS_COLLECTIONS,
        Permission.VIEW_CASHBOOK,
        Permission.ACCESS_CASHBOOK,
        Permission.VIEW_REPORTS,
        Permission.ACCESS_REPORTS,
        Permission.VIEW_INVENTORY,
        Permission.ACCESS_INVENTORY,
        Permission.EDIT_INVENTORY,
        Permission.VIEW_SUPPLIERS,
        Permission.ACCESS_SUPPLIERS,
        Permission.EDIT_SUPPLIERS -> role == Role.ADMINISTRATOR || role == Role.MANAGER

        Permission.EDIT_CREDIT_LIMIT,
        Permission.MANAGE_SHOP_PROFILE,
        Permission.MANAGE_STAFF -> role == Role.ADMINISTRATOR
    }
}

/**
 * Throws [SecurityException] if [role] is not allowed to perform [permission].
 */
fun requirePermission(role: Role?, permission: Permission) {
    if (!can(role, permission)) {
        throw SecurityException("Role ${role ?: "NONE"} does not have permission $permission")
    }
}

/**
 * Checks whether [user] can edit basic details (name, phone, village) of [customer].
 * Admin and Manager can edit any customer; Staff can edit only customers they created.
 */
fun canEditCustomerBasic(user: User?, customer: Customer): Boolean {
    if (user == null) return false
    if (can(user.role, Permission.EDIT_CUSTOMER_FULL)) return true
    return can(user.role, Permission.EDIT_OWN_CUSTOMER_BASIC) &&
        customer.createdByUserId.isNotBlank() &&
        customer.createdByUserId == user.id
}

/**
 * Status chip shown to Staff (and optionally alongside balance for Admin/Manager)
 * without exposing exact Udhari amounts to Staff.
 */
enum class CustomerStatusChip(val labelEn: String, val labelHi: String) {
    CLEAR("Clear", "क्लियर"),
    HAS_UDHARI("Has Udhari", "उधारी है"),
    OVER_LIMIT("Over limit", "सीमा पार")
}

fun getCustomerStatusChip(customer: Customer): CustomerStatusChip {
    val hasLimit = customer.creditLimit.compareTo(BigDecimal.ZERO) > 0
    return when {
        hasLimit && customer.totalUdhari.compareTo(customer.creditLimit) > 0 -> CustomerStatusChip.OVER_LIMIT
        customer.totalUdhari.compareTo(BigDecimal.ZERO) > 0 -> CustomerStatusChip.HAS_UDHARI
        else -> CustomerStatusChip.CLEAR
    }
}

/**
 * Returns true if the customer has a positive credit limit and either is already over limit
 * or adding [additionalUdhari] (> 0) would push their balance over [Customer.creditLimit].
 */
fun isCustomerCreditBlocked(
    customer: Customer,
    additionalUdhari: BigDecimal = BigDecimal.ZERO
): Boolean {
    if (customer.creditLimit.compareTo(BigDecimal.ZERO) <= 0) return false
    if (customer.totalUdhari.compareTo(customer.creditLimit) > 0) return true
    if (additionalUdhari.compareTo(BigDecimal.ZERO) > 0) {
        return customer.totalUdhari.add(additionalUdhari).compareTo(customer.creditLimit) > 0
    }
    return false
}
