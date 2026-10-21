import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export async function GET(){await requireSession();return Response.json(await prisma.product.findMany({include:{category:true},orderBy:{name:"asc"}}))}
