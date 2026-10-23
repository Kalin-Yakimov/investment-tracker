import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";

export default [
  { ignores: ["build/**", "coverage/**", "node_modules/**", "test-results/**", "playwright-report/**"] },
  {
    files: ["**/*.{js,jsx,cjs}"],
    ...js.configs.recommended,
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      parserOptions: { ecmaFeatures: { jsx: true } },
      globals: { ...globals.browser },
    },
    plugins: { "react-hooks": reactHooks, "react-refresh": reactRefresh },
    rules: {
      ...js.configs.recommended.rules,
      "no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrorsIgnorePattern: "^_" }],
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
    },
  },
  {
    files: ["src/**/*.jsx"],
    ignores: ["**/*.test.jsx"],
    rules: { "react-refresh/only-export-components": ["warn", { allowConstantExport: true }] },
  },
  {
    files: ["*.config.js", "scripts/**/*.{js,cjs}"],
    languageOptions: { globals: { ...globals.node } },
  },
];
