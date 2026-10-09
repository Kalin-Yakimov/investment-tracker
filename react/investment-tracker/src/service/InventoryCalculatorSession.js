const STORAGE_KEY = "investment-tracker.inventory-calculator.v1";

let memoryEntry = null;

const ownerKey = (userId) => userId == null ? null : String(userId);

const isValidEntry = (entry, userId) =>
  entry?.ownerId === userId &&
  typeof entry.steamUrl === "string" &&
  entry.result &&
  typeof entry.result === "object" &&
  !Array.isArray(entry.result);

export function clearInventoryCalculatorSession() {
  memoryEntry = null;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // The in-memory copy is still cleared if browser storage is unavailable.
  }
}

export function loadInventoryCalculatorSession(userId) {
  const ownerId = ownerKey(userId);
  if (!ownerId) {
    clearInventoryCalculatorSession();
    return null;
  }

  if (memoryEntry) {
    if (isValidEntry(memoryEntry, ownerId)) return memoryEntry;
    memoryEntry = null;
  }

  try {
    const saved = window.sessionStorage.getItem(STORAGE_KEY);
    if (!saved) return null;

    const entry = JSON.parse(saved);
    if (isValidEntry(entry, ownerId)) {
      memoryEntry = entry;
      return entry;
    }
  } catch {
    // Invalid or unavailable browser storage should not prevent calculation.
  }

  clearInventoryCalculatorSession();
  return null;
}

export function saveInventoryCalculatorSession(userId, steamUrl, result) {
  const ownerId = ownerKey(userId);
  if (!ownerId) return;

  memoryEntry = { ownerId, steamUrl, result };
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(memoryEntry));
  } catch {
    // Keep the result in memory for route changes if browser storage is full.
  }
}
