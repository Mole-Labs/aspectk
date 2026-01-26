package com.mole.core.ir

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

        moduleFragment.acceptChildren(AspectVisitor(aspectkContext), null)
        moduleFragment.transform(
            MethodSignatureInjectTransformer(
                aspectkContext,
                aspectkContext.aspectLookUp.targets,
            ),
            null,
        )
    }
}
