package com.mole.core.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.FqName

@OptIn(UnsafeDuringIrConstructionAPI::class)
class AdviceGenerationExtension : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val methodSignatureSymbol =
            pluginContext
                .getSymbol(
                    fqName = "com.mole.runtime.MethodSignature",
                )
        val methodParameterSymbol =
            pluginContext.getSymbol(
                fqName = "com.mole.runtime.MethodParameter",
            )

        val annotationInfoSymbol =
            pluginContext.getSymbol(
                fqName = "com.mole.runtime.AnnotationInfo",
            )

        moduleFragment.transform(
            MethodSignatureInjectTransformer(
                pluginContext,
                methodSignatureSymbol,
                methodParameterSymbol,
                annotationInfoSymbol,
                FqName("com.mole.runtime.Before"), // 추후 동적으로 변경,
            ),
            null,
        )
    }
}
