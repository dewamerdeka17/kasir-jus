const {chromium}=require('playwright');
(async()=>{
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:390,height:844}});
const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
await page.addInitScript(()=>{
 const state={products:[{id:1,name:'Jus Mangga',category:'Jus',price:10000,cost:4000,stock:5}],settings:{name:'JusMerdeka',address:'Gudo',phone:'',footer:'Terima kasih'},printerName:'Belum dipilih'};
 const sales=[];
 window.NativePOS={request(raw){const p=JSON.parse(raw);let data=true;
 switch(p.action){case 'state':data=state;break;case 'history':data=sales;break;case 'checkout':{
 let s=sales.find(s=>s.requestId===p.requestId);if(!s){s={id:sales.length+1,requestId:p.requestId,invoice:'JM-TEST-000001',created_at:'2026-10-26T10:00:00+07:00',total:p.items.reduce((s,l)=>s+l.qty*10000,0)-p.discount,paid:p.paid,cancelled:0,receipt:'JusMerdeka\nJus Mangga\nTOTAL: Rp19.000'};sales.push(s);state.products[0].stock-=p.items[0].qty;}data=s;break;}
 case 'sale':data=sales.find(s=>s.id===p.id);break;
 case 'report':data={count:sales.length,revenue:sales.reduce((s,l)=>s+l.total,0),qty:2,profit:11000};break;
 case 'saveProduct':state.products.push({...p.product,id:state.products.length+1});data=state.products.length;break;
 }
 return JSON.stringify({ok:true,data});}};
 localStorage.clear();
});
await page.goto(require('node:url').pathToFileURL(require('node:path').resolve(__dirname,'../app/src/main/assets/pos/index.html')).href);
await page.getByRole('button',{name:/Jus Mangga/}).click();
await page.getByRole('button',{name:'Tambah jumlah',exact:true}).click();
await page.locator('#discount').fill('1000');await page.locator('#paid').fill('20000');
if(await page.locator('#total').innerText()!=='Rp19.000')throw Error('Wrong total');
if(await page.locator('#change').innerText()!=='Rp1.000')throw Error('Wrong change');
await page.getByRole('button',{name:'Bayar & simpan'}).click();
await page.getByRole('heading',{name:'Transaksi tersimpan'}).waitFor();
if(await page.evaluate(()=>localStorage.getItem('jm-pending'))!==null)throw Error('Recovery key not cleared');
await page.getByRole('button',{name:'Tutup',exact:true}).click();
await page.locator('nav [data-tab="riwayat"]').click();await page.getByRole('button',{name:/JM-TEST/}).waitFor();
await page.locator('nav [data-tab="produk"]').click();await page.getByRole('button',{name:'+ Produk'}).click();
await page.locator('[name=name]').fill('<img src=x onerror=alert(1)>');await page.locator('[name=price]').fill('12000');
await page.getByRole('button',{name:'Simpan produk'}).click();
if(await page.locator('#content img').count())throw Error('Unsafe product HTML');
await page.locator('nav [data-tab="laporan"]').click();await page.getByText('Rp19.000',{exact:true}).waitFor();
await page.locator('nav [data-tab="pengaturan"]').click();await page.getByRole('heading',{name:'Printer Bluetooth'}).waitFor();
await page.screenshot({path:process.env.UI_SCREENSHOT||'/tmp/jm-mobile.png',fullPage:true});
if(errors.length)throw Error(errors.join('\n'));
console.log('PASS: bundled file/CSP, mobile POS, change, checkout, recovery-key cleanup, history, product HTML escaping, report, printer settings; no page errors.');
await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
