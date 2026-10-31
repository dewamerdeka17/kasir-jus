// Receives configuration through hidden stdin in automation, never command-line arguments.
import {neon} from '@neondatabase/serverless';
import fs from 'node:fs/promises';
let url=process.env.DATABASE_URL;if(!url){process.stdout.write('Ready for DATABASE_URL on hidden stdin.\n');if(process.stdin.isTTY){const {execFileSync}=await import('node:child_process');execFileSync('stty',['-echo'],{stdio:['inherit','inherit','inherit']});}let input='';for await(const piece of process.stdin){input+=piece;if(input.includes('\n'))break;}url=input.trim();}
if(!url)throw Error('DATABASE_URL required');const sql=neon(url);const statements=(await fs.readFile(new URL('../db/001_monitor.sql',import.meta.url),'utf8')).split('-- statement').map(s=>s.trim()).filter(Boolean);for(const statement of statements)await sql.query(statement);console.log('Applied additive monitor schema. Existing POS data was not modified.');
