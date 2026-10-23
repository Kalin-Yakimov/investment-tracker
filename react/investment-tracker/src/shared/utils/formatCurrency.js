export const formatCurrency = (value) => {
  if (value == null || Number.isNaN(Number(value))) return "-";
  return `€${Number(value).toFixed(2)}`;
};
