import {cookies} from 'next/headers';
import {cookieName} from '../../../lib/auth';
import {sameOrigin,json,failure} from '../../../lib/security';
export async function POST(request:Request){try{sameOrigin(request);(await cookies()).delete(cookieName);return json({ok:true});}catch(e){return failure(e);}}
