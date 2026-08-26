package sample.multiplatform.viewmodel

// @AuditAction 없음 → inherits=true 덕분에 Aspect가 동작함
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
