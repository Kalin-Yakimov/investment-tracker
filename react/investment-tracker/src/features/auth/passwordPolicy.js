// Mirrors backend PasswordPolicy; server-side validation remains authoritative.
export const MIN_NEW_PASSWORD_CHARACTERS = 10;
export const MAX_PASSWORD_BYTES = 72;
export const PASSWORD_HINT = `Use at least ${MIN_NEW_PASSWORD_CHARACTERS} characters. Spaces and passphrases are welcome.`;
const TOO_LONG = `Password is too long. Use at most ${MAX_PASSWORD_BYTES} UTF-8 bytes (non-English characters may use more than one byte).`;

export function passwordError(password, { newPassword = true } = {}) {
  if (typeof password !== "string" || password.trim().length === 0) return "Password is required.";
  if (password.length > MAX_PASSWORD_BYTES) {
    return TOO_LONG;
  }
  if (!password.isWellFormed()) return "Password contains invalid Unicode text.";
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return TOO_LONG;
  }
  if (newPassword && Array.from(password).length < MIN_NEW_PASSWORD_CHARACTERS) {
    return `Password must be at least ${MIN_NEW_PASSWORD_CHARACTERS} characters long.`;
  }
  return "";
}
