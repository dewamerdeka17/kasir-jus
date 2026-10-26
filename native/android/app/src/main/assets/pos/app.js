'use strict';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>'Rp'+Number(n).toLocaleString('id-ID');
const day=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Jakarta',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
let state={products:[],settings:{}},tab='kasir',cart=[],discount=0,paid='',query='',paying=false,noticeTimer;
function api(action,data={}) {
  if(!window.NativePOS) throw Error('Buka kasir melalui APK Android.');
  const r=JSON.parse(NativePOS.request(JSON.stringify({action,...data})));
  if(!r.ok) throw Error(r.error); return r.data;
}
function notify(text){$('#notice').textContent=text;$('#notice').hidden=false;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>$('#notice').hidden=true,6500);}
function persist(){localStorage.setItem('jm-cart',JSON.stringify({cart,discount,paid}));}
function load(){state=api('state');}
function product(id){return state.products.find(p=>p.id===id);}
function subtotal(){return cart.reduce((n,l)=>n+(product(l.id)?.price||0)*l.qty,0);}
function total(){return Math.max(0,subtotal()-discount);}
function modal(title,body){$('#modal-content').innerHTML=`<h2 id="modal-title">${esc(title)}</h2>${body}`;$('#modal').hidden=false;$('#modal').querySelector('input,button')?.focus();}
function close(){if(!paying){$('#modal').hidden=true;$('#modal-content').innerHTML='';}}
function navigate(next){if(paying)return;tab=next;query='';render();}
function render(){
  const titles={kasir:'Kasir',produk:'Produk & stok',riwayat:'Riwayat',laporan:'Laporan',pengaturan:'Pengaturan'};
  $('h1').textContent=titles[tab];document.querySelectorAll('[data-tab]').forEach(b=>{b.classList.toggle('active',b.dataset.tab===tab);b.setAttribute('aria-current',b.dataset.tab===tab?'page':'false');});
  if(tab==='kasir') renderPOS();else if(tab==='produk') renderProducts();else if(tab==='riwayat') renderHistory();else if(tab==='laporan') renderReport();else renderSettings();
}
function cards(){
  const list=state.products.filter(p=>(p.name+' '+p.category).toLowerCase().includes(query.toLowerCase()));
  return list.map(p=>`<button class="product" data-action="add" data-id="${p.id}" ${p.stock<1?'disabled':''}><span class="muted">${esc(p.category||'Jus')}</span><strong>${esc(p.name)}</strong><span class="price">${money(p.price)}</span><span class="stock">Stok ${p.stock}</span></button>`).join('')||'<p class="empty">Belum ada produk yang cocok.</p>';
}
function cartHTML(){
 return cart.map(l=>{const p=product(l.id);return `<div class="line"><div class="row"><div><strong>${esc(p?.name||'Produk tidak tersedia')}</strong><p class="muted">${money(p?.price||0)} / item</p></div><strong>${money((p?.price||0)*l.qty)}</strong></div><div class="stepper"><button class="secondary" aria-label="Kurangi jumlah" data-action="minus" data-id="${l.id}">−</button><span>${l.qty}</span><button class="secondary" aria-label="Tambah jumlah" data-action="add" data-id="${l.id}">+</button></div></div>`;}).join('')||'<p class="empty">Pilih produk untuk mulai menjual.</p>';
}
function renderPOS(){
 $('#content').innerHTML=`<div class="pos-layout"><section><label id="search-label">Cari produk<input id="search" class="search" placeholder="Nama atau kategori" value="${esc(query)}" type="search"></label><div class="grid" id="products">${cards()}</div>${state.products.length?'':'<button class="wide spaced" data-tab="produk">Tambah produk pertama</button>'}</section><section class="checkout card"><div class="row"><h2>Pesanan</h2><button class="secondary" data-action="clear">Kosongkan</button></div><div id="cart">${cartHTML()}</div><label>Diskon nominal (Rp)<input id="discount" inputmode="numeric" type="number" min="0" step="1" value="${discount}"></label><div class="row line"><span>Subtotal</span><strong id="subtotal">${money(subtotal())}</strong></div><div class="row line"><strong>Total bayar</strong><span class="amount" id="total">${money(total())}</span></div><label>Uang tunai diterima (Rp)<input id="paid" inputmode="numeric" type="number" min="0" step="1" placeholder="Masukkan pembayaran" value="${esc(paid)}"></label><div class="row"><span>Kembalian</span><strong id="change">${money(Math.max(0,Number(paid)-total()))}</strong></div><button class="wide spaced" data-action="pay" ${cart.length?'':'disabled'}>Bayar & simpan</button><p class="muted">Tersimpan di HP. Cetak struk setelah pembayaran berhasil.</p></section></div>`;
}
function renderProducts(){
 $('#content').innerHTML=`<div class="row"><p class="muted">Harga dan stok tersimpan di HP ini.</p><button data-action="newProduct">+ Produk</button></div>${state.products.map(p=>`<section class="card row"><div><h3>${esc(p.name)}</h3><span class="muted">${esc(p.category)} · Stok ${p.stock}</span><p>${money(p.price)} <span class="muted">/ HPP ${money(p.cost)}</span></p></div><button class="secondary" data-action="editProduct" data-id="${p.id}">Ubah</button></section>`).join('')||'<div class="card empty">Tambahkan produk, harga, HPP, dan stok awal.</div>'}`;
}
function editProduct(id=0){
 const p=product(id)||{id:0,name:'',category:'Jus',price:'',cost:0,stock:0};
 modal(id?'Ubah produk & stok':'Tambah produk',`<form id="product-form"><input type="hidden" name="id" value="${p.id}"><label>Nama produk<input name="name" required maxlength="100" value="${esc(p.name)}"></label><label>Kategori<input name="category" maxlength="60" value="${esc(p.category)}"></label>${[['price','Harga jual (Rp)',p.price,1000000000],['cost','HPP per item (Rp)',p.cost,1000000000],['stock','Stok tersedia',p.stock,1000000]].map(([n,l,v,max])=>`<label>${l}<input name="${n}" type="number" inputmode="numeric" min="0" max="${max}" step="1" required value="${v}"></label>`).join('')}<p class="muted">Stok diisi sesuai jumlah yang tersedia sekarang.</p><button type="submit" class="wide">Simpan produk</button></form>${id?`<button class="danger wide" data-action="archive" data-id="${id}">Arsipkan produk</button>`:''}`);
}
function renderHistory(){
 const rows=api('history');
 $('#content').innerHTML='<p class="muted">500 transaksi terakhir. Seluruh transaksi tetap masuk laporan dan backup.</p>'+rows.map(s=>`<button class="card wide row secondary" data-action="sale" data-id="${s.id}"><span>${esc(s.invoice)}<br><span class="muted">${esc(s.created_at.replace('T',' '))}${s.cancelled?' · DIBATALKAN':''}</span></span><strong>${money(s.total)}</strong></button>`).join('');
 if(!rows.length)$('#content').innerHTML+='<div class="card empty">Belum ada transaksi.</div>';
}
function showSale(id){
 const s=api('sale',{id});
 modal(s.cancelled?'Transaksi dibatalkan':'Transaksi tersimpan',`<p><strong>${esc(s.invoice)}</strong></p><p>Total ${money(s.total)} · Kembali ${money(s.paid-s.total)}</p>${s.cancelled?'<p class="error">Stok transaksi ini sudah dikembalikan.</p>':''}<pre>${esc(s.receipt)}</pre><label>Ukuran kertas<select id="paper"><option value="32">58 mm (32 karakter)</option><option value="48">80 mm (48 karakter)</option></select></label><button class="wide" data-action="print" data-id="${s.id}">Cetak struk Bluetooth</button>${s.cancelled?'':`<button class="danger wide" data-action="cancel" data-id="${s.id}">Batalkan transaksi & kembalikan stok</button>`}`);
}
function renderReport(date=day()){
 const r=api('report',{day:date});
 $('#content').innerHTML=`<label>Tanggal (WIB)<input id="report-day" type="date" value="${esc(date)}"></label><div class="stats">${[['Omzet',money(r.revenue)],['Transaksi',r.count],['Produk terjual',r.qty],['Laba kotor',money(r.profit)]].map(([l,v])=>`<section class="card"><span class="muted">${l}</span><strong>${v}</strong></section>`).join('')}</div><p class="muted">Laba kotor = penjualan setelah diskon dikurangi HPP saat transaksi. Belum dikurangi pengeluaran operasional. Transaksi batal tidak dihitung.</p>`;
}
function renderSettings(){
 const s=state.settings;
 $('#content').innerHTML=`<section class="card"><h2>Identitas toko</h2><form id="settings-form">${[['name','Nama toko'],['address','Alamat'],['phone','Nomor pemesanan'],['footer','Pesan bawah struk']].map(([n,l])=>`<label>${l}<input name="${n}" maxlength="160" ${n==='name'?'required':''} value="${esc(s[n])}"></label>`).join('')}<button type="submit" class="wide">Simpan identitas</button></form></section><section class="card"><h2>Printer Bluetooth</h2><p id="printer-name">${esc(state.printerName)}</p><p class="muted">Pasangkan printer di pengaturan Bluetooth HP. Mendukung printer ESC/POS Bluetooth Classic/SPP; BLE dan printer khusus merek belum didukung.</p><button data-action="choosePrinter">Pilih printer</button> <button class="secondary" data-action="testPrint">Tes cetak</button></section><section class="card"><h2>Backup data</h2><p class="hint">Data tersimpan hanya di HP ini. Simpan backup rutin ke kartu memori, perangkat lain, atau penyimpanan cloud. Menghapus data aplikasi akan menghapus catatan kasir.</p><button class="wide" data-action="backup">Simpan backup (.json)</button><button class="danger wide spaced" data-action="restore">Pulihkan dari backup</button><p class="muted">Pemulihan mengganti data saat ini. Backup mencakup produk, stok, transaksi, dan identitas toko; keranjang serta pilihan printer tidak disertakan.</p></section><p class="muted">JusMerdeka Offline 2.0 · Satu HP kasir · Tunai · Belum tersinkron dengan website. Data awal website tidak otomatis masuk.</p>`;
}
function add(id){
 const p=product(id);if(!p)throw Error('Produk tidak tersedia.');
 const l=cart.find(x=>x.id===id);if((l?.qty||0)>=Math.min(999,p.stock))throw Error('Jumlah sudah mencapai stok yang tersedia.');
 if(l)l.qty++;else cart.push({id,qty:1});persist();renderPOS();
}
function pay(){
 if(paying)return;
 if(!cart.length)throw Error('Keranjang kosong.');
 const amount=Number(paid);
 if(!Number.isSafeInteger(discount)||discount<0||discount>subtotal())throw Error('Diskon harus angka bulat dan tidak melebihi subtotal.');
 if(paid===''||!Number.isSafeInteger(amount)||amount<total()||amount>100000000000000)throw Error('Masukkan uang tunai yang cukup dalam rupiah bulat.');
 const requestId='sale-'+Date.now()+'-'+Array.from(crypto.getRandomValues(new Uint32Array(4)),n=>n.toString(16)).join('-');
 const payload={requestId,items:cart.map(x=>({...x})),discount,paid:amount};
 localStorage.setItem('jm-pending',JSON.stringify(payload)); // Durable before native checkout; retry uses the exact same request id.
 completePayment(payload);
}
function completePayment(payload){
 paying=true;modal('Menyimpan transaksi','<p>Tunggu sebentar…</p>');
 setTimeout(()=>{
   try{
     const sale=api('checkout',payload);
     // Save the cleared cart before dropping recovery information.
     cart=[];discount=0;paid='';persist();localStorage.removeItem('jm-pending');load();tab='kasir';render();showSale(sale.id);
   }catch(e){
     // Keep the pending request after any uncertain failure; it can be retried safely.
     modal('Periksa pembayaran',`<p class="error">${esc(e.message)}</p><p>Gunakan Cek ulang untuk memastikan transaksi tersimpan. Jika ditolak karena stok atau uang kurang, kembali dan ubah pesanan.</p><button class="wide" data-action="retryPayment">Cek ulang pembayaran</button><button class="secondary wide" data-action="discardPending">Kembali ke pesanan</button>`);
   }finally{paying=false;}
 },30);
}
function pendingDialog(){modal('Pulihkan pembayaran', '<p>Ada pembayaran yang belum selesai diperiksa. Cek ulang sebelum membuat transaksi baru agar tidak tercatat dua kali.</p><button class="wide" data-action="retryPayment">Cek ulang pembayaran</button>');}
window.nativeResult=(text,reload)=>{notify(text);if(reload){localStorage.removeItem('jm-cart');localStorage.removeItem('jm-pending');cart=[];discount=0;paid='';close();load();render();}else{load();if(tab==='pengaturan')renderSettings();}};
window.goHome=()=>{if(!$('#modal').hidden)close();else navigate('kasir');};
document.addEventListener('click',event=>{
 const b=event.target.closest('button');if(!b||b.disabled||paying)return;
 try{
  if(localStorage.getItem('jm-pending')&&!['retryPayment','discardPending'].includes(b.dataset.action)){pendingDialog();return;}
  if(b.dataset.tab){navigate(b.dataset.tab);return;}
  const id=Number(b.dataset.id);
  switch(b.dataset.action){
   case 'close':close();break;
   case 'add':add(id);break;
   case 'minus':{const l=cart.find(x=>x.id===id);if(l&&--l.qty===0)cart=cart.filter(x=>x.id!==id);persist();renderPOS();break;}
   case 'clear':if(cart.length&&confirm('Kosongkan keranjang?')){cart=[];discount=0;paid='';persist();renderPOS();}break;
   case 'newProduct':editProduct();break;
   case 'editProduct':editProduct(id);break;
   case 'archive':if(confirm('Arsipkan produk? Riwayat penjualan tetap tersimpan.')){api('archiveProduct',{id});close();load();render();}break;
   case 'pay':pay();break;
   case 'sale':showSale(id);break;
   case 'cancel':if(confirm('Batalkan transaksi ini dan kembalikan stok?')){api('cancel',{id});load();render();showSale(id);}break;
   case 'print':api('print',{id,columns:Number($('#paper').value)});notify('Menghubungkan printer… Transaksi sudah tersimpan.');break;
   case 'retryPayment':{const p=localStorage.getItem('jm-pending');if(p)completePayment(JSON.parse(p));break;}
   case 'discardPending':{
     // Resolve the idempotency key before allowing a new payment. A saved transaction must be shown, never silently discarded.
     const p=JSON.parse(localStorage.getItem('jm-pending'));
     try{const saved=api('checkout',p);cart=[];discount=0;paid='';persist();localStorage.removeItem('jm-pending');load();render();showSale(saved.id);}
     catch(e){if(confirm('Pembayaran ditolak: '+e.message+'\nKembali untuk mengubah pesanan?')){localStorage.removeItem('jm-pending');close();load();render();}}break;
   }
   case 'choosePrinter':case 'testPrint':case 'backup':case 'restore':api(b.dataset.action);break;
  }
 }catch(e){notify(e.message);}
});
document.addEventListener('input',event=>{
 const el=event.target;
 try{
  if(el.id==='search'){query=el.value;$('#products').innerHTML=cards();}
  if(el.id==='discount'||el.id==='paid'){
   if(el.id==='discount')discount=Number(el.value);else paid=el.value;
   persist();$('#total').textContent=money(total());$('#change').textContent=money(Math.max(0,Number(paid)-total()));
  }
 }catch(e){notify(e.message);}
});
document.addEventListener('change',event=>{if(event.target.id==='report-day')try{renderReport(event.target.value);}catch(e){notify(e.message);}});
document.addEventListener('submit',event=>{
 event.preventDefault();try{
  const f=event.target;const data=Object.fromEntries(new FormData(f));
  if(f.matches('#product-form')){for(const key of ['id','price','cost','stock']){data[key]=Number(data[key]);if(!Number.isSafeInteger(data[key]))throw Error('Gunakan angka rupiah atau stok bulat.');}api('saveProduct',{product:data});close();load();render();notify('Produk disimpan.');}
  if(f.matches('#settings-form')){api('saveSettings',{settings:data});load();notify('Identitas toko disimpan.');}
 }catch(e){notify(e.message);}
});
try{
 load();
 try{const draft=JSON.parse(localStorage.getItem('jm-cart')||'null');if(draft){cart=Array.isArray(draft.cart)?draft.cart.filter(l=>Number.isSafeInteger(l.id)&&Number.isInteger(l.qty)&&l.qty>0&&l.qty<=999):[];discount=Number(draft.discount)||0;paid=String(draft.paid??'');}}catch{cart=[];}
 render();if(localStorage.getItem('jm-pending'))pendingDialog();
}catch(e){$('#content').innerHTML=`<div class="card error">${esc(e.message)}</div>`;}
