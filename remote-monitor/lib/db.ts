import {neon} from '@neondatabase/serverless';
export function database(){const url=process.env.DATABASE_URL;if(!url)throw Error('CONFIGURATION');return neon(url);}
export async function query<T=Record<string,unknown>>(sql:string,params:unknown[]=[]){return await database().query(sql,params) as T[];}
