import type {NextConfig} from 'next';
const config:NextConfig={turbopack:{root:process.cwd()},experimental:{optimizePackageImports:['lucide-react']}};
export default config;
