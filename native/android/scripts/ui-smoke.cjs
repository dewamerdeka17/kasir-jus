const {chromium}=require('playwright');
(async()=>{
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:390,height:844}});
const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
await page.addInitScript(()=>{
 const state={products:[{id:1,name:'Jus Mangga',category:'Jus',price:10000,cost:4000,stock:5}],settings:{name:'JusMerdeka',address:'Gudo',phone:'',footer:'Terima kasih'},printerName:'Belum dipilih'};
 const sales=[],expenses=[];let role='';
 window.NativePOS={request(raw){const p=JSON.parse(raw);let data=true;
 if(!['login','logout','session'].includes(p.action)&&!role)return JSON.stringify({ok:false,error:'Silakan login terlebih dahulu.'});
 if(role==='CASHIER'&&['saveProduct','archiveProduct','cancel','report','saveSettings','backup','restore'].includes(p.action))return JSON.stringify({ok:false,error:'Akses ini hanya untuk admin.'});
 switch(p.action){
 case 'session':data={role};break;
 case 'login':if((p.role==='ADMIN'&&p.pin==='654321')||(p.role==='CASHIER'&&p.pin==='123456')){role=p.role;data={role};}else return JSON.stringify({ok:false,error:'PIN salah untuk akun yang dipilih.'});break;
 case 'logout':role='';break;
 case 'state':data={...state,role};break;case 'history':data=sales;break;case 'checkout':{
 let s=sales.find(s=>s.requestId===p.requestId);if(!s){s={id:sales.length+1,requestId:p.requestId,invoice:'JM-TEST-000001',created_at:'2026-10-26T10:00:00+07:00',total:p.items.reduce((s,l)=>s+l.qty*10000,0)-p.discount,paid:p.paid,cancelled:0,receipt:'JusMerdeka\nJus Mangga\nTOTAL: Rp19.000',payment_method:p.paymentMethod,customer:p.customer};sales.push(s);state.products[0].stock-=p.items[0].qty;}data=s;break;}
 case 'expenses':{const list=expenses.filter(e=>e.expense_date===p.date&&(role==='ADMIN'||e.created_by==='CASHIER'));data={items:list,total:list.filter(e=>!e.cancelled).reduce((n,e)=>n+e.amount,0),count:list.filter(e=>!e.cancelled).length};break;}
 case 'addExpense':{let e=expenses.find(e=>e.request_id===p.expense.request_id);if(!e){e={...p.expense,id:expenses.length+1,created_by:role,created_at:'2026-10-26T11:00:00+07:00',cancelled:0};expenses.push(e);}data=e;break;}
 case 'sale':data=sales.find(s=>s.id===p.id);break;
 case 'report':data={count:sales.length,revenue:sales.reduce((s,l)=>s+l.total,0),qty:2,profit:11000,expenses:50000,profitAfterExpenses:-39000};break;
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
if(JSON.stringify(visibleTabs)!==JSON.stringify(['kasir','pengeluaran','riwayat']))throw Error('Cashier sees wrong navigation: '+visibleTabs);
const denied=await page.evaluate(()=>{try{api('report',{day:'2026-10-26'});return false;}catch{return true;}});if(!denied)throw Error('Cashier accessed report');
await page.getByRole('button',{name:/Jus Mangga/}).click();
await page.locator('#paid').fill('10000');
await page.getByRole('button',{name:'Bayar & simpan'}).click();await page.getByRole('heading',{name:'Transaksi tersimpan'}).waitFor();
if(await page.getByRole('button',{name:/Batalkan transaksi/}).count())throw Error('Cashier sees cancellation');
await page.getByRole('button',{name:'Pilih printer Bluetooth'}).waitFor();
await page.getByRole('button',{name:'Tutup',exact:true}).click();
await page.locator('nav [data-tab="pengeluaran"]').click();
await page.locator('#expense-form [name=amount]').fill('50000');await page.locator('#expense-form [name=description]').fill('Beli mangga 5 kg');
await page.getByRole('button',{name:'Simpan pengeluaran',exact:true}).click();
await page.getByRole('heading',{name:'Beli mangga 5 kg',exact:true}).waitFor();
if(await page.locator('.expense-overview strong').innerText()!=='Rp50.000')throw Error('Wrong expense total');
if(await page.locator('[data-action=cancelExpense]').count())throw Error('Cashier sees expense cancellation');
if(await page.evaluate(()=>localStorage.getItem('jm-expense-pending'))!==null)throw Error('Expense recovery key not cleared');
if(await page.locator('nav [data-tab=kasbon]').count())throw Error('Kasbon menu still exists');
await page.screenshot({path:process.env.UI_SCREENSHOT||'/tmp/jm-mobile.png',fullPage:true});
await page.locator('nav [data-tab="kasir"]').click();
await page.screenshot({path:(process.env.UI_SCREENSHOT||'/tmp/jm-mobile.png').replace('.png','-kasir.png'),fullPage:true});
await page.locator('#logout').click();await page.getByRole('heading',{name:'Masuk ke JusMerdeka'}).waitFor();
if(await page.locator('nav').isVisible())throw Error('Menu remained after logout');
if(errors.length)throw Error(errors.join('\n'));
console.log('PASS: PIN login/error/logout, exact cashier tabs and denied report, offline POS/change/history, product HTML escaping, printer access, cashier expense creation/total, removed kasbon menu; no page errors.');
await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
