
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { Flame, Siren } from "lucide-react";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function FireDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      activeCallouts: "0",
      highSeverity: "0",
      unitsAvailable: "8"
  });

  useEffect(() => {
    const fetchFireData = async () => {
      try {
        const fireAlerts = await getAlerts("Fire");
        setAlerts(fireAlerts);

        const activeCalloutsCount = fireAlerts.filter(a => a.status === 'Dispatched' || a.status === 'On-Scene').length;
        const highSeverityCount = fireAlerts.filter(a => a.severity === 'High' || a.severity === 'Critical').length;

        setStats(prevStats => ({
            ...prevStats,
            activeCallouts: activeCalloutsCount.toString(),
            highSeverity: highSeverityCount.toString(),
        }));
      } catch (error) {
        console.error("Failed to fetch fire data:", error);
      }
    };
    fetchFireData();
  }, []);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Fire Department Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Active Callouts" icon={Siren} value={stats.activeCallouts} />
        <AnalyticsCard title="High Severity Alerts" icon={Flame} value={stats.highSeverity} />
        <AnalyticsCard title="Units Available" icon={Siren} value={stats.unitsAvailable} />
      </div>
      <div className="grid grid-cols-1 gap-4">
          <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/fire/incidents" />
      </div>
    </div>
  );
}
