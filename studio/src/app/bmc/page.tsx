
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { Trash2, Wrench, Droplets } from "lucide-react";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function BmcDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
    garbageTickets: "0",
    potholeReports: "0",
    sewageIssues: "0",
  });

  useEffect(() => {
    const fetchBmcData = async () => {
      try {
        const bmcAlerts = await getAlerts("BMC");
        setAlerts(bmcAlerts);

        const garbageTicketsCount = bmcAlerts.filter(a => a.type === 'Garbage' && a.status === 'Open').length;
        const potholeReportsCount = bmcAlerts.filter(a => a.type === 'Pothole').length;
        const sewageIssuesCount = bmcAlerts.filter(a => a.type === 'Sewage' && a.status === 'Open').length;
        
        setStats({
            garbageTickets: garbageTicketsCount.toString(),
            potholeReports: potholeReportsCount.toString(),
            sewageIssues: sewageIssuesCount.toString(),
        });

      } catch (error) {
        console.error("Failed to fetch BMC data:", error);
      }
    };
    fetchBmcData();
  }, []);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">BMC (Civic Body) Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Open Garbage Tickets" icon={Trash2} value={stats.garbageTickets} />
        <AnalyticsCard title="Pothole Reports" icon={Wrench} value={stats.potholeReports} />
        <AnalyticsCard title="Sewage Issues" icon={Droplets} value={stats.sewageIssues} />
      </div>
      <div className="grid grid-cols-1 gap-4">
        <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/bmc/garbage" />
      </div>
    </div>
  );
}
