import { formatCalendarDate, parseCalendarDate } from "../../../shared/utils/calendarDate";

export function portfolioFormError(item, requireCatalogue = false) {
  if (requireCatalogue && !item.csItemId) return "Please select a valid CS item from the search dropdown menu.";
  if (!item.itemName?.trim() || !["BOUGHT", "SOLD"].includes(item.itemStatus) || !parseCalendarDate(item.date)) return "Please fill out the name, status and a valid date.";
  const quantity = String(item.quantity ?? "").trim();
  if (!/^\d+$/.test(quantity) || Number(quantity) < 1 || Number(quantity) > 2147483647) return "Quantity must be a whole number between 1 and 2147483647.";
  const price = String(item.price ?? "").trim();
  if (!/^\d{1,36}(\.\d{1,2})?$/.test(price)) return "Price must be zero or greater, with at most 2 decimal places and 36 integer digits.";
  return "";
}

export function portfolioFormPayload(item) {
  return { itemName: item.itemName.trim(), csItemId: item.csItemId || null, itemStatus: item.itemStatus,
    date: formatCalendarDate(item.date, "."), quantity: Number(item.quantity), price: String(item.price).trim() };
}
