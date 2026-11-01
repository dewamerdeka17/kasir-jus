import type {Metadata} from 'next';
import './globals.css';
export const metadata:Metadata={title:'JusMerdeka · Pantau Toko',description:'Pantau penjualan, pengeluaran, dan stok JusMerdeka.',robots:{index:false,follow:false},icons:{icon:'/favicon.svg'}};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="id"><body>{children}</body></html>;}
