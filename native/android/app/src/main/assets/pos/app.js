'use strict';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>'Rp'+Number(n).toLocaleString('id-ID');
const day=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Jakarta',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
let role='',loginRole='',state={products:[],settings:{}},tab='kasir',cart=[],discount=0,paid='',paymentMethod='CASH',customer='',phone='',query='',selectedCategory='',expenseDate=day(),reportMode='DAY',reportAnchor=day(),paying=false,noticeTimer,previewTimer,printReceipt=false,qrisConfirmed=false,confirmedTotal=null;
function api(action,data={}) {
  if(!window.NativePOS) throw Error('Buka kasir melalui APK Android.');
  const r=JSON.parse(NativePOS.request(JSON.stringify({action,...data})));
  if(!r.ok) throw Error(r.error); return r.data;
}
function notify(text){$('#notice').textContent=text;$('#notice').hidden=false;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>$('#notice').hidden=true,6500);}
function persist(){localStorage.setItem('jm-cart',JSON.stringify({cart,discount,paid,paymentMethod,customer,phone,printReceipt}));}
function load(){state=api('state');role=state.role;}
const isAdmin=()=>role==='ADMIN';
function restoreCart(){try{const draft=JSON.parse(localStorage.getItem('jm-cart')||'null');if(draft){cart=Array.isArray(draft.cart)?draft.cart.filter(l=>Number.isSafeInteger(l.id)&&Number.isInteger(l.qty)&&l.qty>0&&l.qty<=999):[];discount=Number(draft.discount)||0;paid=String(draft.paid??'');paymentMethod=draft.paymentMethod==='QRIS'?'QRIS':'CASH';printReceipt=draft.printReceipt===true;customer=String(draft.customer||'');phone=String(draft.phone||'');}}catch{cart=[];}}
function renderLogin(){
 document.body.classList.remove('admin');$('h1').textContent='Selamat datang';$('nav').hidden=true;$('#logout').hidden=true;$('#modal').hidden=true;
 $('#content').innerHTML=`<section class="card login-card"><div class="login-mark">JM</div><h2>Masuk ke JusMerdeka</h2><p class="muted">Pilih akun, lalu masukkan PIN 6 digit.<br>Tetap bisa masuk tanpa internet.</p><div class="role-options">${[['CASHIER','Kasir'],['ADMIN','Admin']].map(([r,n])=>`<button class="${loginRole===r?'':'secondary'}" data-login-role="${r}" aria-pressed="${loginRole===r}">${n}</button>`).join('')}</div>${loginRole?`<form id="login-form"><label>PIN ${loginRole==='ADMIN'?'Admin':'Kasir'}<input id="login-pin" name="pin" type="password" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" autocomplete="off" required placeholder="••••••"></label><p id="login-error" class="error" role="alert" hidden></p><button type="submit" class="wide">Masuk sebagai ${loginRole==='ADMIN'?'Admin':'Kasir'}</button></form>`:''}</section>`;
 $('#login-pin')?.focus();
}
function product(id){return state.products.find(p=>p.id===id);}
function subtotal(){return cart.reduce((n,l)=>n+(product(l.id)?.price||0)*l.qty,0);}
function total(){return Math.max(0,subtotal()-discount);}
function modal(title,body){$('#modal-content').innerHTML=`<h2 id="modal-title">${esc(title)}</h2>${body}`;$('#modal').hidden=false;$('#modal').querySelector('input,button')?.focus();}
function close(){if(!paying){$('#modal').hidden=true;$('#modal-content').innerHTML='';}}
function navigate(next){if(paying)return;if(!isAdmin()&&!['kasir','pengeluaran','riwayat'].includes(next))throw Error('Akses ini hanya untuk admin.');tab=next;query='';selectedCategory='';render();}
function render(){
  if(!role){renderLogin();return;}
  if(!isAdmin()&&!['kasir','pengeluaran','riwayat'].includes(tab))tab='kasir';
  document.body.classList.toggle('admin',isAdmin());$('nav').hidden=false;$('#logout').hidden=false;$('#logout').textContent='Keluar · '+(isAdmin()?'Admin':'Kasir');
  document.querySelectorAll('nav [data-tab]').forEach(b=>b.hidden=!isAdmin()&&!['kasir','pengeluaran','riwayat'].includes(b.dataset.tab));
  const titles={kasir:'Kasir',pengeluaran:'Pengeluaran',produk:'Produk & stok',riwayat:'Riwayat',laporan:'Laporan',pengaturan:'Pengaturan'};
  $('h1').textContent=titles[tab];document.querySelectorAll('[data-tab]').forEach(b=>{b.classList.toggle('active',b.dataset.tab===tab);b.setAttribute('aria-current',b.dataset.tab===tab?'page':'false');});
  if(tab==='kasir') renderPOS();else if(tab==='pengeluaran') renderExpenses();else if(tab==='produk') renderProducts();else if(tab==='riwayat') renderHistory();else if(tab==='laporan') renderReport();else renderSettings();
}
const productEmoji=name=>{for(const [key,emoji] of Object.entries({Semangka:'🍉',Melon:'🍈',Tomat:'🍅',Wortel:'🥕',Nanas:'🍍',Apel:'🍎',Jambu:'🍐',Sirsak:'🥤',Strawberry:'🍓',Mangga:'🥭',Naga:'🐉',Markisa:'🟡',Sawo:'🥝',Alpukat:'🥑',Durian:'🟢'}))if(name.includes(key))return emoji;return '🧃';};
function cards(){
  const list=state.products.filter(p=>(!selectedCategory||p.category===selectedCategory)&&(p.name+' '+p.category).toLowerCase().includes(query.toLowerCase()));
  return list.map(p=>`<button class="product" data-action="add" data-id="${p.id}" ${p.stock<1?'disabled':''}><span class="product-top"><span class="product-emoji">${productEmoji(p.name)}</span><span class="stock-pill ${p.stock?'':'sold'}">${p.stock?'Stok '+p.stock:'Habis'}</span></span><span class="product-category">${esc(p.category||'Jus')}</span><strong>${esc(p.name)}</strong><span class="price">${money(p.price)}</span><span class="add-label">${p.stock?'+ Tambah':'Stok belum tersedia'}</span></button>`).join('')||'<p class="empty">Belum ada produk yang cocok.</p>';
}
function cartHTML(){
 return cart.map(l=>{const p=product(l.id);return `<div class="line"><div class="row"><div><strong>${esc(p?.name||'Produk tidak tersedia')}</strong><p class="muted">${money(p?.price||0)} / item</p></div><strong>${money((p?.price||0)*l.qty)}</strong></div><div class="stepper"><button class="secondary" aria-label="Kurangi jumlah" data-action="minus" data-id="${l.id}">−</button><span>${l.qty}</span><button class="secondary" aria-label="Tambah jumlah" data-action="add" data-id="${l.id}">+</button></div></div>`;}).join('')||'<p class="empty">Pilih produk untuk mulai menjual.</p>';
}
function renderPOS(){
 $('#content').innerHTML=`<div class="catalog-intro"><span class="eyebrow">JUS SEGAR, HARI CERAH</span><h2>Siap melayani hari ini?</h2><p>Pilih menu favorit pelanggan, lalu selesaikan pembayaran.</p></div><div class="pos-layout"><section><label id="search-label">Cari produk<input id="search" class="search" placeholder="Nama atau kategori" value="${esc(query)}" type="search"></label><div class="category-chips"><button class="${selectedCategory?'secondary':''}" data-category="">Semua menu</button>${[...new Set(state.products.map(p=>p.category))].map(c=>`<button class="${selectedCategory===c?'':'secondary'}" data-category="${esc(c)}">${esc(c||'Lainnya')}</button>`).join('')}</div><div class="grid" id="products">${cards()}</div>${state.products.length||!isAdmin()?'':'<button class="wide spaced" data-tab="produk">Tambah produk pertama</button>'}</section><section class="checkout card"><div class="row"><h2>Pesanan</h2><button class="secondary" data-action="clear">Kosongkan</button></div><div id="cart">${cartHTML()}</div><label>Diskon nominal (Rp)<input id="discount" inputmode="numeric" type="number" min="0" step="1" value="${discount}"></label><div class="row line"><span>Subtotal</span><strong id="subtotal">${money(subtotal())}</strong></div><div class="row line"><strong>Total bayar</strong><span class="amount" id="total">${money(total())}</span></div><div class="payment-heading"><span class="eyebrow">PEMBAYARAN</span><span>Pilih cara bayar</span></div><div class="payment-methods"><button class="${paymentMethod==='CASH'?'selected':'secondary'}" data-method="CASH" aria-pressed="${paymentMethod==='CASH'}"><span aria-hidden="true">💵</span> Tunai</button><button class="${paymentMethod==='QRIS'?'selected':'secondary'}" data-method="QRIS" aria-pressed="${paymentMethod==='QRIS'}"><span aria-hidden="true">▦</span> QRIS</button></div>${paymentMethod==='CASH'?`<label>Uang tunai diterima (Rp)<input id="paid" inputmode="numeric" type="number" min="0" step="1" placeholder="Contoh: 20000" value="${esc(paid)}"></label><div class="cash-presets">${[10000,20000,50000,100000].map(n=>`<button class="secondary" data-action="cashAmount" data-amount="${n}">${n/1000}k</button>`).join('')}</div><button class="text-button exact-money" data-action="exactAmount">Uang pas · ${money(total())}</button><div class="row change-row"><span>Kembalian</span><strong id="change">${money(Math.max(0,Number(paid)-total()))}</strong></div>`:`<div class="qris-panel"><div class="qris-symbol" aria-hidden="true">▦</div><strong>Scan QRIS toko</strong><p>Pastikan pembayaran <b id="qris-amount">${money(total())}</b> sudah diterima. Pencatatan dikonfirmasi manual.</p><label class="check-row"><input id="qris-confirmed" type="checkbox" ${qrisConfirmed?'checked':''}><span>Pembayaran QRIS sudah diterima</span></label></div>`}<div class="receipt-choice"><span>Nota pelanggan</span><div class="receipt-options"><button class="${printReceipt?'':'secondary'}" data-print-choice="yes" aria-pressed="${printReceipt}">Cetak nota</button><button class="${printReceipt?'secondary':''}" data-print-choice="no" aria-pressed="${!printReceipt}">Tanpa nota</button></div><small>Printer disiapkan oleh admin.</small></div><button class="wide spaced" data-action="pay" ${cart.length?'':'disabled'}>Bayar & simpan</button><p class="muted">Transaksi tetap tersimpan meskipun tanpa nota.</p></section></div>${cart.length?`<div class="quick-cart"><button class="wide" data-action="viewCart"><span>${cart.reduce((n,l)=>n+l.qty,0)} item · Lihat pesanan</span><strong>${money(total())}</strong></button></div>`:''}`;
}
function renderProducts(){
 $('#content').innerHTML=`<div class="row"><p class="muted">Harga dan stok tersimpan di HP ini.</p><button data-action="newProduct">+ Produk</button></div>${state.products.map(p=>`<section class="card row"><div><h3>${esc(p.name)}</h3><span class="muted">${esc(p.category)} · Stok ${p.stock}</span><p>${money(p.price)}</p></div><button class="secondary" data-action="editProduct" data-id="${p.id}">Ubah</button></section>`).join('')||'<div class="card empty">Tambahkan produk, harga, dan stok awal.</div>'}`;
}
function editProduct(id=0){
 const p=product(id)||{id:0,name:'',category:'Jus',price:'',cost:0,stock:0};
 modal(id?'Ubah produk & stok':'Tambah produk',`<form id="product-form"><input type="hidden" name="id" value="${p.id}"><label>Nama produk<input name="name" required maxlength="100" value="${esc(p.name)}"></label><label>Kategori<input name="category" maxlength="60" value="${esc(p.category)}"></label>${[['price','Harga jual (Rp)',p.price,1000000000],['stock','Stok tersedia',p.stock,1000000]].map(([n,l,v,max])=>`<label>${l}<input name="${n}" type="number" inputmode="numeric" min="0" max="${max}" step="1" required value="${v}"></label>`).join('')}<p class="muted">Stok diisi sesuai jumlah yang tersedia sekarang.</p><button type="submit" class="wide">Simpan produk</button></form>${id?`<button class="danger wide" data-action="archive" data-id="${id}">Arsipkan produk</button>`:''}`);
}
function renderHistory(){
 const rows=api('history');
 $('#content').innerHTML='<p class="muted">500 transaksi terakhir. Seluruh transaksi tetap masuk laporan dan backup.</p>'+rows.map(s=>`<button class="card wide row secondary" data-action="sale" data-id="${s.id}"><span>${esc(s.invoice)}<br><span class="muted">${esc(s.created_at.replace('T',' '))}${s.cancelled?' · DIBATALKAN':''}</span></span><strong>${money(s.total)}</strong></button>`).join('');
 if(!rows.length)$('#content').innerHTML+='<div class="card empty">Belum ada transaksi.</div>';
}
function printSale(id){try{api('print',{id});notify('Menghubungkan printer. Transaksi sudah tersimpan.');}catch(e){notify(e.message+' Transaksi tetap tersimpan.');}}
function showSale(id){
 const s=api('sale',{id}),wide=state.settings.paper==='48';
 const receipt=wide&&s.receipt_wide?s.receipt_wide:s.receipt;
 modal(s.cancelled?'Transaksi dibatalkan':'Transaksi tersimpan',`<div class="sale-success"><span aria-hidden="true">✓</span><strong>${money(s.total)}</strong><p>${esc(s.invoice)} · ${s.payment_method==='QRIS'?'QRIS':s.payment_method==='CREDIT'?'Catatan lama':'Tunai'}</p></div>${s.payment_method==='QRIS'?'<p class="hint">QRIS dikonfirmasi kasir. Tidak ada kembalian.</p>':`<p>Total ${money(s.total)} · Kembali ${money(Math.max(0,s.paid-s.total))}</p>`}${s.cancelled?'<p class="error">Stok transaksi ini sudah dikembalikan.</p>':''}<pre class="receipt-paper" data-paper="${wide?'48':'32'}">${esc(receipt)}</pre><div class="receipt-actions"><button class="wide" data-action="print" data-id="${s.id}">Cetak nota</button><button class="secondary wide" data-action="close">Selesai tanpa cetak</button></div>${isAdmin()?'<button class="text-button" data-action="printerSettings">Pengaturan printer & struk</button>':''}${s.cancelled||!isAdmin()?'':`<button class="danger wide" data-action="cancel" data-id="${s.id}">Batalkan transaksi & kembalikan stok</button>`}`);
}
const expenseCategories={BAHAN_BAKU:'Bahan baku',OPERASIONAL:'Operasional',TRANSPORTASI:'Transportasi',PERALATAN:'Peralatan',LAINNYA:'Lainnya'};
function renderExpenses(){
 const data=api('expenses',{date:expenseDate});
 $('#content').innerHTML=`<div class="section-intro"><div><span class="eyebrow">KAS KELUAR</span><h2>Catat kebutuhan toko</h2><p class="muted">Belanja bahan dan kebutuhan harian tersimpan offline.</p></div></div><section class="expense-overview"><div><span>Total pengeluaran</span><strong>${money(data.total)}</strong></div><span class="badge">${data.count} catatan aktif</span></section><label class="date-filter">Tanggal catatan<input id="expense-filter" type="date" value="${esc(expenseDate)}"></label><div class="expense-layout"><section class="card"><h3>Tambah pengeluaran</h3><form id="expense-form"><label>Tanggal<input name="expense_date" type="date" required value="${esc(expenseDate)}" max="${day()}"></label><label>Kategori<select name="category">${Object.entries(expenseCategories).map(([key,label])=>`<option value="${key}">${label}</option>`).join('')}</select></label><label>Nominal (Rp)<input name="amount" type="number" inputmode="numeric" min="1" max="2000000000" step="1" required placeholder="Contoh: 50000"></label><label>Keterangan<input name="description" minlength="3" maxlength="300" required placeholder="Contoh: Beli mangga 5 kg"></label><button class="wide" type="submit">Simpan pengeluaran</button></form></section><section class="expense-history"><h3>Riwayat pengeluaran</h3>${data.items.map(e=>`<article class="card expense-item"><div class="expense-icon">↗</div><div class="expense-info"><span class="eyebrow">${esc(expenseCategories[e.category]||e.category)}</span><h3>${esc(e.description)}</h3><p class="muted">${esc(e.expense_date)} · ${e.created_by==='ADMIN'?'Admin':'Kasir'}${e.cancelled?' · DIBATALKAN':''}</p></div><div class="expense-value"><strong>${money(e.amount)}</strong>${isAdmin()&&!e.cancelled?`<button class="text-button danger" data-action="cancelExpense" data-id="${e.id}">Batalkan</button>`:''}</div></article>`).join('')||'<div class="card empty"><span class="empty-icon">↗</span><h3>Belum ada pengeluaran</h3><p>Catatan pada tanggal ini akan tampil di sini.</p></div>'}</section></div>`;
}
function expenseRecovery(error='Ada pengeluaran yang belum selesai diperiksa.'){
 modal('Periksa pengeluaran',`<p class="hint">${esc(error)}</p><button class="wide" data-action="retryExpense">Cek ulang pengeluaran</button><button class="secondary wide" data-action="returnExpense">Kembali jika ditolak</button>`);
}
function completeExpense(payload,returnOnReject=false){
 if(paying||!payload)return;paying=true;modal('Menyimpan pengeluaran','<p>Tunggu sebentar…</p>');
 setTimeout(()=>{try{const e=api('addExpense',{expense:payload});localStorage.removeItem('jm-expense-pending');expenseDate=e.expense_date;tab='pengeluaran';$('#modal').hidden=true;render();notify('Pengeluaran tersimpan.');}
 catch(e){if(returnOnReject&&confirm('Pengeluaran ditolak: '+e.message+'\nKembali mengubah catatan?')){localStorage.removeItem('jm-expense-pending');$('#modal').hidden=true;tab='pengeluaran';render();}else expenseRecovery(e.message);}
 finally{paying=false;}},30);
}
function renderReport(){
 const report=api('reportRange',{mode:reportMode,anchor:reportAnchor}),s=report.summary,top=s.best,busy=s.busiest;
 $('#content').innerHTML=`<div class="section-intro"><span class="eyebrow">PENJUALAN TOKO</span><h2>Laporan & ekspor Excel</h2><p class="muted">Lihat performa toko dan simpan rincian penjualan.</p></div><section class="card report-controls"><label>Periode<select id="report-mode"><option value="DAY" ${reportMode==='DAY'?'selected':''}>1 hari</option><option value="WEEK" ${reportMode==='WEEK'?'selected':''}>7 hari</option><option value="MONTH" ${reportMode==='MONTH'?'selected':''}>1 bulan kalender</option></select></label><label>${reportMode==='MONTH'?'Bulan laporan':reportMode==='WEEK'?'Tanggal akhir':'Tanggal laporan'}<input id="report-anchor" type="${reportMode==='MONTH'?'month':'date'}" value="${esc(reportMode==='MONTH'?reportAnchor.slice(0,7):reportAnchor)}"></label><p class="muted">${esc(report.period.start)} sampai ${esc(report.period.end)} · WIB</p><button class="wide" data-action="exportReport">Ekspor laporan Excel (.xlsx)</button></section><div class="stats">${[['Omzet bersih',money(s.revenue)],['Transaksi sah',s.count],['Item terjual',s.qty],['Tunai',money(s.cash)],['QRIS',money(s.qris)],['Pengeluaran',money(s.expenses)]].map(([label,value])=>`<section class="card"><span class="muted">${label}</span><strong>${value}</strong></section>`).join('')}</div><section class="card spaced"><h3>Tanggal teramai</h3><p class="busiest-label">${busy?esc(busy.date):'Belum ada penjualan'}</p><p class="muted">${busy?busy.count+' transaksi · '+money(busy.revenue):'Tidak ada transaksi sah pada periode ini.'}</p></section><section class="card"><h3>Best seller</h3>${report.bestSellers.slice(0,10).map((p,i)=>`<div class="row line"><span>${i+1}. ${esc(p.name)}</span><strong>${p.qty} item</strong></div>`).join('')||'<p class="muted">Belum ada produk terjual.</p>'}</section><p class="muted">Transaksi batal dikeluarkan dari omzet dan best seller. Best seller berdasarkan jumlah item; tanggal teramai berdasarkan jumlah transaksi. Excel memisahkan Tunai dan QRIS serta menyertakan rincian harian dan pengeluaran. Ringkasan ini tidak menghitung laba bersih.</p>`;
}
function receiptDraft(){return Object.fromEntries(new FormData($('#settings-form')));}
function updateReceiptPreview(){
 try{const draft=receiptDraft();const preview=api('previewReceipt',{settings:draft,method:$('#preview-method').value});$('#receipt-preview').textContent=preview;$('#receipt-preview').dataset.paper=draft.paper;$('#preview-error').hidden=true;}
 catch(e){$('#preview-error').textContent=e.message;$('#preview-error').hidden=false;}
}
function renderSettings(){
 const s=state.settings;
 $('#content').innerHTML=`<div class="section-intro"><span class="eyebrow">IDENTITAS TOKO</span><h2>Buat nota khas tokomu</h2><p class="muted">Ubah isi struk, lihat preview, lalu simpan. Perubahan berlaku untuk transaksi baru.</p></div><div class="receipt-editor"><section class="card"><h3>Edit struk</h3><form id="settings-form">${[['name','Nama toko'],['address','Alamat'],['phone','Nomor pemesanan'],['header','Pesan atas struk'],['footer','Pesan bawah struk']].map(([n,l])=>`<label>${l}<input name="${n}" maxlength="160" ${n==='name'?'required':''} value="${esc(s[n]||'')}"></label>`).join('')}<label>Ukuran kertas<select name="paper"><option value="32" ${s.paper!=='48'?'selected':''}>58 mm · 32 karakter</option><option value="48" ${s.paper==='48'?'selected':''}>80 mm · 48 karakter</option></select></label><button type="submit" class="wide">Simpan pengaturan struk</button></form></section><section class="card preview-panel"><div class="row"><h3>Preview struk</h3><span class="badge">Contoh</span></div><label>Contoh pembayaran<select id="preview-method"><option value="CASH">Tunai</option><option value="QRIS">QRIS</option></select></label><div class="receipt-stage"><pre id="receipt-preview" class="receipt-paper"></pre></div><p id="preview-error" class="error" hidden></p><p class="muted">Preview memakai format cetak yang sama. Printer perlu diuji untuk memastikan lebar kertasnya.</p></section></div><section class="card"><div class="printer-heading"><span class="settings-icon" aria-hidden="true">▤</span><div><h2>Printer Bluetooth</h2><p id="printer-name">${esc(state.printerName)}</p></div></div><p class="muted">Hanya admin yang bisa memilih dan menguji printer. Kasir mencetak ke printer yang sudah dipilih.</p><div class="row wrap"><button data-action="choosePrinter">Pilih printer</button><button class="secondary" data-action="testPrint">Tes cetak</button></div><p class="muted">Pasangkan printer ESC/POS Bluetooth Classic/SPP lewat pengaturan HP.</p></section><section class="card sync-panel"><div class="printer-heading"><span class="settings-icon" aria-hidden="true">↗</span><div><h2>Pantau dari jauh</h2><p>Hubungkan HP kasir ke dashboard privat JusMerdeka.</p></div></div><p class="muted">Di dashboard, buka Hubungkan HP dan unduh file koneksi. Simpan file di HP ini, lalu pilih di bawah. File berlaku 24 jam untuk koneksi awal; hapus setelah terhubung.</p><div id="sync-status" role="status"></div><div class="row wrap"><button data-action="connectSync">Pilih file koneksi</button><button class="secondary" data-action="syncNow">Kirim sekarang</button><button class="secondary" data-action="syncRefresh">Perbarui status</button></div><button class="text-button danger" data-action="disconnectSync">Putuskan koneksi di HP ini</button><p class="muted">Data dikirim otomatis setiap sekitar 1 menit saat aplikasi terbuka dan internet tersedia. Saat aplikasi ditutup, pembaruan dilanjutkan ketika dibuka lagi. Kasir tetap berfungsi tanpa internet.</p></section><section class="card"><h2>Backup data</h2><p class="hint">Simpan backup rutin di luar HP kasir agar catatan tetap tersedia saat pindah perangkat.</p><button class="wide" data-action="backup">Simpan backup (.json)</button><button class="danger wide spaced" data-action="restore">Pulihkan dari backup</button></section><p class="muted">JusMerdeka 2.5 · Satu HP · Tunai & QRIS manual</p>`;
 updateReceiptPreview();updateSyncStatus();
}
function updateSyncStatus(){
 if(!isAdmin()||!$('#sync-status'))return;
 try{const s=api('syncStatus');$('#sync-status').innerHTML=`<p class="hint"><strong>${s.configured?'Terhubung':'Belum terhubung'}</strong><br>${esc(s.status)}${s.origin?'<br>'+esc(s.origin):''}<br>Terakhir berhasil: ${s.lastSuccess?esc(new Date(s.lastSuccess).toLocaleString('id-ID',{timeZone:'Asia/Jakarta'}))+' WIB':'Belum pernah'}</p>`;}catch(e){$('#sync-status').textContent=e.message;}
}
setInterval(()=>{if(tab==='pengaturan'&&isAdmin())updateSyncStatus();},5000);
function add(id){
 const p=product(id);if(!p)throw Error('Produk tidak tersedia.');
 const l=cart.find(x=>x.id===id);if((l?.qty||0)>=Math.min(999,p.stock))throw Error('Jumlah sudah mencapai stok yang tersedia.');
 if(l)l.qty++;else cart.push({id,qty:1});qrisConfirmed=false;confirmedTotal=null;persist();renderPOS();
}
function pay(){
 if(paying)return;
 if(!cart.length)throw Error('Keranjang kosong.');
 const amount=Number(paid);
 if(!Number.isSafeInteger(discount)||discount<0||discount>subtotal())throw Error('Diskon harus angka bulat dan tidak melebihi subtotal.');
 if(paymentMethod==='CASH'&&(paid===''||!Number.isSafeInteger(amount)||amount<total()||amount>100000000000000))throw Error('Masukkan uang tunai yang cukup dalam rupiah bulat.');
 if(paymentMethod==='QRIS'&&(!qrisConfirmed||confirmedTotal!==total()))throw Error('Pastikan pembayaran QRIS sudah diterima, lalu centang konfirmasi.');
 const requestId='sale-'+Date.now()+'-'+Array.from(crypto.getRandomValues(new Uint32Array(4)),n=>n.toString(16)).join('-');
 const payload={requestId,items:cart.map(x=>({...x})),discount,paid:paymentMethod==='QRIS'?total():amount,paymentMethod,qrisConfirmed,printReceipt};
 localStorage.setItem('jm-pending',JSON.stringify(payload)); // Durable before native checkout; retry uses the exact same request id.
 completePayment(payload,true);
}
function completePayment(payload,autoPrint=false){
 paying=true;modal('Menyimpan transaksi','<p>Tunggu sebentar…</p>');
 setTimeout(()=>{
   try{
     const sale=api('checkout',payload);
     // Save the cleared cart before dropping recovery information.
     cart=[];discount=0;paid='';paymentMethod='CASH';customer='';phone='';qrisConfirmed=false;printReceipt=false;persist();localStorage.removeItem('jm-pending');load();tab='kasir';render();showSale(sale.id);if(autoPrint&&payload.printReceipt)printSale(sale.id);
   }catch(e){
     // Keep the pending request after any uncertain failure; it can be retried safely.
     modal('Periksa pembayaran',`<p class="error">${esc(e.message)}</p><p>Gunakan Cek ulang untuk memastikan transaksi tersimpan. Jika ditolak karena stok atau uang kurang, kembali dan ubah pesanan.</p><button class="wide" data-action="retryPayment">Cek ulang pembayaran</button><button class="secondary wide" data-action="discardPending">Kembali ke pesanan</button>`);
   }finally{paying=false;}
 },30);
}
function pendingDialog(){if(localStorage.getItem('jm-expense-pending')){expenseRecovery();return;}modal('Pulihkan pembayaran', '<p>Ada pembayaran yang belum selesai diperiksa. Cek ulang sebelum membuat transaksi baru agar tidak tercatat dua kali.</p><button class="wide" data-action="retryPayment">Cek ulang pembayaran</button>');}
window.nativeResult=(text,reload)=>{notify(text);if(!api('session').role){role='';renderLogin();return;}if(reload){localStorage.removeItem('jm-cart');localStorage.removeItem('jm-pending');localStorage.removeItem('jm-expense-pending');cart=[];discount=0;paid='';paymentMethod='CASH';customer='';phone='';close();load();render();}else{load();if(tab==='pengaturan')renderSettings();}};
window.goHome=()=>{if(!role){loginRole='';renderLogin();return;}if(!$('#modal').hidden)close();else navigate('kasir');};
document.addEventListener('click',event=>{
 const b=event.target.closest('button');if(!b||b.disabled||paying)return;
 try{
  if(b.dataset.loginRole){loginRole=b.dataset.loginRole;renderLogin();return;}
  if(b.dataset.action==='logout'){api('logout');role='';loginRole='';state={products:[],settings:{}};cart=[];discount=0;paid='';tab='kasir';renderLogin();return;}
  if(!role)return;
  if(b.dataset.method){paymentMethod=b.dataset.method;qrisConfirmed=false;persist();renderPOS();return;}
  if(b.dataset.printChoice){printReceipt=b.dataset.printChoice==='yes';persist();renderPOS();return;}
  if(b.dataset.category!==undefined){selectedCategory=b.dataset.category;renderPOS();return;}
  if((localStorage.getItem('jm-pending')||localStorage.getItem('jm-expense-pending'))&&!['retryPayment','discardPending','retryExpense','returnExpense'].includes(b.dataset.action)){pendingDialog();return;}
  if(b.dataset.tab){navigate(b.dataset.tab);return;}
  const id=Number(b.dataset.id);
  switch(b.dataset.action){
   case 'close':close();break;
   case 'viewCart':$('.checkout').scrollIntoView({behavior:'smooth',block:'start'});break;
   case 'add':add(id);break;
   case 'minus':{qrisConfirmed=false;confirmedTotal=null;const l=cart.find(x=>x.id===id);if(l&&--l.qty===0)cart=cart.filter(x=>x.id!==id);persist();renderPOS();break;}
   case 'clear':if(cart.length&&confirm('Kosongkan keranjang?')){cart=[];discount=0;paid='';paymentMethod='CASH';customer='';phone='';persist();renderPOS();}break;
   case 'newProduct':editProduct();break;
   case 'editProduct':editProduct(id);break;
   case 'archive':if(confirm('Arsipkan produk? Riwayat penjualan tetap tersimpan.')){api('archiveProduct',{id});close();load();render();}break;
   case 'pay':pay();break;
   case 'sale':showSale(id);break;
   case 'retryExpense':completeExpense(JSON.parse(localStorage.getItem('jm-expense-pending')));break;
   case 'returnExpense':completeExpense(JSON.parse(localStorage.getItem('jm-expense-pending')),true);break;
   case 'cancelExpense':if(confirm('Batalkan catatan pengeluaran ini?')){api('cancelExpense',{id});renderExpenses();}break;
   case 'cancel':if(confirm('Batalkan transaksi ini dan kembalikan stok?')){api('cancel',{id});load();render();showSale(id);}break;
   case 'print':printSale(id);break;
   case 'printerSettings':close();navigate('pengaturan');break;
   case 'cashAmount':paid=String(b.dataset.amount);$('#paid').value=paid;$('#paid').dispatchEvent(new Event('input',{bubbles:true}));break;
   case 'exactAmount':paid=String(total());$('#paid').value=paid;$('#paid').dispatchEvent(new Event('input',{bubbles:true}));break;
   case 'retryPayment':{const p=localStorage.getItem('jm-pending');if(p)completePayment(JSON.parse(p));break;}
   case 'discardPending':{
     // Resolve the idempotency key before allowing a new payment. A saved transaction must be shown, never silently discarded.
     const p=JSON.parse(localStorage.getItem('jm-pending'));
     try{const saved=api('checkout',p);cart=[];discount=0;paid='';paymentMethod='CASH';customer='';phone='';qrisConfirmed=false;printReceipt=false;persist();localStorage.removeItem('jm-pending');load();render();showSale(saved.id);}
     catch(e){if(confirm('Pembayaran ditolak: '+e.message+'\nKembali untuk mengubah pesanan?')){localStorage.removeItem('jm-pending');close();load();render();}}break;
   }
   case 'syncRefresh':updateSyncStatus();break;
   case 'syncNow':api('syncNow');notify('Pembaruan dijadwalkan. Periksa status setelah beberapa saat.');updateSyncStatus();break;
   case 'disconnectSync':if(confirm('Putuskan koneksi di HP ini? Data kasir tetap tersimpan. Akses pengiriman juga bisa dicabut di dashboard.')){api('disconnectSync');updateSyncStatus();}break;
   case 'connectSync':api('connectSync');break;
   case 'exportReport':api('exportReport',{mode:reportMode,anchor:reportAnchor});break;
   case 'choosePrinter':case 'testPrint':case 'backup':case 'restore':api(b.dataset.action);break;
  }
 }catch(e){notify(e.message);}
});
document.addEventListener('input',event=>{
 const el=event.target;
 try{
  if(el.closest('#settings-form')){clearTimeout(previewTimer);previewTimer=setTimeout(updateReceiptPreview,150);}
  if(el.id==='login-pin')el.value=el.value.replace(/[^0-9]/g,'');
  if(el.id==='customer'){customer=el.value;persist();}
  if(el.id==='customer-phone'){phone=el.value;persist();}
  if(el.id==='search'){query=el.value;$('#products').innerHTML=cards();}
  if(el.id==='discount'||el.id==='paid'){
   if(el.id==='discount')discount=Number(el.value);else paid=el.value;
   persist();$('#total').textContent=money(total());if($('.quick-cart strong'))$('.quick-cart strong').textContent=money(total());if($('#change'))$('#change').textContent=money(Math.max(0,Number(paid)-total()));if(paymentMethod==='QRIS'){qrisConfirmed=false;if($('#qris-confirmed'))$('#qris-confirmed').checked=false;if($('#qris-amount'))$('#qris-amount').textContent=money(total());}
  }
 }catch(e){notify(e.message);}
});
document.addEventListener('change',event=>{if(event.target.id==='qris-confirmed'){qrisConfirmed=event.target.checked;confirmedTotal=qrisConfirmed?total():null;return;}if(event.target.id==='preview-method'||event.target.closest('#settings-form')){updateReceiptPreview();return;}if(event.target.id==='expense-filter'){expenseDate=event.target.value;renderExpenses();return;}if(event.target.id==='report-mode'){reportMode=event.target.value;renderReport();return;}if(event.target.id==='report-anchor'){reportAnchor=event.target.value+(reportMode==='MONTH'?'-01':'');try{renderReport();}catch(e){notify(e.message);}return;}});
document.addEventListener('submit',event=>{
 event.preventDefault();try{
  const f=event.target;const data=Object.fromEntries(new FormData(f));
  if(f.matches('#login-form')){
   try{const logged=api('login',{role:loginRole,pin:data.pin});role=logged.role;f.reset();load();restoreCart();tab='kasir';render();if(localStorage.getItem('jm-pending')||localStorage.getItem('jm-expense-pending'))pendingDialog();}
   catch(e){$('#login-error').textContent=e.message;$('#login-error').hidden=false;$('#login-pin').value='';$('#login-pin').focus();}
   return;
  }
  if(f.matches('#expense-form')){const amount=Number(data.amount);if(!Number.isSafeInteger(amount)||amount<1)throw Error('Isi nominal dalam rupiah bulat.');const payload={...data,amount,request_id:'expense-'+Date.now()+'-'+Array.from(crypto.getRandomValues(new Uint32Array(4)),n=>n.toString(16)).join('-')};localStorage.setItem('jm-expense-pending',JSON.stringify(payload));completeExpense(payload);return;}
  if(f.matches('#product-form')){for(const key of ['id','price','stock']){data[key]=Number(data[key]);if(!Number.isSafeInteger(data[key]))throw Error('Gunakan angka rupiah atau stok bulat.');}api('saveProduct',{product:data});close();load();render();notify('Produk disimpan.');}
  if(f.matches('#settings-form')){api('saveSettings',{settings:data});load();updateReceiptPreview();notify('Pengaturan struk disimpan.');}
 }catch(e){notify(e.message);}
});
try{role=api('session').role;if(role){load();restoreCart();}render();if(role&&(localStorage.getItem('jm-pending')||localStorage.getItem('jm-expense-pending')))pendingDialog();}
catch(e){$('#content').innerHTML=`<div class="card error">${esc(e.message)}</div>`;}
