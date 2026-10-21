const rupiahFormatter = new Intl.NumberFormat("id-ID", { style: "currency", currency: "IDR", maximumFractionDigits: 0 });
const dateTimeFormatter = new Intl.DateTimeFormat("id-ID", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Jakarta" });
const localDateFormatter = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta" });

export const rupiah = (value: number) => rupiahFormatter.format(value);

export const dateTime = (value: Date | string) => dateTimeFormatter.format(new Date(value));

export const localDateKey = (date = new Date()) =>
  localDateFormatter.format(date).replaceAll("-", "");
