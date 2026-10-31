import {query} from '../../../lib/db';
import {ingest} from '../../../lib/ingest';
export const runtime='nodejs';
export const maxDuration=60;
export async function POST(request:Request){return ingest(request,query);}
