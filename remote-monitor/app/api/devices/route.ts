import {requireOwner} from '../../../lib/auth';
import {query} from '../../../lib/db';
import {deviceList} from '../../../lib/monitor';
import {sameOrigin,json,failure,bodyText} from '../../../lib/security';
export async function GET(){try{const u=await requireOwner();return json({devices:await deviceList(u.id)});}catch(e){return failure(e);}}
export async function DELETE(request:Request){try{sameOrigin(request);const u=await requireOwner();const {id}=JSON.parse(await bodyText(request,1024));if(typeof id!=='string'||!/^[-a-f0-9]{36}$/.test(id))return json({error:'HP tidak valid.'},400);await query('UPDATE jm_monitor_devices SET revoked=true WHERE id=$1 AND owner_user_id=$2',[id,u.id]);return json({ok:true});}catch(e){return failure(e);}}
