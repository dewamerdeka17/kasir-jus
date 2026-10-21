"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { rupiah } from "@/lib/format";

export function SalesChart({ data }: { data: { label: string; total: number }[] }) {
  return <div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><AreaChart data={data} margin={{ top: 10, right: 5, left: -15, bottom: 0 }}><defs><linearGradient id="sales" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#2c8b5c" stopOpacity={.28}/><stop offset="95%" stopColor="#2c8b5c" stopOpacity={0}/></linearGradient></defs><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e7ece9"/><XAxis dataKey="label" axisLine={false} tickLine={false} fontSize={11}/><YAxis axisLine={false} tickLine={false} fontSize={10} tickFormatter={(v)=>`${v/1000}k`}/><Tooltip formatter={(value)=>rupiah(Number(value))} contentStyle={{borderRadius:12,border:"1px solid #e2e9e5"}}/><Area type="monotone" dataKey="total" stroke="#1b6b47" strokeWidth={3} fill="url(#sales)"/></AreaChart></ResponsiveContainer></div>;
}
