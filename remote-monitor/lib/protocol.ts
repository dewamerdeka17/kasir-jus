import {z} from 'zod';
const int=z.number().int().min(0).max(100000000000000),id=z.number().int().positive().max(Number.MAX_SAFE_INTEGER),flag=z.union([z.literal(0),z.literal(1)]),text=(n:number)=>z.string().max(n);
const date=z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(s=>{const d=new Date(s+'T00:00:00Z');return Number.isFinite(+d)&&d.toISOString().slice(0,10)===s;});
const line=z.object({id,name:text(100),qty:int.min(1).max(999),price:int.max(1000000000)}).strict();
const sale=z.object({id,invoice:text(100),created_at:z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\+07:00)?$/),subtotal:int,discount:int,total:int,paid:int,cancelled:flag,payment_method:z.enum(['CASH','QRIS','CREDIT']),items:z.array(line).min(1).max(100)}).strict().superRefine((s,c)=>{if(s.items.reduce((v,l)=>v+l.qty*l.price,0)!==s.subtotal||s.subtotal-s.discount!==s.total||s.discount>s.subtotal)c.addIssue({code:'custom',message:'Total tidak cocok.'});if(s.payment_method==='CASH'&&s.paid<s.total||s.payment_method==='QRIS'&&s.paid!==s.total)c.addIssue({code:'custom',message:'Pembayaran tidak cocok.'});if(!date.safeParse(s.created_at.slice(0,10)).success)c.addIssue({code:'custom',message:'Tanggal salah.'});});
const expense=z.object({id,expense_date:date,category:z.enum(['BAHAN_BAKU','OPERASIONAL','TRANSPORTASI','PERALATAN','LAINNYA']),description:text(300),amount:int.min(1).max(2000000000),created_by:z.enum(['ADMIN','CASHIER']),cancelled:flag}).strict();
const product=z.object({id,name:text(100),category:text(60),price:int.max(1000000000),stock:int.max(1000000),active:flag}).strict();
const change=z.discriminatedUnion('op',[z.object({op:z.literal('remove'),kind:z.enum(['products','sales','expenses']),id}).strict(),z.object({op:z.literal('put'),kind:z.enum(['products','sales','expenses']),id,data:z.unknown()}).strict()]).superRefine((v,c)=>{if(v.op==='put'){const r=(v.kind==='products'?product:v.kind==='sales'?sale:expense).safeParse(v.data);if(!r.success||r.data.id!==v.id)c.addIssue({code:'custom',message:'Catatan tidak valid.'});}});
export const syncRequest=z.discriminatedUnion('operation',[
 z.object({operation:z.literal('status')}).strict(),
 z.object({operation:z.literal('start'),uploadId:z.string().uuid(),revision:id,baseRevision:int,reset:z.boolean(),expectedBatches:z.number().int().min(0).max(10000),shopName:text(160),capturedAt:z.string().datetime()}).strict(),
 z.object({operation:z.literal('batch'),uploadId:z.string().uuid(),index:z.number().int().min(0).max(9999),changes:z.array(change).min(1).max(100)}).strict(),
 z.object({operation:z.literal('commit'),uploadId:z.string().uuid()}).strict()
]);
export type SyncRequest=z.infer<typeof syncRequest>;
