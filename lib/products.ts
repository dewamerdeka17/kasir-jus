export function productPrice(sellingPrice: number, discountPercent: number) {
  const discount = Math.max(0, Math.min(100, discountPercent));
  return Math.max(0, Math.round(sellingPrice * (100 - discount) / 100));
}
