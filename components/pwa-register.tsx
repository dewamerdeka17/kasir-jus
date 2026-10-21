"use client";

import { useEffect, useState } from "react";
import { Download, X } from "lucide-react";

type InstallPromptEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: "accepted" | "dismissed" }> };

export function PwaRegister() {
  const [prompt, setPrompt] = useState<InstallPromptEvent | null>(null);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    const register = () => { if ("serviceWorker" in navigator) void navigator.serviceWorker.register("/sw.js"); };
    const registerTimer = window.setTimeout(register, 1200);
    const listener = (event: Event) => { event.preventDefault(); setPrompt(event as InstallPromptEvent); };
    window.addEventListener("beforeinstallprompt", listener);
    return () => {
      window.removeEventListener("beforeinstallprompt", listener);
      window.clearTimeout(registerTimer);
    };
  }, []);

  if (!prompt || hidden) return null;
  return <aside className="install-app"><div><Download size={19}/><span><strong>Pasang aplikasi</strong><small>Gunakan seperti aplikasi native</small></span></div><button className="button primary" onClick={async()=>{await prompt.prompt();const result=await prompt.userChoice;if(result.outcome==="accepted")setHidden(true)}}>Pasang</button><button className="icon-button" aria-label="Tutup" onClick={()=>setHidden(true)}><X size={17}/></button></aside>;
}
