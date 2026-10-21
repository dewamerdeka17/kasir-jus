"use client";

import { Download, Printer } from "lucide-react";

export function ReceiptActions() { return <div className="header-actions no-print"><button className="button ghost" onClick={()=>window.print()}><Printer size={17}/> Cetak Struk</button><button className="button secondary" onClick={()=>window.print()}><Download size={17}/> Simpan PDF</button></div>; }
