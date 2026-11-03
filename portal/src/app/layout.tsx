import type { Metadata, Viewport } from "next";
import { SessionProvider } from "@/components/layout/SessionProvider";
import { Toaster } from "@/components/ui/Toaster";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Meu Dinheiro", template: "%s · Meu Dinheiro" },
  description: "Portal web de finanças pessoais Meu Dinheiro: contas, cartões, orçamentos, metas e investimentos.",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f3f7f7" },
    { media: "(prefers-color-scheme: dark)", color: "#0a1415" },
  ],
};

const SCRIPT_TEMA = `try{var t=localStorage.getItem('meudinheiro:tema');if(t==='light'||t==='dark'){document.documentElement.dataset.theme=t}}catch(e){}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: SCRIPT_TEMA }} />
      </head>
      <body className="min-h-dvh antialiased">
        <SessionProvider>{children}</SessionProvider>
        <Toaster />
      </body>
    </html>
  );
}
