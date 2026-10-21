import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export async function GET(){const session=await requireSession();return Response.json(await prisma.transaction.findMany({where:session.role==="CASHIER"?{userId:session.userId}:{},include:{items:true,user:{select:{id:true,name:true}}},orderBy:{createdAt:"desc"},take:100}))}
