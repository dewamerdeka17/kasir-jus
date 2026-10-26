const {chromium}=require('playwright');
(async()=>{
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:390,height:844}});
const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
await page.addInitScript(()=>{
 const state={products:[{id:1,name:'Jus Mangga',category:'Jus',price:10000,cost:4000,stock:5}],settings:{name:'JusMerdeka',address:'Gudo',phone:'',footer:'Terima kasih'},printerName:'Belum dipilih'};
 const sales=[],debts=[];let role='';
 window.NativePOS={request(raw){const p=JSON.parse(raw);let data=true;
 if(!['login','logout','session'].includes(p.action)&&!role)return JSON.stringify({ok:false,error:'Silakan login terlebih dahulu.'});
 if(role==='CASHIER'&&['saveProduct','archiveProduct','cancel','report','saveSettings','backup','restore'].includes(p.action))return JSON.stringify({ok:false,error:'Akses ini hanya untuk admin.'});
 switch(p.action){
 case 'session':data={role};break;
 case 'login':if((p.role==='ADMIN'&&p.pin==='654321')||(p.role==='CASHIER'&&p.pin==='123456')){role=p.role;data={role};}else return JSON.stringify({ok:false,error:'PIN salah untuk akun yang dipilih.'});break;
 case 'logout':role='';break;
 case 'state':data={...state,role};break;case 'history':data=sales;break;case 'checkout':{
 let s=sales.find(s=>s.requestId===p.requestId);if(!s){s={id:sales.length+1,requestId:p.requestId,invoice:'JM-TEST-000001',created_at:'2026-10-26T10:00:00+07:00',total:p.items.reduce((s,l)=>s+l.qty*10000,0)-p.discount,paid:p.paid,cancelled:0,receipt:'JusMerdeka\nJus Mangga\nTOTAL: Rp19.000',payment_method:p.paymentMethod,customer:p.customer};sales.push(s);if(p.paymentMethod==='CREDIT')debts.push({sale_id:s.id,invoice:s.invoice,customer:p.customer,phone:p.phone,total:s.total,paid:0,payments:[]});state.products[0].stock-=p.items[0].qty;}data=s;break;}
 case 'debts':data=debts;break;
 case 'debt':data=debts.find(d=>d.sale_id===p.id);break;
 case 'payDebt':{const d=debts.find(d=>d.sale_id===p.id);if(!d.payments.some(x=>x.request_id===p.requestId)){if(p.amount>d.total-d.paid)return JSON.stringify({ok:false,error:'Pembayaran berlebih'});d.paid+=p.amount;d.payments.push({request_id:p.requestId,amount:p.amount,created_at:'2026-10-26T11:00:00+07:00'});}data=d;break;}
 case 'sale':data=sales.find(s=>s.id===p.id);break;
 case 'report':data={count:sales.length,revenue:sales.reduce((s,l)=>s+l.total,0),qty:2,profit:11000};break;
 case 'saveProduct':state.products.push({...p.product,id:state.products.length+1});data=state.products.length;break;
 }
 return JSON.stringify({ok:true,data});}};
 localStorage.clear();
});
await page.goto(require('node:url').pathToFileURL(require('node:path').resolve(__dirname,'../app/src/main/assets/pos/index.html')).href);
await page.getByRole('heading',{name:'Masuk ke JusMerdeka'}).waitFor();
if(await page.locator('nav').isVisible())throw Error('Navigation visible before login');
await page.getByRole('button',{name:'Admin',exact:true}).click();
await page.locator('#login-pin').fill('123456');await page.getByRole('button',{name:'Masuk sebagai Admin'}).click();
await page.getByRole('alert').filter({hasText:'PIN salah'}).waitFor();
await page.locator('#login-pin').fill('654321');await page.getByRole('button',{name:'Masuk sebagai Admin'}).click();
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
await page.waitForFunction(()=>document.querySelector('#modal').hidden,{},{timeout:3000}).catch(async e=>{
  console.error('Product form diagnostics:',await page.locator('#modal').innerText(),await page.locator('#notice').innerText(),errors);
  console.error('Invalid inputs:',await page.locator('#product-form input:invalid').evaluateAll(inputs=>inputs.map(i=>({name:i.name,value:i.value,message:i.validationMessage}))));
  throw e;
});
if(await page.locator('#content img').count())throw Error('Unsafe product HTML');
await page.locator('nav [data-tab="laporan"]').click();await page.getByText('Rp19.000',{exact:true}).waitFor();
await page.locator('nav [data-tab="pengaturan"]').click();await page.getByRole('heading',{name:'Printer Bluetooth'}).waitFor();
await page.locator('#logout').click();
await page.getByRole('button',{name:'Kasir',exact:true}).click();
await page.locator('#login-pin').fill('123456');await page.getByRole('button',{name:'Masuk sebagai Kasir'}).click();
const visibleTabs=await page.locator('nav button:visible').evaluateAll(bs=>bs.map(b=>b.dataset.tab));
if(JSON.stringify(visibleTabs)!==JSON.stringify(['kasir','kasbon','riwayat']))throw Error('Cashier sees wrong navigation: '+visibleTabs);
const denied=await page.evaluate(()=>{try{api('report',{day:'2026-10-26'});return false;}catch{return true;}});if(!denied)throw Error('Cashier accessed report');
await page.getByRole('button',{name:/Jus Mangga/}).click();
await page.locator('#payment-method').selectOption('CREDIT');await page.locator('#customer').fill('Bu Ani');
await page.getByRole('button',{name:'Bayar & simpan'}).click();await page.getByRole('heading',{name:'Transaksi tersimpan'}).waitFor();
if(await page.getByRole('button',{name:/Batalkan transaksi/}).count())throw Error('Cashier sees cancellation');
await page.getByRole('button',{name:'Pilih printer Bluetooth'}).waitFor();
await page.getByRole('button',{name:'Tutup',exact:true}).click();await page.locator('nav [data-tab="kasbon"]').click();
await page.getByRole('button',{name:/Bu Ani/}).click();await page.locator('#debt-form [name=amount]').fill('4000');
await page.getByRole('button',{name:'Simpan pembayaran kasbon'}).click();
await page.waitForFunction(()=>document.querySelector('#debt-form [name=amount]')?.value==='6000');
await page.getByRole('button',{name:'Simpan pembayaran kasbon'}).click();await page.getByText('Kasbon sudah lunas',{exact:true}).waitFor();
if(await page.evaluate(()=>localStorage.getItem('jm-debt-pending'))!==null)throw Error('Debt recovery key not cleared');
await page.getByRole('button',{name:'Tutup',exact:true}).click();
await page.screenshot({path:process.env.UI_SCREENSHOT||'/tmp/jm-mobile.png',fullPage:true});
await page.locator('#logout').click();await page.getByRole('heading',{name:'Masuk ke JusMerdeka'}).waitFor();
if(await page.locator('nav').isVisible())throw Error('Menu remained after logout');
if(errors.length)throw Error(errors.join('\n'));
console.log('PASS: PIN login/error/logout, exact cashier tabs and denied report, offline POS/change/history, product HTML escaping, printer access, credit sale and partial/full repayment; no page errors.');
await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
