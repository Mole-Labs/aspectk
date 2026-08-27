package sample.multiplatform.viewmodel

// No @AuditAction here → the aspect still applies thanks to inherits=true
class OrderViewModel : BaseViewModel() {
    val orders = mutableListOf<String>()

    override fun loadData(): String {
        orders.addAll(listOf("Order#001", "Order#002"))
        return "orders: ${orders.size}개 로드됨"
    }

    override fun submit(input: String): Boolean {
        orders.add(input)
        return true
    }

    override fun reset() {
        orders.clear()
    }
}
