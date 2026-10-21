"use client";
export default function ErrorPage({reset}:{reset:()=>void}){return <main className="standalone-message"><div>🧃</div><h1>Terjadi kesalahan</h1><p>Silakan coba lagi. Data transaksi yang sudah tersimpan tetap aman.</p><button onClick={reset} className="button primary">Coba lagi</button></main>}
