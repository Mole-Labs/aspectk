package sample.multiplatform.viewmodel

// No @AuditAction here → the aspect still applies thanks to inherits=true
class ProductViewModel : BaseViewModel() {
    val products = mutableListOf<String>()

    override fun loadData(): String {
        products.addAll(listOf("MacBook Pro", "iPhone 16", "iPad Air"))
        return "products: ${products.size}개 로드됨"
    }

    override fun submit(input: String): Boolean {
        products.add(input)
        return true
    }

    override fun reset() {
        products.clear()
    }
}
