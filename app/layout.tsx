import type { Metadata } from "next";
import "./globals.css";
import { PwaRegister } from "@/components/pwa-register";

export const metadata: Metadata = { title: "Jus Merdeka Jumbo POS", description: "Kasir modern untuk usaha jus", manifest: "/manifest.webmanifest", icons: { icon: "/icon.svg" }, appleWebApp: { capable: true, statusBarStyle: "default", title: "Jus Merdeka" } };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="id"><body>{children}<PwaRegister/></body></html>;
}
