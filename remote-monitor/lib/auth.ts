import {SignJWT,jwtVerify} from 'jose';
import {cookies} from 'next/headers';
import {query} from './db';
export const cookieName='jm_monitor_session';
function secret(){const value=process.env.SESSION_SECRET;if(!value||value.length<32)throw Error('CONFIGURATION');return new TextEncoder().encode(value);}
export type Owner={id:number;name:string};
export async function getOwner():Promise<Owner|null>{const token=(await cookies()).get(cookieName)?.value;if(!token)return null;try{const {payload}=await jwtVerify(token,secret(),{algorithms:['HS256'],issuer:'jusmerdeka-monitor',audience:'owner'});const id=Number(payload.sub);if(!Number.isSafeInteger(id)||id<1)return null;const users=await query<Owner>('SELECT id,name FROM "User" WHERE id=$1 AND role=\'ADMIN\' AND status=\'ACTIVE\'',[id]);return users[0]||null;}catch(e){if(e instanceof Error&&e.message==='CONFIGURATION')throw e;return null;}}
export async function requireOwner(){const owner=await getOwner();if(!owner)throw Error('UNAUTHORIZED');return owner;}
export async function setSession(id:number){const token=await new SignJWT({}).setProtectedHeader({alg:'HS256'}).setIssuer('jusmerdeka-monitor').setAudience('owner').setSubject(String(id)).setIssuedAt().setExpirationTime('12h').sign(secret());(await cookies()).set(cookieName,token,{httpOnly:true,sameSite:'strict',secure:process.env.NODE_ENV==='production',path:'/',maxAge:43200});}
