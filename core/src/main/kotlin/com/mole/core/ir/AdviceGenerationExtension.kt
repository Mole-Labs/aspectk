package com.mole.core.ir

import com.mole.core.trace
import com.mole.core.tracer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

@OptIn(UnsafeDuringIrConstructionAPI::class)
class AdviceGenerationExtension : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val aspectkContext = AspectKIrCompilerContext(pluginContext)
        val joinPointGenerator = JoinPointGenerator(aspectkContext)
        val methodSignatureGenerator = MethodSignatureFieldGenerator(aspectkContext)

        aspectkContext
            .tracer(
                tag =
                    moduleFragment.name
                        .asString()
                        .removePrefix("<")
                        .removeSuffix(">"),
                description = "Advice Generation",
            ).trace {
                moduleFragment.acceptChildren(AspectVisitor(aspectkContext), null)
                moduleFragment.transform(
                    AspectTransformer(
                        joinPointGenerator,
                        methodSignatureGenerator,
                        aspectkContext,
                        aspectkContext.aspectLookUp.targets,
                    ),
                    null,
                )
            }
    }
}
