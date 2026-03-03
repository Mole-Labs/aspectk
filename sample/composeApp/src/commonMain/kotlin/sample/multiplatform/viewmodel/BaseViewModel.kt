package sample.multiplatform.viewmodel

import sample.multiplatform.annotations.AuditAction

abstract class BaseViewModel {
    @AuditAction(action = "load-data")
    open fun loadData(): String = ""

    @AuditAction(action = "submit-form")
    open fun submit(input: String): Boolean = false

    @AuditAction(action = "reset-state")
    open fun reset() {}
}
