// "Remove 'inner' modifier" "true"
interface A {
    inne<caret>r class B
}

/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase