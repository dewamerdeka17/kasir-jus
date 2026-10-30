export type Line={id:number;name:string;qty:number;price:number};
export type Sale={id:number;invoice:string;created_at:string;subtotal:number;discount:number;total:number;paid:number;cancelled:number;payment_method:'CASH'|'QRIS'|'CREDIT';items:Line[]};
export type Expense={id:number;expense_date:string;category:string;description:string;amount:number;created_by:string;cancelled:number};
export type Product={id:number;name:string;category:string;price:number;stock:number;active:number};
export type Snapshot={capturedAt:string;shop:{name:string};products:Product[];sales:Sale[];expenses:Expense[]};
export type Mode='DAY'|'WEEK'|'MONTH';
export const today=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Jakarta',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
export function period(mode:string,anchor:string){
 if(!/^\d{4}-\d{2}-\d{2}$/.test(anchor))throw Error('Tanggal tidak valid.');
 const d=new Date(anchor+'T00:00:00Z');if(!Number.isFinite(+d)||d.toISOString().slice(0,10)!==anchor)throw Error('Tanggal tidak valid.');
 let start=anchor,end=anchor;
 if(mode==='WEEK'){d.setUTCDate(d.getUTCDate()-6);start=d.toISOString().slice(0,10);}else if(mode==='MONTH'){start=anchor.slice(0,7)+'-01';d.setUTCMonth(d.getUTCMonth()+1,0);end=d.toISOString().slice(0,10);}else if(mode!=='DAY')throw Error('Periode tidak valid.');
 return {mode,start,end};
}
export const categoryNames:Record<string,string>={BAHAN_BAKU:'Bahan baku',OPERASIONAL:'Operasional',TRANSPORTASI:'Transportasi',PERALATAN:'Peralatan',LAINNYA:'Lainnya'};
export function report(snapshot:Snapshot,mode:string,anchor:string){
 const p=period(mode,anchor),daily=[];
 for(let d=new Date(p.start+'T00:00:00Z');d.toISOString().slice(0,10)<=p.end;d.setUTCDate(d.getUTCDate()+1))daily.push({date:d.toISOString().slice(0,10),count:0,qty:0,revenue:0,cash:0,qris:0,legacy:0,expenses:0});
 const days=new Map(daily.map(d=>[d.date,d])),best=new Map<number,{id:number;name:string;qty:number;gross:number;orders:number}>();
 const sales=snapshot.sales.filter(s=>days.has(s.created_at.slice(0,10))).map(s=>({...s,qty:s.items.reduce((v,l)=>v+l.qty,0),itemNames:s.items.map(l=>`${l.name} x${l.qty}`).join('; ')}));
 const expenses=snapshot.expenses.filter(e=>days.has(e.expense_date));
 let revenue=0,count=0,qty=0,cash=0,qris=0,legacy=0,cancelled=0,expenseTotal=0;
 for(const s of sales){if(s.cancelled){cancelled++;continue;}count++;revenue+=s.total;qty+=s.qty;const d=days.get(s.created_at.slice(0,10))!;d.count++;d.revenue+=s.total;d.qty+=s.qty;
 if(s.payment_method==='CASH'){d.cash+=s.total;cash+=s.total;}else if(s.payment_method==='QRIS'){d.qris+=s.total;qris+=s.total;}else{d.legacy+=s.total;legacy+=s.total;}
 const seen=new Set();for(const l of s.items){const b=best.get(l.id)||{id:l.id,name:l.name,qty:0,gross:0,orders:0};b.qty+=l.qty;b.gross+=l.qty*l.price;if(!seen.has(l.id)){b.orders++;seen.add(l.id);}best.set(l.id,b);}}
 for(const e of expenses)if(!e.cancelled){expenseTotal+=e.amount;days.get(e.expense_date)!.expenses+=e.amount;}
 const bestSellers=[...best.values()].sort((a,b)=>b.qty-a.qty||b.gross-a.gross||a.name.localeCompare(b.name));
 const busiest=[...daily].filter(d=>d.count>0).sort((a,b)=>b.count-a.count||b.revenue-a.revenue||a.date.localeCompare(b.date))[0]||null;
 return {period:p,shop:snapshot.shop,summary:{revenue,count,qty,cash,qris,legacy,expenses:expenseTotal,afterExpenses:revenue-expenseTotal,cancelled,best:bestSellers[0]||null,busiest},daily,bestSellers,sales,expenses,products:snapshot.products.filter(p=>p.active===1)};
}
export type Report=ReturnType<typeof report>;
