import fs from 'node:fs/promises';
import path from 'node:path';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';
const outputDir=process.env.REPORT_TEMPLATE_OUTPUT || path.resolve('outputs/jm-report-template');
const wb=Workbook.create();
const specs=[
 ['Ringkasan',['Indikator','Nilai','Keterangan'],[32,27,76],[]],
 ['Harian',['Tanggal','Transaksi','Item terjual','Omzet bersih','Tunai','QRIS','Kasbon lama','Pengeluaran','Penjualan - pengeluaran'],[16,15,16,22,22,22,22,22,30],[3,4,5,6,7,8]],
 ['Best Seller',['Peringkat','Produk','Item terjual','Subtotal item','Jumlah nota','Porsi item'],[13,36,16,24,18,16],[3]],
 ['Tunai',['Nomor nota','Tanggal / waktu WIB','Subtotal','Diskon','Total penjualan','Uang diterima','Kembalian','Metode','Item terjual','Menu'],[31,24,23,21,24,24,23,16,17,55],[2,3,4,5,6]],
 ['QRIS',['Nomor nota','Tanggal / waktu WIB','Subtotal','Diskon','Total penjualan','Dibayar','Kembalian','Metode','Item terjual','Menu'],[31,24,23,21,24,24,23,16,17,55],[2,3,4,5,6]],
 ['Pengeluaran',['ID catatan','Tanggal','Kategori','Keterangan','Nominal','Pencatat','Status'],[16,17,24,50,24,17,16],[4]],
 ['Transaksi Lain',['Nomor nota','Tanggal / waktu WIB','Subtotal','Diskon','Total penjualan','Dibayar','Kembalian','Metode','Item terjual','Menu','Status'],[31,24,23,21,24,24,23,16,17,55,16],[2,3,4,5,6]],
];
const labels=[['Omzet bersih','Penjualan sah setelah diskon nota.'],['Transaksi sah','Tidak termasuk transaksi batal.'],['Item terjual','Jumlah unit dalam transaksi sah.'],['Penjualan Tunai','Total penjualan, bukan uang diserahkan sebelum kembalian.'],['Penjualan QRIS','QRIS dikonfirmasi manual oleh kasir.'],['Kasbon lama','Hanya catatan dari versi terdahulu.'],['Pengeluaran aktif','Tidak termasuk catatan pengeluaran batal.'],['Penjualan - pengeluaran','Bukan laba bersih; tidak menghitung HPP atau perubahan persediaan.'],['Rata-rata nota','Omzet bersih dibagi jumlah transaksi sah.'],['Best seller','Peringkat berdasarkan jumlah unit, lalu subtotal item.'],['Item best seller','Jumlah unit produk peringkat pertama.'],['Tanggal teramai','Transaksi terbanyak; seri: omzet terbesar, lalu tanggal awal.'],['Transaksi tanggal teramai','Hanya transaksi sah.'],['Transaksi batal','Dicantumkan pada Transaksi Lain dan dikeluarkan dari omzet.']];
for(const [name,headers,widths,money] of specs){
 const sh=wb.worksheets.add(name);sh.showGridLines=false;sh.tabColor=name==='Ringkasan'?'#176344':name==='QRIS'?'#356D91':name==='Tunai'?'#B48436':'#DCE7D4';
 const last=String.fromCharCode(64+headers.length);
 sh.getRange(`A1:${last}${name==='Ringkasan'?24:10}`).format.font={name:'Arial',size:11,color:'#243B30'};
 sh.getRange(`A1:${last}${name==='Ringkasan'?24:10}`).format.rowHeight=24;
 sh.getRange(`A1:${last}${name==='Ringkasan'?24:10}`).format.verticalAlignment='center';
 widths.forEach((w,i)=>sh.getRange(`${String.fromCharCode(65+i)}1:${String.fromCharCode(65+i)}24`).format.columnWidth=w);
 sh.getRange('A2').values=[[name==='Ringkasan'?'Laporan penjualan':name==='Transaksi Lain'?'Transaksi lain dan batal':name]];
 sh.getRange(`A2:${last}2`).format.rowHeight=31;sh.getRange('A2').format.font={name:'Arial',size:16,bold:true,color:'#176344'};
 sh.getRange('A3').values=[['JusMerdeka']];sh.getRange('A3').format.font={name:'Arial',size:11,bold:true};
 sh.getRange('A4').values=[['Periode dipilih saat ekspor. Waktu Indonesia Barat.']];
 sh.getRange('A5').values=[[name==='Best Seller'?'Subtotal item sebelum diskon nota. Sumber: transaksi SQLite pada HP kasir.':'Sumber: catatan SQLite pada HP kasir. Semua nominal dalam rupiah.']];
 sh.getRange(`A5:${last}5`).format.font={name:'Arial',size:10,color:'#748170',italic:true};
 sh.getRange(`A6:${last}6`).format.rowHeight=10;
 sh.getRange(`A7:${last}7`).values=[headers];sh.getRange(`A7:${last}7`).format={fill:'#176344',font:{name:'Arial',size:11,bold:true,color:'#FFFFFF'},rowHeight:34,wrapText:true,horizontalAlignment:'center',verticalAlignment:'center'};
 if(name==='QRIS')sh.getRange(`A7:${last}7`).format.fill='#356D91';
 if(name==='Tunai')sh.getRange(`A7:${last}7`).format.fill='#A87424';
 if(name==='Ringkasan'){
  labels.forEach(([label,note],i)=>{const r=i+8;sh.getRange(`A${r}:C${r}`).values=[[label,0,'  '+note]];sh.getRange(`C${r}`).format.wrapText=true;sh.getRange(`A${r}:C${r}`).format.rowHeight=36;if(i%2)sh.getRange(`A${r}:C${r}`).format.fill='#F1F6EC';});
  for(const r of [8,11,12,13,14,15,16])sh.getRange(`B${r}`).setNumberFormat('"Rp" #,##0');
  for(const r of [9,10,18,20,21])sh.getRange(`B${r}`).setNumberFormat('#,##0');
  sh.getRange('B17').values=[['Belum ada penjualan']];sh.getRange('B19').values=[['Belum ada penjualan']];sh.getRange('B19').setNumberFormat('dd mmm yyyy');
  sh.getRange('B17').format.wrapText=true;
  sh.getRange('A15:C15').format.fill='#E6EEDC';sh.getRange('A15:B15').format.font.bold=true;
 }else{
  for(const r of [8,9]){sh.getRange(`A${r}:${last}${r}`).values=[headers.map((_,i)=>money.includes(i)?0:'')];if(r===9)sh.getRange(`A${r}:${last}${r}`).format.fill='#F1F6EC';}
  for(const c of money)sh.getRange(`${String.fromCharCode(65+c)}8:${String.fromCharCode(65+c)}9`).setNumberFormat('"Rp" #,##0');
  if(name==='Harian')sh.getRange('A8:A9').setNumberFormat('dd mmm yyyy');
  if(name==='Tunai'||name==='QRIS'||name==='Transaksi Lain')sh.getRange('B8:B9').setNumberFormat('dd mmm yyyy hh:mm');
  if(name==='Pengeluaran')sh.getRange('B8:B9').setNumberFormat('dd mmm yyyy');
  if(name==='Best Seller')sh.getRange('F8:F9').setNumberFormat('0.0%');
  if(name==='Best Seller')sh.getRange('B8:B9').format.wrapText=true;
  if(name==='Tunai'||name==='QRIS'||name==='Transaksi Lain')sh.getRange('J8:J9').format.wrapText=true;
  if(name==='Pengeluaran')sh.getRange('D8:D9').format.wrapText=true;
  sh.freezePanes.freezeRows(7);
 }
}
wb.recalculate();
console.log((await wb.inspect({kind:'table',range:'Ringkasan!A7:C21',include:'values',tableMaxRows:4,maxChars:1500})).ndjson);
await fs.mkdir(outputDir,{recursive:true});
for(const [name] of specs){const preview=await wb.render({sheetName:name,range:name==='Ringkasan'?'A1:C21':`A1:${name==='Harian'?'I':name==='Tunai'||name==='QRIS'?'J':name==='Transaksi Lain'?'K':name==='Pengeluaran'?'G':'F'}10`,scale:1,format:'png'});await fs.writeFile(`${outputDir}/${name.replaceAll(' ','-')}.png`,new Uint8Array(await preview.arrayBuffer()));}
await (await SpreadsheetFile.exportXlsx(wb)).save(`${outputDir}/report-template.xlsx`);
