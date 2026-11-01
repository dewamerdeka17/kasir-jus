import {redirect} from 'next/navigation';
import {getOwner} from '../lib/auth';
import Dashboard from './dashboard';
export const dynamic='force-dynamic';
export default async function Home(){const user=await getOwner();if(!user)redirect('/login');return <Dashboard/>;}
