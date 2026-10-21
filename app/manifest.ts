import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "Jus Merdeka Jumbo POS",
    short_name: "Jus Merdeka",
    description: "Aplikasi kasir, stok, dan laporan Jus Merdeka Jumbo",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "any",
    background_color: "#f6f8f5",
    theme_color: "#1b6b47",
    lang: "id-ID",
    categories: ["business", "finance", "productivity"],
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
      { src: "/icon-maskable.svg", sizes: "any", type: "image/svg+xml", purpose: "maskable" },
    ],
    shortcuts: [
      { name: "Buka Kasir", short_name: "Kasir", url: "/kasir", icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }] },
      { name: "Transaksi", short_name: "Transaksi", url: "/transaksi", icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }] },
    ],
  };
}
