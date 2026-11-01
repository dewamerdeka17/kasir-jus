import {requireOwner} from '../../../lib/auth';
import {snapshotFor} from '../../../lib/monitor';
import {report} from '../../../lib/report';
import {xlsx} from '../../../lib/xlsx';
import {json,failure} from '../../../lib/security';
export async function GET(request:Request){try{const u=await requireOwner(),q=new URL(request.url).searchParams,{snapshot}=await snapshotFor(u.id,q.get('device')||'');if(!snapshot)return json({error:'Belum ada data dari HP kasir.'},404);let r;try{r=report(snapshot,q.get('mode')||'DAY',q.get('anchor')||'');}catch{return json({error:'Periode tidak valid.'},400);}const bytes=xlsx(r);return new Response(bytes.buffer as ArrayBuffer,{headers:{'Content-Type':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','Content-Disposition':`attachment; filename="JusMerdeka-Laporan-${r.period.start}_${r.period.end}.xlsx"`,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});}catch(e){return failure(e);}}
