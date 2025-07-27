
"use client";

import './globals.css';
import { AppLayout } from '@/components/common/app-layout';
import { Toaster } from "@/components/ui/toaster";
import { usePathname } from 'next/navigation';
import { Inter } from 'next/font/google';

const inter = Inter({ subsets: ['latin'], variable: '--font-inter' });

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = usePathname();
  const isLoginPage = pathname === '/';

  return (
    <html lang="en" className="dark">
       <head>
        <title>Namma Nagara</title>
        <meta name="description" content="Role-based, real-time agentic dashboards." />
      </head>
      <body className={`${inter.variable} font-sans antialiased`}>
        {isLoginPage ? (
          children
        ) : (
          <AppLayout>{children}</AppLayout>
        )}
        <Toaster />
      </body>
    </html>
  );
}
