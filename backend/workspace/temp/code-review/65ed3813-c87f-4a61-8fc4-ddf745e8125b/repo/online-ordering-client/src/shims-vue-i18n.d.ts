// Temporary shim to help IDE/tsserver resolve vue-i18n in case of broken type acquisition.
// If you have vue-i18n v9+ installed correctly, it already ships its own types.
// Keeping this file is harmless, but you can remove it once the IDE error is gone.

declare module 'vue-i18n' {
  // Minimal typing to unblock imports in the editor.
  // For full types, rely on vue-i18n's bundled declarations.
  export function createI18n(...args: any[]): any
}

