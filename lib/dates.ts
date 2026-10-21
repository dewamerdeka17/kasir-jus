export function jakartaDayRange(date = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta", year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
  const start = new Date(`${parts}T00:00:00+07:00`);
  return { start, end: new Date(start.getTime() + 86_400_000), key: parts.replaceAll("-", "") };
}

export function monthRange(date = new Date()) {
  const text = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta", year: "numeric", month: "2-digit" }).format(date);
  const [year, month] = text.split("-").map(Number);
  return { start: new Date(`${text}-01T00:00:00+07:00`), end: new Date(`${month === 12 ? year + 1 : year}-${String(month === 12 ? 1 : month + 1).padStart(2, "0")}-01T00:00:00+07:00`) };
}
