// "Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'" "true"
// IGNORE_FIR
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
@Target(AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

open class Base {
    @MyExperimentalAPI
    open fun foo() {}
}

class Outer {
    fun outer() {
        class Derived : Base() {
            override fun foo<caret>() {}
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixesFactory$HighPriorityPropagateOptInAnnotationFix