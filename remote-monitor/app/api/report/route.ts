import {requireOwner} from '../../../lib/auth';
import {snapshotFor} from '../../../lib/monitor';
import {report} from '../../../lib/report';
import {json,failure} from '../../../lib/security';
export async function GET(request:Request){try{const u=await requireOwner(),q=new URL(request.url).searchParams,{device,snapshot}=await snapshotFor(u.id,q.get('device')||'');if(!snapshot)return json({device,report:null});let r;try{r=report(snapshot,q.get('mode')||'DAY',q.get('anchor')||'');}catch{return json({error:'Periode tidak valid.'},400);}return json({device,report:r});}catch(e){return failure(e);}}
