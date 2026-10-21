"use client";

import { useActionState, useState } from "react";
import { Eye, EyeOff, LockKeyhole, UserRound } from "lucide-react";
import { loginAction } from "@/app/actions/auth";

export function LoginForm() {
  const [state, action, pending] = useActionState(loginAction, {});
  const [show, setShow] = useState(false);
  return <form action={action} className="form-stack"><label>Username atau email<div className="input-icon"><UserRound size={18}/><input name="identity" placeholder="Masukkan username" autoComplete="username" required /></div></label><label>Password<div className="input-icon"><LockKeyhole size={18}/><input name="password" type={show ? "text" : "password"} placeholder="Masukkan password" autoComplete="current-password" required /><button type="button" className="icon-button" onClick={() => setShow(!show)} aria-label="Tampilkan password">{show ? <EyeOff size={18}/> : <Eye size={18}/>}</button></div></label><label className="remember"><input type="checkbox" name="remember"/> Ingat saya</label>{state.error && <p className="form-error">{state.error}</p>}<button className="button primary wide" disabled={pending}>{pending ? "Memeriksa..." : "Masuk ke Dashboard"}</button></form>;
}
